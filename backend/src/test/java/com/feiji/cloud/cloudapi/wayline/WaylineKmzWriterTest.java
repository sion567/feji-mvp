package com.feiji.cloud.cloudapi.wayline;

import com.feiji.cloud.cloudapi.wayline.model.MissionConfig;
import com.feiji.cloud.cloudapi.wayline.model.Wayline;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.model.WaylineTemplate;
import com.feiji.cloud.cloudapi.wayline.model.Waypoint;
import com.feiji.cloud.cloudapi.wayline.model.WaypointAction;
import com.feiji.cloud.cloudapi.wayline.model.WaypointActionGroup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * round-trip：内部模型 → WaylineKmzWriter 生成 KMZ → WaylineKmzParser 解析 → 逐字段相等。
 * 与 {@link WaylineKmzParserTest} 对称（那边是「规范样例 → 解析」，这边是「模型 → 生成 → 解析」）。
 */
class WaylineKmzWriterTest {

    private final WaylineKmzWriter writer = new WaylineKmzWriter();
    private final WaylineKmzParser parser = new WaylineKmzParser();

    @Test
    void shouldRoundTripModelThroughKmz() {
        WaylineFile original = sampleModel();

        WaylineFile parsed = parser.parse(writer.write(original), "roundtrip.kmz");

        assertEquals("roundtrip.kmz", parsed.name());
        assertEquals(original.author(), parsed.author());
        assertEquals(original.createTime(), parsed.createTime());
        assertEquals(original.updateTime(), parsed.updateTime());
        assertEquals(original.droneModelKey(), parsed.droneModelKey());
        assertEquals(original.droneModelName(), parsed.droneModelName());
        assertEquals(original.payloadModelKey(), parsed.payloadModelKey());

        // missionConfig 全字段
        assertEquals(original.missionConfig(), parsed.missionConfig());

        // 模板全字段
        assertEquals(original.templates(), parsed.templates());

        // 航线与航点全字段
        assertEquals(original.waylines(), parsed.waylines());
    }

    /** 航点无逐点速度时回退全局速度，生成文件同样合法 */
    @Test
    void shouldFallbackToGlobalSpeedWhenWaypointSpeedMissing() {
        Waypoint noSpeed = new Waypoint(0, 121.4401, 31.0201, 80.0, null, 80.0,
                null, "toPointAndStopWithDiscontinuityCurvature", 0.0, "followWayline",
                -90.0, List.of());
        WaylineFile file = new WaylineFile("t.kmz", "feiji-platform", 1L, 1L,
                "0-91-1", "Matrice 3TD", "1-93-0", null,
                sampleMissionConfig(),
                List.of(new WaylineTemplate(0, "waypoint", 8.0, "WGS84",
                        "relativeToStartPoint", null, null)),
                List.of(new Wayline(0, 0, 8.0, "relativeToStartPoint", List.of(noSpeed))));

        WaylineFile parsed = parser.parse(writer.write(file), "t.kmz");
        assertEquals(8.0, parsed.waylines().get(0).waypoints().get(0).speed());
    }

    /** 定时触发（带 triggerParam）与多动作参数也应无损往返 */
    @Test
    void shouldRoundTripTriggerParamAndActionParams() {
        Map<String, String> hoverParams = new LinkedHashMap<>();
        hoverParams.put("hoverTime", "5");
        WaypointActionGroup timed = new WaypointActionGroup(0, 0, 0, "sequence",
                "multipleTiming", "10",
                List.of(new WaypointAction(0, "hover", hoverParams)));
        Waypoint waypoint = new Waypoint(0, 121.44, 31.02, 80.0, null, 80.0,
                8.0, "toPointAndStopWithDiscontinuityCurvature", 0.0, "followWayline",
                null, List.of(timed));
        WaylineFile file = new WaylineFile("t.kmz", "feiji-platform", 1L, 1L,
                "0-91-1", null, null, null, sampleMissionConfig(),
                List.of(new WaylineTemplate(0, "waypoint", 8.0, "WGS84",
                        "relativeToStartPoint", null, null)),
                List.of(new Wayline(0, 0, 8.0, "relativeToStartPoint", List.of(waypoint))));

        WaylineFile parsed = parser.parse(writer.write(file), "t.kmz");
        WaypointActionGroup parsedGroup = parsed.waylines().get(0).waypoints().get(0)
                .actionGroups().get(0);
        assertEquals("multipleTiming", parsedGroup.triggerType());
        assertEquals("10", parsedGroup.triggerParam());
        assertEquals("hover", parsedGroup.actions().get(0).func());
        assertEquals("5", parsedGroup.actions().get(0).params().get("hoverTime"));
    }

    /** DJI 兼容性骨架：命名空间声明、missionConfig、坐标「经度,纬度」顺序（解析器按 localName 匹配，测不出命名空间问题） */
    @Test
    void shouldEmitDjiCompatibleXmlSkeleton() throws IOException {
        byte[] kmz = writer.write(sampleModel());
        String templateXml = entryText(kmz, "wpmz/template.kml");
        String waylinesXml = entryText(kmz, "wpmz/waylines.wpml");

        assertTrue(templateXml.contains("xmlns=\"http://www.opengis.net/kml/2.2\""));
        assertTrue(templateXml.contains("xmlns:wpml=\"http://www.dji.com/wpmz/1.0.2\""));
        assertTrue(templateXml.contains("<wpml:missionConfig>"));
        assertTrue(templateXml.contains("<Placemark>"));
        assertTrue(waylinesXml.contains("<wpml:waylineId>0</wpml:waylineId>"));
        // KML 坐标固定「经度,纬度」
        assertTrue(waylinesXml.contains("<coordinates>121.4401,31.0201</coordinates>"));
    }

    private static String entryText(byte[] kmz, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(kmz))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                zip.closeEntry();
            }
        }
        throw new AssertionError("KMZ 中缺少条目: " + entryName);
    }

    // ---------------- 样例模型（与 RouteComposeService 生成的结构一致） ----------------

    private static MissionConfig sampleMissionConfig() {
        return new MissionConfig("safely", "goHome", "executeLostAction", "goBack", 80.0, 10.0);
    }

    private static WaylineFile sampleModel() {
        Map<String, String> gimbalParams = new LinkedHashMap<>();
        gimbalParams.put("gimbalRotateMode", "absoluteAnglePosition");
        gimbalParams.put("gimbalPitchRotateEnable", "1");
        gimbalParams.put("gimbalPitchRotateAngle", "-90");
        Map<String, String> photoParams = new LinkedHashMap<>();
        photoParams.put("payloadPositionIndex", "0");
        photoParams.put("fileSuffix", "point_0");

        WaypointActionGroup group = new WaypointActionGroup(0, 0, 0, "sequence",
                "reachPoint", null,
                List.of(new WaypointAction(0, "gimbalRotate", gimbalParams),
                        new WaypointAction(1, "takePhoto", photoParams)));
        Waypoint waypoint = new Waypoint(0, 121.4401, 31.0201, 80.0, 8.5, 80.0,
                8.0, "toPointAndStopWithDiscontinuityCurvature", 0.0, "followWayline",
                -90.0, List.of(group));

        return new WaylineFile("compose.kmz", "feiji-platform",
                1728259200000L, 1728345600000L,
                "0-91-1", "Matrice 3TD", "1-93-0", null,
                sampleMissionConfig(),
                List.of(new WaylineTemplate(0, "waypoint", 8.0, "WGS84",
                        "relativeToStartPoint", null, null)),
                List.of(new Wayline(0, 0, 8.0, "relativeToStartPoint", List.of(waypoint))));
    }
}
