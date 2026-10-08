package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.cloudapi.device.DeviceModelEnum;
import com.feiji.cloud.common.Vendors;
import com.feiji.cloud.device.DeviceArchiveService;
import com.feiji.cloud.device.DeviceRegistryService;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import com.feiji.cloud.ws.WsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备上下线推送（sys/product/{sn}/status）。
 * 官方载荷：data.online / data.offline 对象包含 sn、model("domain-type-subType")、
 * version、connections、parent、child[]；另兼容旧版顶层 sn+online 格式。
 */
@Component
public class DeviceStatusHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusHandler.class);

    private final DeviceRegistryService registry;
    private final WsPushService wsPushService;
    private final DeviceArchiveService archive;

    public DeviceStatusHandler(DeviceRegistryService registry, WsPushService wsPushService,
                               DeviceArchiveService archive) {
        this.registry = registry;
        this.wsPushService = wsPushService;
        this.archive = archive;
    }

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.SYS_STATUS;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        JsonNode data = message.path("data");
        if (!data.isObject()) {
            return;
        }
        JsonNode online = data.path("online");
        JsonNode offline = data.path("offline");
        if (online.isObject()) {
            apply(online, true);
        }
        if (offline.isObject()) {
            apply(offline, false);
        }
        // 兼容旧版扁平格式：顶层 sn + online + parent_sn
        if (online.isMissingNode() && offline.isMissingNode() && data.hasNonNull("sn")) {
            apply(data, data.path("online").asBoolean(false));
        }
    }

    private void apply(JsonNode deviceNode, boolean online) {
        String deviceSn = deviceNode.path("sn").asText(null);
        if (deviceSn == null) {
            return;
        }
        String modelKey = deviceNode.path("model").asText(null);
        String modelName = DeviceModelEnum.labelOf(modelKey);
        String parentSn = deviceNode.path("parent").path("sn").asText(null);
        if (parentSn == null) {
            JsonNode legacyParent = deviceNode.path("parent_sn");
            if (!legacyParent.isMissingNode() && !legacyParent.isNull()) {
                parentSn = legacyParent.asText();
            }
        }
        registry.touch(deviceSn, new DeviceRegistryService.DeviceUpsert(
                modelKey, modelName, null, online, parentSn));
        archive.upsert(deviceSn, Vendors.DJI, modelKey, modelName, null, parentSn);

        // 子设备（机场↔无人机）随拓扑一并入册
        List<Map<String, Object>> childUpdates = new ArrayList<>();
        JsonNode children = deviceNode.path("child");
        if (children.isArray()) {
            for (JsonNode child : children) {
                String childSn = child.path("sn").asText(null);
                if (childSn == null) {
                    continue;
                }
                String childModel = child.path("model").asText(null);
                registry.touch(childSn, new DeviceRegistryService.DeviceUpsert(
                        childModel, DeviceModelEnum.labelOf(childModel), null, online, deviceSn));
                archive.upsert(childSn, Vendors.DJI, childModel, DeviceModelEnum.labelOf(childModel), null, deviceSn);
                childUpdates.add(Map.of("sn", childSn, "online", online));
            }
        }
        wsPushService.push("device_status_update", Map.of(
                "sn", deviceSn, "online", online, "children", childUpdates));
        log.info("设备上下线: sn={}, online={}, model={}", deviceSn, online, modelKey);
    }
}
