package com.feiji.cloud.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.feiji.cloud.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MVP 骨架期的轻量设备注册表：设备首次上报消息即入册。
 * model/modelName 来自上下线推送(device-model key 与显示名)，kind 来自 OSD 识别(dock/drone/rc)。
 * 正式的设备档案/拓扑表随设备管理模块落库。
 */
@Service
public class DeviceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistryService.class);

    private static final String REGISTRY_KEY = "feiji:device:registry";
    private static final String OSD_KEY_PREFIX = "feiji:device:osd:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public DeviceRegistryService(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties appProperties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    /** 设备档案增量更新，DeviceUpsert 中为 null 的字段保留旧值 */
    public void touch(String sn, DeviceUpsert upsert) {
        try {
            Map<Object, Object> all = redis.opsForHash().entries(REGISTRY_KEY);
            ObjectNode node = readEntry(all.get(sn));
            if (upsert.modelKey() != null) {
                node.put("model", upsert.modelKey());
            }
            if (upsert.modelName() != null) {
                node.put("modelName", upsert.modelName());
            }
            if (upsert.kind() != null) {
                node.put("kind", upsert.kind());
            }
            if (upsert.online() != null) {
                node.put("online", upsert.online());
            }
            if (upsert.parentSn() != null) {
                node.put("parentSn", upsert.parentSn());
            }
            node.put("lastSeen", System.currentTimeMillis());
            redis.opsForHash().put(REGISTRY_KEY, sn, node.toString());
        } catch (Exception e) {
            log.warn("更新设备注册表失败, sn={}: {}", sn, e.getMessage());
        }
    }

    public record DeviceUpsert(String modelKey, String modelName, String kind, Boolean online, String parentSn) {
    }

    public void saveOsd(String sn, JsonNode data) {
        try {
            redis.opsForValue().set(OSD_KEY_PREFIX + sn, data.toString(),
                    Duration.ofHours(appProperties.osd().osdTtlHours()));
        } catch (Exception e) {
            log.warn("保存 OSD 快照失败, sn={}: {}", sn, e.getMessage());
        }
    }

    private static final String TELEMETRY_KEY_PREFIX = "feiji:device:telemetry:";

    /** 厂商无关的遥测快照（统一模型），供未来中立消费者使用 */
    public void saveTelemetry(String sn, Telemetry telemetry) {
        try {
            redis.opsForValue().set(TELEMETRY_KEY_PREFIX + sn, objectMapper.writeValueAsString(telemetry),
                    Duration.ofHours(appProperties.osd().osdTtlHours()));
        } catch (Exception e) {
            log.debug("保存遥测快照失败, sn={}: {}", sn, e.getMessage());
        }
    }

    public String latestOsd(String sn) {
        return redis.opsForValue().get(OSD_KEY_PREFIX + sn);
    }

    /** 解绑/移除设备：清除注册表、OSD 快照、遥测快照、轨迹 */
    public void remove(String sn) {
        try {
            redis.opsForHash().delete(REGISTRY_KEY, sn);
            redis.delete(OSD_KEY_PREFIX + sn);
            redis.delete(TELEMETRY_KEY_PREFIX + sn);
            redis.delete("feiji:track:" + sn);
        } catch (Exception e) {
            log.warn("移除设备注册信息失败, sn={}: {}", sn, e.getMessage());
        }
    }

    public DeviceEntry entry(String sn) {
        try {
            Object value = redis.opsForHash().get(REGISTRY_KEY, sn);
            if (value == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(value.toString());
            return new DeviceEntry(sn,
                    textOrNull(node, "deviceName"),
                    textOrNull(node, "modelName"),
                    textOrNull(node, "model"),
                    textOrNull(node, "kind"),
                    node.path("online").asBoolean(false),
                    textOrNull(node, "parentSn"),
                    node.path("lastSeen").asLong(0L));
        } catch (Exception e) {
            return null;
        }
    }

    public List<DeviceEntry> listDevices() {
        Map<Object, Object> all = redis.opsForHash().entries(REGISTRY_KEY);
        List<DeviceEntry> list = new ArrayList<>();
        all.forEach((sn, value) -> {
            try {
                JsonNode node = objectMapper.readTree(value.toString());
                list.add(new DeviceEntry(
                        sn.toString(),
                        textOrNull(node, "deviceName"),
                        textOrNull(node, "modelName"),
                        textOrNull(node, "model"),
                        textOrNull(node, "kind"),
                        node.path("online").asBoolean(false),
                        textOrNull(node, "parentSn"),
                        node.path("lastSeen").asLong(0L)));
            } catch (Exception ignored) {
            }
        });
        list.sort((a, b) -> Long.compare(b.lastSeen(), a.lastSeen()));
        return list;
    }

    private ObjectNode readEntry(Object existing) {
        if (existing instanceof String text) {
            try {
                return (ObjectNode) objectMapper.readTree(text);
            } catch (Exception ignored) {
            }
        }
        return objectMapper.createObjectNode();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record DeviceEntry(String sn, String deviceName, String modelName, String model, String kind,
                              boolean online, String parentSn, long lastSeen) {
    }
}
