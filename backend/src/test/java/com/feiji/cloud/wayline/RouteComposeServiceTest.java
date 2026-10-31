package com.feiji.cloud.wayline;

import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.cloudapi.device.DeviceModelEnum;
import com.feiji.cloud.cloudapi.wayline.WaylineKmzParser;
import com.feiji.cloud.cloudapi.wayline.WaylineKmzWriter;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.model.Waypoint;
import com.feiji.cloud.device.DeviceRegistryService;
import com.feiji.cloud.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 选点航线编排端到端（不含 DB/存储）：OSD 起点 → 点序优化 → 组装模型 → 生成 KMZ →
 * 用真实解析器读回验证排序与参数预设。排序正确性细节由 RouteOptimizerTest 覆盖。
 */
class RouteComposeServiceTest {

    private static final String SN = "1581F5FHD00A";

    private DeviceRegistryService registry;
    private WaylineLibraryService library;
    private RouteComposeService service;
    private final WaylineKmzParser parser = new WaylineKmzParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = mock(DeviceRegistryService.class);
        library = mock(WaylineLibraryService.class);
        service = new RouteComposeService(registry, new WaylineKmzWriter(), library, objectMapper);
    }

    private void givenDeviceAt(double lat, double lng) {
        when(registry.latestOsd(SN)).thenReturn(
                "{\"latitude\":" + lat + ",\"longitude\":" + lng + "}");
        when(registry.entry(SN)).thenReturn(new DeviceRegistryService.DeviceEntry(
                SN, null, "Matrice 3TD", "0-91-1", "drone", true, null, 0L));
        when(library.upload(any(), anyString(), any())).thenAnswer(invocation -> {
            WaylineFileEntity entity = new WaylineFileEntity();
            entity.setId(1L);
            entity.setName(invocation.getArgument(1));
            return entity;
        });
    }

    private static RouteComposeService.PointInput point(double lat, double lng) {
        return new RouteComposeService.PointInput(lat, lng);
    }

    private static LoginUser user() {
        return new LoginUser("op", 1L, "OPERATOR");
    }

    @Test
    void planShouldReturnOptimizedOrderWithSourceIndex() {
        givenDeviceAt(0, 0);
        // 距起点 1、3、2（度）的共线点，最优序为近→远
        var response = service.plan(new RouteComposeService.PlanRequest(SN, List.of(
                point(0, 3), point(0, 1), point(0, 2)), 80.0));

        assertEquals("BRUTE_FORCE", response.strategy());
        assertEquals(List.of(1, 2, 0), response.orderedPoints().stream()
                .map(RouteComposeService.PointView::sourceIndex).toList());
        assertTrue(response.totalDistanceMeters() > 0);
    }

    @Test
    void composeShouldProducePhotoWaylineInOptimizedOrder() {
        givenDeviceAt(0, 0);
        // KMZ 字节被 library.upload 接住，用真实解析器读回
        ArgumentCaptor<byte[]> kmzCaptor = ArgumentCaptor.forClass(byte[].class);
        when(library.upload(kmzCaptor.capture(), eq("巡检001"), any())).thenAnswer(invocation -> {
            WaylineFileEntity entity = new WaylineFileEntity();
            entity.setId(9L);
            entity.setName(invocation.getArgument(1));
            return entity;
        });

        service.compose(new RouteComposeService.ComposeRequest(SN, "巡检001", List.of(
                point(0, 3), point(0, 1), point(0, 2)), 80.0, null), user());

        WaylineFile parsed = parser.parse(kmzCaptor.getValue(), "巡检001");
        assertEquals("0-91-1", parsed.droneModelKey());
        assertEquals("Matrice 3TD", parsed.droneModelName());
        assertEquals(80.0, parsed.missionConfig().takeOffSecurityHeight());

        List<Waypoint> waypoints = parsed.waylines().get(0).waypoints();
        assertEquals(3, waypoints.size());
        // 最优序：近→远，与 plan 一致（输入 lng 为 3、1、2）
        assertEquals(List.of(1.0, 2.0, 3.0), waypoints.stream()
                .map(Waypoint::longitude).toList());
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            assertEquals(i, waypoint.index());
            assertEquals(80.0, waypoint.executeHeight());
            assertEquals("toPointAndStopWithDiscontinuityCurvature", waypoint.turnMode());
            assertEquals("followWayline", waypoint.headingMode());
            assertEquals(-90.0, waypoint.gimbalPitchAngle());
            var group = waypoint.actionGroups().get(0);
            assertEquals("reachPoint", group.triggerType());
            assertEquals("gimbalRotate", group.actions().get(0).func());
            assertEquals("-90", group.actions().get(0).params().get("gimbalPitchRotateAngle"));
            assertEquals("takePhoto", group.actions().get(1).func());
            assertEquals("point_" + i, group.actions().get(1).params().get("fileSuffix"));
        }
    }

    @Test
    void composeShouldRejectWhenDeviceHasNoPosition() {
        when(registry.latestOsd(SN)).thenReturn(null);
        var request = new RouteComposeService.ComposeRequest(SN, "无定位",
                List.of(point(31, 121)), 80.0, null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.compose(request, user()));
        assertTrue(e.getMessage().contains("定位"));
    }

    @Test
    void composeShouldRejectEmptyPointsAndBadHeight() {
        givenDeviceAt(31, 121);
        assertThrows(BusinessException.class, () -> service.plan(
                new RouteComposeService.PlanRequest(SN, List.of(), 80.0)));
        assertThrows(BusinessException.class, () -> service.plan(
                new RouteComposeService.PlanRequest(SN, List.of(point(31, 121)), 2.0)));
    }
}
