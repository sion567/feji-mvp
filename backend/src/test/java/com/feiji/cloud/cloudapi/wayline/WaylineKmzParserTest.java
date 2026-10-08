package com.feiji.cloud.cloudapi.wayline;

import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.model.WaylineTemplate;
import com.feiji.cloud.cloudapi.wayline.model.Waypoint;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用按 WPML 规范构造的样例 KMZ 验证解析映射（M3TD + M3TD 相机 + 2 航点 + 拍照/云台动作）。
 */
class WaylineKmzParserTest {

    private static final String MISSION_CONFIG = """
                <wpml:missionConfig>
                  <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>
                  <wpml:finishAction>goHome</wpml:finishAction>
                  <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>
                  <wpml:executeRCLostAction>goBack</wpml:executeRCLostAction>
                  <wpml:takeOffSecurityHeight>30</wpml:takeOffSecurityHeight>
                  <wpml:globalTransitionalSpeed>10</wpml:globalTransitionalSpeed>
                  <wpml:droneInfo>
                    <wpml:droneEnumValue>91</wpml:droneEnumValue>
                    <wpml:droneSubEnumValue>1</wpml:droneSubEnumValue>
                  </wpml:droneInfo>
                  <wpml:payloadInfo>
                    <wpml:payloadEnumValue>93</wpml:payloadEnumValue>
                    <wpml:payloadSubEnumValue>0</wpml:payloadSubEnumValue>
                    <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
                  </wpml:payloadInfo>
                </wpml:missionConfig>
            """;

    private static final String TEMPLATE_KML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.2">
              <Document>
                <wpml:author>feiji-platform</wpml:author>
                <wpml:createTime>1728259200000</wpml:createTime>
                <wpml:updateTime>1728345600000</wpml:updateTime>
            """ + MISSION_CONFIG + """
                <Folder>
                  <wpml:templateType>waypoint</wpml:templateType>
                  <wpml:templateId>0</wpml:templateId>
                  <wpml:autoFlightSpeed>5</wpml:autoFlightSpeed>
                  <wpml:waylineCoordinateSysParam>
                    <wpml:coordinateMode>WGS84</wpml:coordinateMode>
                    <wpml:heightMode>relativeToStartPoint</wpml:heightMode>
                  </wpml:waylineCoordinateSysParam>
                  <Placemark>
                    <Point><coordinates>121.4401,31.0201</coordinates></Point>
                    <wpml:index>0</wpml:index>
                    <wpml:executeHeight>100</wpml:executeHeight>
                    <wpml:height>100</wpml:height>
                    <wpml:ellipsoidHeight>8.5</wpml:ellipsoidHeight>
                  </Placemark>
                  <Placemark>
                    <Point><coordinates>121.4411,31.0211</coordinates></Point>
                    <wpml:index>1</wpml:index>
                    <wpml:executeHeight>100</wpml:executeHeight>
                  </Placemark>
                </Folder>
              </Document>
            </kml>
            """;

    private static final String WAYLINES_WPML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.2">
              <Document>
                <wpml:author>feiji-platform</wpml:author>
                <wpml:createTime>1728259200000</wpml:createTime>
                <wpml:updateTime>1728345600000</wpml:updateTime>
            """ + MISSION_CONFIG + """
                <Folder>
                  <wpml:templateId>0</wpml:templateId>
                  <wpml:waylineId>0</wpml:waylineId>
                  <wpml:autoFlightSpeed>5</wpml:autoFlightSpeed>
                  <wpml:executeHeightMode>relativeToStartPoint</wpml:executeHeightMode>
                  <Placemark>
                    <Point><coordinates>121.4401,31.0201</coordinates></Point>
                    <wpml:index>0</wpml:index>
                    <wpml:executeHeight>100</wpml:executeHeight>
                    <wpml:height>100</wpml:height>
                    <wpml:ellipsoidHeight>8.5</wpml:ellipsoidHeight>
                    <wpml:useGlobalSpeed>1</wpml:useGlobalSpeed>
                    <wpml:waypointSpeed>5</wpml:waypointSpeed>
                    <wpml:useGlobalTurnParam>1</wpml:useGlobalTurnParam>
                    <wpml:waypointTurnParam>
                      <wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>
                      <wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>
                    </wpml:waypointTurnParam>
                    <wpml:useGlobalHeadingParam>1</wpml:useGlobalHeadingParam>
                    <wpml:waypointHeadingParam>
                      <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>
                    </wpml:waypointHeadingParam>
                    <wpml:gimbalPitchAngle>0</wpml:gimbalPitchAngle>
                    <wpml:ActionGroup>
                      <wpml:actionGroupId>0</wpml:actionGroupId>
                      <wpml:actionGroupStartIndex>0</wpml:actionGroupStartIndex>
                      <wpml:actionGroupEndIndex>0</wpml:actionGroupEndIndex>
                      <wpml:actionGroupMode>sequence</wpml:actionGroupMode>
                      <wpml:actionTrigger>
                        <wpml:actionTriggerType>reachPoint</wpml:actionTriggerType>
                      </wpml:actionTrigger>
                      <wpml:action>
                        <wpml:actionId>0</wpml:actionId>
                        <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>
                        <wpml:actionActuatorFuncParam>
                          <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
                          <wpml:fileSuffix>point_0</wpml:fileSuffix>
                        </wpml:actionActuatorFuncParam>
                      </wpml:action>
                      <wpml:action>
                        <wpml:actionId>1</wpml:actionId>
                        <wpml:actionActuatorFunc>gimbalRotate</wpml:actionActuatorFunc>
                        <wpml:actionActuatorFuncParam>
                          <wpml:gimbalRotateMode>absoluteAnglePosition</wpml:gimbalRotateMode>
                          <wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>
                          <wpml:gimbalPitchRotateAngle>-45</wpml:gimbalPitchRotateAngle>
                        </wpml:actionActuatorFuncParam>
                      </wpml:action>
                    </wpml:ActionGroup>
                  </Placemark>
                  <Placemark>
                    <Point><coordinates>121.4411,31.0211</coordinates></Point>
                    <wpml:index>1</wpml:index>
                    <wpml:executeHeight>100</wpml:executeHeight>
                  </Placemark>
                </Folder>
              </Document>
            </kml>
            """;

