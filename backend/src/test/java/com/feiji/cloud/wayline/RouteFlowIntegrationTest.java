package com.feiji.cloud.wayline;

import com.feiji.cloud.device.DeviceRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 选点航线全链路集成（H2 内存库 + mock 设备注册表，无需 MySQL/Redis/EMQX）：
 * 登录 → plan 排序预览 → compose 生成 KMZ 入库 → 航线详情读回 → 任务创建（TIMED 延后派发）。
 * 走真实 HTTP 层（鉴权、审计、参数校验），数据库与 KMZ 存储均为真实组件。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:routetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // 无 EMQX：整体关闭 MQTT，任务用 TIMED 延后派发避免触达厂商适配层
        "app.mqtt.enabled=false",
        // KMZ 落盘到构建目录，不污染 ./data
        "app.wayline.storage-dir=target/test-waylines"
})
@AutoConfigureMockMvc
class RouteFlowIntegrationTest {

    private static final String SN = "1581F5FHD00A";
    /** 起点 (121.44) 与三个选点（121.46 / 121.442 / 121.45），最优序为近→远：下标 1,2,0 */
    private static final String POINTS = """
            [{"lat":31.02,"lng":121.46},{"lat":31.02,"lng":121.442},{"lat":31.02,"lng":121.45}]""";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceRegistryService registry;

    @BeforeEach
    void setUp() {
        when(registry.latestOsd(SN)).thenReturn("{\"latitude\":31.02,\"longitude\":121.44}");
        when(registry.entry(SN)).thenReturn(new DeviceRegistryService.DeviceEntry(
                SN, null, "Matrice 3TD", "0-91-1", "drone", true, null, 0L));
    }

    private String token() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
    }

    @Test
    void fullFlowFromPlanToJobCreation() throws Exception {
        String auth = "Bearer " + token();

        // 1. 排序预览：起点为 OSD 定位，最优序近→远
        mockMvc.perform(post("/api/v1/route/plan")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"sn\":\"" + SN + "\",\"points\":" + POINTS + ",\"height\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.strategy").value("BRUTE_FORCE"))
                .andExpect(jsonPath("$.data.orderedPoints[0].sourceIndex").value(1))
                .andExpect(jsonPath("$.data.orderedPoints.length()").value(3));

        // 2. 生成 KMZ 入库（真实落盘 + 解析回填元数据）
        String composeBody = mockMvc.perform(post("/api/v1/route/compose")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"sn\":\"" + SN + "\",\"name\":\"联调航线001\",\"points\":" + POINTS
                                + ",\"height\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waypointCount").value(3))
                .andExpect(jsonPath("$.data.templateType").value("waypoint"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int fileId = ((Number) com.jayway.jsonpath.JsonPath.read(composeBody, "$.data.id")).intValue();

        // 3. 航线详情从库读回：KMZ 解析出的航点为优化后顺序
        mockMvc.perform(get("/api/v1/waylines/" + fileId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waylines[0].waypoints.length()").value(3))
                .andExpect(jsonPath("$.data.waylines[0].waypoints[0].longitude").value(121.442))
                .andExpect(jsonPath("$.data.waylines[0].waypoints[0].executeHeight").value(80))
                .andExpect(jsonPath("$.data.waylines[0].waypoints[0].actionGroups[0].triggerType")
                        .value("reachPoint"));

        // 4. 任务创建（TIMED 一小时后执行，不触发派发）
        mockMvc.perform(post("/api/v1/wayline-jobs")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"name\":\"联调任务001\",\"waylineFileId\":" + fileId
                                + ",\"droneSn\":\"" + SN + "\",\"taskType\":\"TIMED\",\"executeTime\":"
                                + (System.currentTimeMillis() + 3_600_000) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.waylineFileId").value(fileId));
    }

    @Test
    void planRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/route/plan")
                        .contentType("application/json")
                        .content("{\"sn\":\"" + SN + "\",\"points\":" + POINTS + ",\"height\":80}"))
                .andExpect(status().isUnauthorized());
    }
}
