package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiji.cloud.alert.AlertService;
import com.feiji.cloud.cloudapi.device.OsdDock;
import com.feiji.cloud.cloudapi.device.OsdDrone;
import com.feiji.cloud.common.Vendors;
import com.feiji.cloud.device.DeviceArchiveService;
import com.feiji.cloud.device.DeviceRegistryService;
import com.feiji.cloud.device.Telemetry;
import com.feiji.cloud.device.TrackService;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import com.feiji.cloud.vendor.dji.DjiModes;
import com.feiji.cloud.ws.WsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OSD 处理（thing/product/{sn}/osd）。
 * 官方载荷 data 为扁平结构：机场上报 OsdDock 字段集，无人机上报 OsdDrone 字段集
 * （负载相机以 "type-subType-gimbalIndex" 形式平铺，DTO 侧忽略）。
 * 通过特征字段识别设备角色后解析为 typed DTO，更新注册表并推送前端。
 */
@Component
public class OsdHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(OsdHandler.class);

    private final DeviceRegistryService registry;
    private final WsPushService wsPushService;
    private final ObjectMapper objectMapper;
    private final DeviceArchiveService archive;
    private final TrackService trackService;
    private final com.feiji.cloud.flight.FlightRecordService flightRecordService;
    private final AlertService alertService;

    public OsdHandler(DeviceRegistryService registry, WsPushService wsPushService, ObjectMapper objectMapper,
                      DeviceArchiveService archive, TrackService trackService,
                      com.feiji.cloud.flight.FlightRecordService flightRecordService,
                      AlertService alertService) {
        this.registry = registry;
        this.wsPushService = wsPushService;
        this.objectMapper = objectMapper;
        this.archive = archive;
        this.trackService = trackService;
        this.flightRecordService = flightRecordService;
        this.alertService = alertService;
    }

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.OSD;
    }

    @Override
    public void handle(String sn, JsonNode message) throws Exception {
        JsonNode data = message.path("data");
        if (!data.isObject()) {
            return;
        }
        registry.saveOsd(sn, data);
        long ts = message.path("timestamp").asLong(System.currentTimeMillis());

        if (isDockOsd(data)) {
            OsdDock dock = objectMapper.treeToValue(data, OsdDock.class);
            registry.touch(sn, new DeviceRegistryService.DeviceUpsert(null, null, "dock", true, null));
            archive.ensureDevice(sn, Vendors.DJI, null, null, "dock", null);
            Telemetry telemetry = new Telemetry(sn, ts, "dock",
                    dock == null ? null : dock.latitude(), dock == null ? null : dock.longitude(),
                    dock == null ? null : dock.height(), null, null, null, null,
                    DjiModes.dockMode(dock == null ? null : dock.modeCode()), null,
                    objectMapper.convertValue(data, Map.class));
            registry.saveTelemetry(sn, telemetry);
            trackService.append(sn, telemetry);
            wsPushService.pushOsd(sn, "dock", dock);
            return;
        }
        // 无人机与遥控器网关都按 OsdDrone 公共字段解析（遥控器仅 lat/lon/height/电量）
        OsdDrone drone = objectMapper.treeToValue(data, OsdDrone.class);
        String kind = data.hasNonNull("battery") ? "drone" : "rc";
        registry.touch(sn, new DeviceRegistryService.DeviceUpsert(null, null, kind, true, null));
        archive.ensureDevice(sn, Vendors.DJI, null, null, kind, null);
        Telemetry telemetry = new Telemetry(sn, ts, kind,
                drone == null ? null : drone.latitude(), drone == null ? null : drone.longitude(),
                drone == null ? null : drone.height(), drone == null ? null : drone.elevation(),
                drone == null ? null : drone.horizontalSpeed(), drone == null ? null : drone.verticalSpeed(),
                drone == null ? null : drone.attitudeHead(),
                DjiModes.droneMode(drone == null ? null : drone.modeCode()),
                drone == null ? null : drone.batteryPercent(),
                objectMapper.convertValue(data, Map.class));
        registry.saveTelemetry(sn, telemetry);
        trackService.append(sn, telemetry);
        flightRecordService.onTelemetry(telemetry);
        // 无人机才做低电量/RTK 断连派生告警（遥控器 OSD 字段不全）
        if ("drone".equals(kind)) {
            alertService.onDroneOsd(sn, drone);
        }
        wsPushService.pushOsd(sn, kind, drone);
    }

    private boolean isDockOsd(JsonNode data) {
        return data.hasNonNull("cover_state") || data.hasNonNull("putter_state")
                || data.hasNonNull("air_conditioner");
    }
}
