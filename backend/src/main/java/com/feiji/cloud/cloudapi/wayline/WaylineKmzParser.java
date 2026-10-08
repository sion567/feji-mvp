package com.feiji.cloud.cloudapi.wayline;

import com.feiji.cloud.cloudapi.device.DeviceModelEnum;
import com.feiji.cloud.cloudapi.wayline.model.MissionConfig;
import com.feiji.cloud.cloudapi.wayline.model.Wayline;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.model.WaylineTemplate;
import com.feiji.cloud.cloudapi.wayline.model.Waypoint;
import com.feiji.cloud.cloudapi.wayline.model.WaypointAction;
import com.feiji.cloud.cloudapi.wayline.model.WaypointActionGroup;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DJI 航线 KMZ 解析器：解出 wpmz/template.kml（编辑态）与 wpmz/waylines.wpml（执行态），
 * 按 WPML 规范映射为内部统一模型 {@link WaylineFile}。
 * 解析按 localName 匹配，不受 wpml 命名空间版本（wpmz/1.0.x）与前缀写法影响。
 */
@Component
public class WaylineKmzParser {

    public WaylineFile parse(byte[] kmz, String name) {
        String templateXml = null;
        String waylinesXml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(kmz))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/').toLowerCase();
                if (entryName.endsWith("/template.kml") || entryName.equals("template.kml")) {
                    templateXml = readAll(zip);
                } else if (entryName.endsWith("/waylines.wpml") || entryName.equals("waylines.wpml")) {
                    waylinesXml = readAll(zip);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new WaylineParseException("KMZ 解压失败: " + e.getMessage(), e);
        }
        if (templateXml == null || waylinesXml == null) {
            throw new WaylineParseException("KMZ 中缺少 wpmz/template.kml 或 wpmz/waylines.wpml");
        }

        Document templateDoc = parseXml(templateXml);
        Document waylinesDoc = parseXml(waylinesXml);

        Element templateRoot = documentOf(templateDoc);
        Element waylinesRoot = documentOf(waylinesDoc);

        MissionConfig missionConfig = parseMissionConfig(templateRoot);
        String droneModelKey = droneModelKey(templateRoot, "droneInfo");
        String payloadModelKey = payloadModelKey(templateRoot, "payloadInfo");