    private final WaylineKmzParser parser = new WaylineKmzParser();

    @Test
    void shouldParseKmzIntoUnifiedModel() throws IOException {
        byte[] kmz = zip(new LinkedHashMap<>(Map.of(
                "wpmz/template.kml", TEMPLATE_KML,
                "wpmz/waylines.wpml", WAYLINES_WPML)));

        WaylineFile file = parser.parse(kmz, "巡检样例.kmz");

        assertEquals("巡检样例.kmz", file.name());
        assertEquals("feiji-platform", file.author());
        assertEquals(1728259200000L, file.createTime());

        // 机型映射复用 DeviceModelEnum：0-91-1 → Matrice 3TD
        assertEquals("0-91-1", file.droneModelKey());
        assertEquals("Matrice 3TD", file.droneModelName());
        assertEquals("1-93-0", file.payloadModelKey());

        assertNotNull(file.missionConfig());
        assertEquals("safely", file.missionConfig().flyToWaylineMode());
        assertEquals("goHome", file.missionConfig().finishAction());
        assertEquals(30.0, file.missionConfig().takeOffSecurityHeight());

        assertEquals(1, file.templates().size());
        WaylineTemplate template = file.templates().get(0);
        assertEquals("waypoint", template.templateType());
        assertEquals("WGS84", template.coordinateMode());
        assertEquals(5.0, template.autoFlightSpeed());

        assertEquals(1, file.waylines().size());
        assertEquals(0, file.waylines().get(0).waylineId());
        assertEquals("relativeToStartPoint", file.waylines().get(0).executeHeightMode());
        assertEquals(2, file.waylines().get(0).waypoints().size());

        Waypoint waypoint0 = file.waylines().get(0).waypoints().get(0);
        assertEquals(121.4401, waypoint0.longitude());
        assertEquals(31.0201, waypoint0.latitude());
        assertEquals(100.0, waypoint0.executeHeight());
        assertEquals("toPointAndStopWithDiscontinuityCurvature", waypoint0.turnMode());
        assertEquals("followWayline", waypoint0.headingMode());
        assertEquals(1, waypoint0.actionGroups().size());

        var group = waypoint0.actionGroups().get(0);
        assertEquals("reachPoint", group.triggerType());
        assertEquals(2, group.actions().size());
        assertEquals("takePhoto", group.actions().get(0).func());
        assertEquals("point_0", group.actions().get(0).params().get("fileSuffix"));
        assertEquals("gimbalRotate", group.actions().get(1).func());
        assertEquals("-45", group.actions().get(1).params().get("gimbalPitchRotateAngle"));
    }

    @Test
    void shouldRejectKmzWithoutWpmlEntries() throws IOException {
        byte[] kmz = zip(new LinkedHashMap<>(Map.of("other.txt", "hello")));
        assertThrows(WaylineParseException.class, () -> parser.parse(kmz, "bad.kmz"));
    }

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
