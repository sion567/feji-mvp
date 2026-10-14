package com.feiji.cloud.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiji.cloud.cloudapi.device.DroneBattery;
import com.feiji.cloud.cloudapi.device.OsdDrone;
import com.feiji.cloud.cloudapi.device.PositionState;
import com.feiji.cloud.ws.WsPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证告警边沿触发（低电量阈值下穿一次、RTK 断连/恢复各一次）与 HMS 逐条解析、级别映射。
 */
class AlertServiceTest {

    private DeviceAlertMapper mapper;
    private WsPushService ws;
    private AlertService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(DeviceAlertMapper.class);
        ws = mock(WsPushService.class);
        service = new AlertService(mapper, ws, objectMapper);
    }

    private OsdDrone drone(Integer batteryPercent, PositionState positionState) {
        DroneBattery battery = batteryPercent == null ? null
                : new DroneBattery(batteryPercent, null, null, null);
        return new OsdDrone(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, battery, positionState);
    }

    @Test
    void lowBatteryFiresOncePerThresholdCrossing() {
        service.onDroneOsd("SN", drone(45, null));   // 高电量：不告警
        service.onDroneOsd("SN", drone(28, null));   // 下穿 30：WARN 一次
        service.onDroneOsd("SN", drone(25, null));   // 缓降但未穿阈值：不告警
        service.onDroneOsd("SN", drone(12, null));   // 下穿 15：CRITICAL 一次
        service.onDroneOsd("SN", drone(11, null));   // 继续下降：不告警
        service.onDroneOsd("SN", drone(60, null));   // 回充升高：不告警
        service.onDroneOsd("SN", drone(29, null));   // 再次下穿 30：WARN 一次

        ArgumentCaptor<DeviceAlertEntity> captor = ArgumentCaptor.forClass(DeviceAlertEntity.class);
        verify(mapper, times(3)).insert(captor.capture());
        List<DeviceAlertEntity> alerts = captor.getAllValues();
        assertEquals("WARN", alerts.get(0).getLevel());
        assertEquals("CRITICAL", alerts.get(1).getLevel());
        assertEquals("WARN", alerts.get(2).getLevel());
        verify(ws, times(3)).push(eq(AlertService.WS_TYPE), any());
    }

    @Test
    void rtkLossAndRecoveryFireOnceEach() {
        PositionState ok = new PositionState(18, true, 5, 18);
        PositionState lost = new PositionState(18, false, 3, 0);
        service.onDroneOsd("SN", drone(80, ok));     // 正常：不告警
        service.onDroneOsd("SN", drone(80, lost));   // 断连：WARN 一次
        service.onDroneOsd("SN", drone(80, lost));   // 持续断连：不告警
        service.onDroneOsd("SN", drone(80, ok));     // 恢复：NOTICE 一次

        ArgumentCaptor<DeviceAlertEntity> captor = ArgumentCaptor.forClass(DeviceAlertEntity.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals("rtk_disconnect", captor.getAllValues().get(0).getEventKey());
        assertEquals("WARN", captor.getAllValues().get(0).getLevel());
        assertEquals("rtk_recovered", captor.getAllValues().get(1).getEventKey());
        assertEquals("NOTICE", captor.getAllValues().get(1).getLevel());
    }

    @Test
    void hmsEventRecordsOneAlertPerEntryWithLevelMapping() throws Exception {
        String json = """
                {"hms":[
                  {"code":"0x16100086","level":2,"module":"battery","key":"hms_0x16100086_bat_overheat"},
                  {"code":"0x16220008","level":3,"module":"flight_control","key":"hms_0x16220008_esc_err"}
                ]}
                """;
        service.onHms("SN", objectMapper.readTree(json), false);
        service.onHms("SN", objectMapper.readTree(json), true);

        ArgumentCaptor<DeviceAlertEntity> captor = ArgumentCaptor.forClass(DeviceAlertEntity.class);
        verify(mapper, times(4)).insert(captor.capture());
        List<DeviceAlertEntity> alerts = captor.getAllValues();
        assertEquals("WARN", alerts.get(0).getLevel());
        assertEquals("HMS 告警: hms_0x16100086_bat_overheat", alerts.get(0).getMessage());
        assertEquals("CRITICAL", alerts.get(1).getLevel());
        assertEquals("NOTICE", alerts.get(2).getLevel());
        assertEquals("HMS 告警恢复: hms_0x16100086_bat_overheat", alerts.get(2).getMessage());
    }

    @Test
    void hmsExitWithoutEntriesRecordsRecoveryNotice() throws Exception {
        service.onHms("SN", objectMapper.readTree("{}"), true);
        ArgumentCaptor<DeviceAlertEntity> captor = ArgumentCaptor.forClass(DeviceAlertEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("NOTICE", captor.getValue().getLevel());
        assertEquals("HMS", captor.getValue().getCategory());
    }
}