        return new WaylineFile(
                name,
                text(templateRoot, "author"),
                tryLong(text(templateRoot, "createTime")),
                tryLong(text(templateRoot, "updateTime")),
                droneModelKey,
                DeviceModelEnum.labelOf(droneModelKey),
                payloadModelKey,
                DeviceModelEnum.labelOf(payloadModelKey),
                missionConfig,
                parseTemplates(templateRoot),
                parseWaylines(waylinesRoot));
    }

    // ---------------- template.kml ----------------

    private List<WaylineTemplate> parseTemplates(Element root) {
        List<WaylineTemplate> templates = new ArrayList<>();
        for (Element folder : children(root, "Folder")) {
            Element sysParam = child(folder, "waylineCoordinateSysParam");
            templates.add(new WaylineTemplate(
                    integer(folder, "templateId"),
                    text(folder, "templateType"),
                    dbl(folder, "autoFlightSpeed"),
                    sysParam == null ? null : text(sysParam, "coordinateMode"),
                    sysParam == null ? null : text(sysParam, "heightMode"),
                    dbl(folder, "globalHeight"),
                    dbl(folder, "globalEllipsoidHeight")));
        }
        return templates;
    }

    // ---------------- waylines.wpml ----------------

    private List<Wayline> parseWaylines(Element root) {
        List<Wayline> waylines = new ArrayList<>();
        for (Element folder : children(root, "Folder")) {
            List<Waypoint> waypoints = new ArrayList<>();
            for (Element placemark : children(folder, "Placemark")) {
                waypoints.add(parseWaypoint(placemark));
            }
            waylines.add(new Wayline(
                    integer(folder, "waylineId"),
                    integer(folder, "templateId"),
                    dbl(folder, "autoFlightSpeed"),
                    text(folder, "executeHeightMode"),
                    waypoints));
        }
        return waylines;
    }

    private Waypoint parseWaypoint(Element placemark) {
        Double longitude = null;
        Double latitude = null;
        Element point = child(placemark, "Point");
        if (point != null) {
            String coordinates = text(point, "coordinates");
            if (coordinates != null) {
                String[] parts = coordinates.split(",");
                if (parts.length >= 2) {
                    longitude = tryDouble(parts[0].trim());
                    latitude = tryDouble(parts[1].trim());
                }
            }
        }

        Element turnParam = child(placemark, "waypointTurnParam");
        Element headingParam = child(placemark, "waypointHeadingParam");

        List<WaypointActionGroup> actionGroups = new ArrayList<>();
        for (Element group : children(placemark, "ActionGroup")) {
            actionGroups.add(parseActionGroup(group));
        }

        return new Waypoint(
                integer(placemark, "index"),
                longitude,
                latitude,
                dbl(placemark, "height"),
                dbl(placemark, "ellipsoidHeight"),
                dbl(placemark, "executeHeight"),
                dbl(placemark, "waypointSpeed"),
                turnParam == null ? null : text(turnParam, "waypointTurnMode"),
                turnParam == null ? null : dbl(turnParam, "waypointTurnDampingDist"),
                headingParam == null ? null : text(headingParam, "waypointHeadingMode"),
                dbl(placemark, "gimbalPitchAngle"),
                actionGroups);
    }

    private WaypointActionGroup parseActionGroup(Element group) {
        Element trigger = child(group, "actionTrigger");
        String triggerType = trigger == null ? null : text(trigger, "actionTriggerType");
        String triggerParam = null;
        if (trigger != null) {
            String param = text(trigger, "actionTriggerParam");
            if (param == null) {
                // 兜底：取 actionTrigger 下除 type 外的第一个数值元素
                for (Element element : childElements(trigger)) {
                    if (!"actionTriggerType".equals(localName(element))) {
                        param = element.getTextContent().trim();
                        break;
                    }
                }
            }
            triggerParam = param;
        }

        List<WaypointAction> actions = new ArrayList<>();
        for (Element action : children(group, "action")) {
            actions.add(parseAction(action));
        }

        return new WaypointActionGroup(
                integer(group, "actionGroupId"),
                integer(group, "actionGroupStartIndex"),
                integer(group, "actionGroupEndIndex"),
                text(group, "actionGroupMode"),
                triggerType,
                triggerParam,
                actions);
    }

    private WaypointAction parseAction(Element action) {
        Map<String, String> params = new LinkedHashMap<>();
        Element funcParam = child(action, "actionActuatorFuncParam");
        if (funcParam != null) {
            for (Element element : childElements(funcParam)) {
                params.put(localName(element), element.getTextContent().trim());
            }
        }
        return new WaypointAction(
                integer(action, "actionId"),
                text(action, "actionActuatorFunc"),
                params);
    }

    // ---------------- 公共结构 ----------------

    private MissionConfig parseMissionConfig(Element root) {
        Element missionConfig = child(root, "missionConfig");
        if (missionConfig == null) {
            return null;
        }
        return new MissionConfig(
                text(missionConfig, "flyToWaylineMode"),
                text(missionConfig, "finishAction"),
                text(missionConfig, "exitOnRCLost"),
                text(missionConfig, "executeRCLostAction"),
                // 不同批次文件存在 takeOffSecurityHeight / takeoffSecurityHeight 两种写法
                dbl(missionConfig, "takeOffSecurityHeight") != null
                        ? dbl(missionConfig, "takeOffSecurityHeight")
                        : dbl(missionConfig, "takeoffSecurityHeight"),
                dbl(missionConfig, "globalTransitionalSpeed"));
    }

    private String droneModelKey(Element root, String infoElement) {
        Element missionConfig = child(root, "missionConfig");
        Element info = missionConfig == null ? null : child(missionConfig, infoElement);
        if (info == null) {
            return null;
        }
        String enumValue = text(info, "droneEnumValue");
        String subEnumValue = text(info, "droneSubEnumValue");
        return enumValue == null ? null : "0-" + enumValue + "-" + (subEnumValue == null ? "0" : subEnumValue);
    }

    private String payloadModelKey(Element root, String infoElement) {
        Element missionConfig = child(root, "missionConfig");
        Element info = missionConfig == null ? null : child(missionConfig, infoElement);
        if (info == null) {
            return null;
        }
        String enumValue = text(info, "payloadEnumValue");
        String subEnumValue = text(info, "payloadSubEnumValue");
        return enumValue == null ? null : "1-" + enumValue + "-" + (subEnumValue == null ? "0" : subEnumValue);
    }

    // ---------------- DOM 工具（按 localName 匹配，命名空间免疫） ----------------

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new WaylineParseException("WPML XML 解析失败: " + e.getMessage(), e);
        }
    }

    private Element documentOf(Document doc) {
        Element document = child(doc.getDocumentElement(), "Document");
        return document == null ? doc.getDocumentElement() : document;
    }

    private static Element child(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals(localName)) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals(localName)) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        return local != null ? local : element.getNodeName();
    }

    private static String text(Element parent, String localName) {
        Element element = child(parent, localName);
        if (element == null) {
            return null;
        }
        String content = element.getTextContent();
        return content == null || content.isBlank() ? null : content.trim();
    }

    private static Double dbl(Element parent, String localName) {
        String value = text(parent, localName);
        return value == null ? null : tryDouble(value);
    }

    private static Integer integer(Element parent, String localName) {
        String value = text(parent, localName);
        return value == null ? null : tryInt(value);
    }

    private static Double tryDouble(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer tryInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long tryLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
