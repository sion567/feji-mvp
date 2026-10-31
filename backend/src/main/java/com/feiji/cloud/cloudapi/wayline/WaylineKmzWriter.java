package com.feiji.cloud.cloudapi.wayline;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * DJI 航线 KMZ 生成器：内部统一模型 {@link WaylineFile} → wpmz/template.kml + wpmz/waylines.wpml → KMZ，
 * 与 {@link WaylineKmzParser} 对称（round-trip：生成 → 解析 → 逐字段相等）。
 * 命名空间固定 kml 2.2 + wpmz/1.0.2，元素顺序对齐官方样例。
 */
@Component
public class WaylineKmzWriter {

    private static final String KML_NS = "http://www.opengis.net/kml/2.2";
    private static final String WPML_NS = "http://www.dji.com/wpmz/1.0.2";

    public byte[] write(WaylineFile file) {
        String templateXml = toXml(templateDocument(file));
        String waylinesXml = toXml(waylinesDocument(file));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("wpmz/template.kml"));
            zip.write(templateXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("wpmz/waylines.wpml"));
            zip.write(waylinesXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new WaylineParseException("KMZ 生成失败: " + e.getMessage(), e);
        }
    }

    // ---------------- template.kml ----------------

    private Document templateDocument(WaylineFile file) {
        Document doc = newDocument();
        Element document = documentElement(doc, file);

        appendMissionConfig(doc, document, file);

        List<Wayline> waylines = file.waylines() == null ? List.of() : file.waylines();
        for (WaylineTemplate template : file.templates()) {
            Element folder = doc.createElementNS(KML_NS, "Folder");
            appendText(doc, folder, "wpml:templateId", template.templateId());
            appendText(doc, folder, "wpml:templateType", template.templateType());
            appendText(doc, folder, "wpml:autoFlightSpeed", template.autoFlightSpeed());
            if (template.coordinateMode() != null || template.heightMode() != null) {
                Element sysParam = doc.createElementNS(WPML_NS, "wpml:waylineCoordinateSysParam");
                appendText(doc, sysParam, "wpml:coordinateMode", template.coordinateMode());
                appendText(doc, sysParam, "wpml:heightMode", template.heightMode());
                folder.appendChild(sysParam);
            }
            appendText(doc, folder, "wpml:globalHeight", template.globalHeight());
            appendText(doc, folder, "wpml:globalEllipsoidHeight", template.globalEllipsoidHeight());
            // 模板航点取同 templateId 的执行航线（官方 template.kml 的 Placemark 与执行态同构）
            waylines.stream()
                    .filter(wayline -> template.templateId() == null
                            || template.templateId().equals(wayline.templateId()))
                    .findFirst()
                    .ifPresent(wayline -> appendPlacemarks(doc, folder, wayline));
            document.appendChild(folder);
        }
        return doc;
    }

    // ---------------- waylines.wpml ----------------

    private Document waylinesDocument(WaylineFile file) {
        Document doc = newDocument();
        Element document = documentElement(doc, file);

        appendMissionConfig(doc, document, file);

        for (Wayline wayline : file.waylines()) {
            Element folder = doc.createElementNS(KML_NS, "Folder");
            appendText(doc, folder, "wpml:templateId", wayline.templateId());
            appendText(doc, folder, "wpml:waylineId", wayline.waylineId());
            appendText(doc, folder, "wpml:autoFlightSpeed", wayline.autoFlightSpeed());
            appendText(doc, folder, "wpml:executeHeightMode", wayline.executeHeightMode());
            appendPlacemarks(doc, folder, wayline);
            document.appendChild(folder);
        }
        return doc;
    }

    // ---------------- Placemark ----------------

    private void appendPlacemarks(Document doc, Element folder, Wayline wayline) {
        for (Waypoint waypoint : wayline.waypoints()) {
            Element placemark = doc.createElementNS(KML_NS, "Placemark");
            appendCoordinates(doc, placemark, waypoint);
            appendText(doc, placemark, "wpml:index", waypoint.index());
            appendText(doc, placemark, "wpml:executeHeight", waypoint.executeHeight());
            appendText(doc, placemark, "wpml:height", waypoint.height());
            appendText(doc, placemark, "wpml:ellipsoidHeight", waypoint.ellipsoidHeight());
            appendFlightParams(doc, placemark, waypoint, wayline.autoFlightSpeed());
            appendActionGroups(doc, placemark, waypoint.actionGroups());
            folder.appendChild(placemark);
        }
    }

    private void appendCoordinates(Document doc, Element placemark, Waypoint waypoint) {
        Element point = doc.createElementNS(KML_NS, "Point");
        // KML 坐标顺序固定「经度,纬度」
        appendText(doc, point, "coordinates",
                waypoint.longitude() + "," + waypoint.latitude());
        placemark.appendChild(point);
    }

    private void appendFlightParams(Document doc, Element placemark, Waypoint waypoint, Double globalSpeed) {
        Double speed = waypoint.speed();
        appendText(doc, placemark, "wpml:useGlobalSpeed", speed == null ? 1 : 0);
        appendText(doc, placemark, "wpml:waypointSpeed", speed != null ? speed : globalSpeed);

        if (waypoint.turnMode() != null) {
            appendText(doc, placemark, "wpml:useGlobalTurnParam", 1);
            Element turnParam = doc.createElementNS(WPML_NS, "wpml:waypointTurnParam");
            appendText(doc, turnParam, "wpml:waypointTurnMode", waypoint.turnMode());
            appendText(doc, turnParam, "wpml:waypointTurnDampingDist", waypoint.turnDampingDist());
            placemark.appendChild(turnParam);
        }
        if (waypoint.headingMode() != null) {
            appendText(doc, placemark, "wpml:useGlobalHeadingParam", 1);
            Element headingParam = doc.createElementNS(WPML_NS, "wpml:waypointHeadingParam");
            appendText(doc, headingParam, "wpml:waypointHeadingMode", waypoint.headingMode());
            placemark.appendChild(headingParam);
        }
        appendText(doc, placemark, "wpml:gimbalPitchAngle", waypoint.gimbalPitchAngle());
    }

    private void appendActionGroups(Document doc, Element placemark,
                                    List<WaypointActionGroup> actionGroups) {
        if (actionGroups == null) {
            return;
        }
        for (WaypointActionGroup group : actionGroups) {
            Element groupElement = doc.createElementNS(WPML_NS, "wpml:ActionGroup");
            appendText(doc, groupElement, "wpml:actionGroupId", group.groupId());
            appendText(doc, groupElement, "wpml:actionGroupStartIndex", group.startIndex());
            appendText(doc, groupElement, "wpml:actionGroupEndIndex", group.endIndex());
            appendText(doc, groupElement, "wpml:actionGroupMode", group.mode());

            Element trigger = doc.createElementNS(WPML_NS, "wpml:actionTrigger");
            appendText(doc, trigger, "wpml:actionTriggerType", group.triggerType());
            appendText(doc, trigger, "wpml:actionTriggerParam", group.triggerParam());
            groupElement.appendChild(trigger);

            for (WaypointAction action : group.actions()) {
                Element actionElement = doc.createElementNS(WPML_NS, "wpml:action");
                appendText(doc, actionElement, "wpml:actionId", action.actionId());
                appendText(doc, actionElement, "wpml:actionActuatorFunc", action.func());
                if (action.params() != null && !action.params().isEmpty()) {
                    Element funcParam = doc.createElementNS(WPML_NS, "wpml:actionActuatorFuncParam");
                    action.params().forEach((key, value) ->
                            appendText(doc, funcParam, "wpml:" + key, value));
                    actionElement.appendChild(funcParam);
                }
                groupElement.appendChild(actionElement);
            }
            placemark.appendChild(groupElement);
        }
    }

    // ---------------- 公共结构 ----------------

    private Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new WaylineParseException("KMZ 生成失败: " + e.getMessage(), e);
        }
    }

    /** Document 元素 + author/createTime/updateTime（template 与 waylines 共用） */
    private Element documentElement(Document doc, WaylineFile file) {
        Element kml = doc.createElementNS(KML_NS, "kml");
        // 根元素显式声明 wpml 前缀，否则序列化器会在每个 wpml 子元素上重复内联声明
        kml.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:wpml", WPML_NS);
        doc.appendChild(kml);
        Element document = doc.createElementNS(KML_NS, "Document");
        kml.appendChild(document);
        appendText(doc, document, "wpml:author", file.author());
        appendText(doc, document, "wpml:createTime", file.createTime());
        appendText(doc, document, "wpml:updateTime", file.updateTime());
        return document;
    }

    private void appendMissionConfig(Document doc, Element document, WaylineFile file) {
        MissionConfig config = file.missionConfig();
        if (config == null) {
            return;
        }
        Element missionConfig = doc.createElementNS(WPML_NS, "wpml:missionConfig");
        appendText(doc, missionConfig, "wpml:flyToWaylineMode", config.flyToWaylineMode());
        appendText(doc, missionConfig, "wpml:finishAction", config.finishAction());
        appendText(doc, missionConfig, "wpml:exitOnRCLost", config.exitOnRCLost());
        appendText(doc, missionConfig, "wpml:executeRCLostAction", config.executeRCLostAction());
        appendText(doc, missionConfig, "wpml:takeOffSecurityHeight", config.takeOffSecurityHeight());
        appendText(doc, missionConfig, "wpml:globalTransitionalSpeed", config.globalTransitionalSpeed());

        String droneEnum = enumValueOf(file.droneModelKey(), 1);
        String droneSub = enumValueOf(file.droneModelKey(), 2);
        if (droneEnum != null) {
            Element droneInfo = doc.createElementNS(WPML_NS, "wpml:droneInfo");
            appendText(doc, droneInfo, "wpml:droneEnumValue", droneEnum);
            appendText(doc, droneInfo, "wpml:droneSubEnumValue", droneSub);
            missionConfig.appendChild(droneInfo);
        }
        String payloadEnum = enumValueOf(file.payloadModelKey(), 1);
        String payloadSub = enumValueOf(file.payloadModelKey(), 2);
        if (payloadEnum != null) {
            Element payloadInfo = doc.createElementNS(WPML_NS, "wpml:payloadInfo");
            appendText(doc, payloadInfo, "wpml:payloadEnumValue", payloadEnum);
            appendText(doc, payloadInfo, "wpml:payloadSubEnumValue", payloadSub);
            missionConfig.appendChild(payloadInfo);
        }
        document.appendChild(missionConfig);
    }

    /** model key "0-91-1" / "1-93-0" 按段取值：segment 1=type，2=subType；解析失败返回 null */
    private String enumValueOf(String modelKey, int segment) {
        if (modelKey == null) {
            return null;
        }
        String[] parts = modelKey.split("-");
        return parts.length > segment ? parts[segment] : null;
    }

    // ---------------- 工具 ----------------

    private static void appendText(Document doc, Element parent, String qualifiedName, Object value) {
        if (value == null) {
            return;
        }
        // wpml: 前缀 → WPML 命名空间，其余（kml 结构元素）→ KML 命名空间
        Element element = qualifiedName.startsWith("wpml:")
                ? doc.createElementNS(WPML_NS, qualifiedName)
                : doc.createElementNS(KML_NS, qualifiedName);
        element.setTextContent(numberOrRaw(value));
        parent.appendChild(element);
    }

    /** 去掉整型浮点的 .0（80.0 → 80），其余原样 */
    private static String numberOrRaw(Object value) {
        if (value instanceof Double d && d == Math.floor(d) && !d.isInfinite()
                && Math.abs(d) < 1e15) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(value);
    }

    private static String toXml(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WaylineParseException("KMZ 生成失败: " + e.getMessage(), e);
        }
    }
}
