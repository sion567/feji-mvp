package com.feiji.cloud.alert;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.feiji.cloud.cloudapi.device.OsdDrone;
import com.feiji.cloud.cloudapi.device.PositionState;
import com.feiji.cloud.ws.WsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备安全告警收敛点：HMS 事件（thing/product/{sn}/events 的 device_hms / device_exit_hms、
 * DRC 上行 drc/up 的 hms 推送）与 OSD 派生告警（低电量、RTK 断连，边沿触发去重）。
 * 每条告警落库（数据库不可用时静默降级）并经 WebSocket 推送前端（type=device_alert）。
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /** 低电量告警阈值（%）：≤30 警告、≤15 严重 */
    static final int BATTERY_WARN_PERCENT = 30;
    static final int BATTERY_CRITICAL_PERCENT = 15;

    public static final String WS_TYPE = "device_alert";

    private final DeviceAlertMapper alertMapper;
    private final WsPushService wsPushService;
    private final ObjectMapper objectMapper;

    /** 每台无人机上一次电量（%），用于低电量阈值下穿的边沿触发 */
    private final Map<String, Integer> lastBatteryPercent = new ConcurrentHashMap<>();

    private record RtkState(Boolean isFixed, Integer rtkNumber) {
    }

    /** 每台无人机上一次 RTK 状态，用于断连/恢复的边沿触发 */
    private final Map<String, RtkState> lastRtkState = new ConcurrentHashMap<>();

    public AlertService(DeviceAlertMapper alertMapper, WsPushService wsPushService, ObjectMapper objectMapper) {
        this.alertMapper = alertMapper;
        this.wsPushService = wsPushService;
        this.objectMapper = objectMapper;
    }

    /**
     * HMS 健康告警事件（data.hms 为数组，每项含 code/level/module/key）。
     * exit=true 为 device_exit_hms（告警恢复），否则为 device_hms（告警发生）。
     */
    public void onHms(String sn, JsonNode data, boolean exit) {
        long ts = System.currentTimeMillis();
        JsonNode entries = data == null ? null : data.path("hms");
        if (entries.isArray() && !entries.isEmpty()) {
            for (JsonNode entry : entries) {
                String key = textOrNull(entry, "key");
                if (key == null) {
                    key = textOrNull(entry, "code");
                }
                String message = (exit ? "HMS 告警恢复: " : "HMS 告警: ") + (key == null ? "未知" : key);
                record(sn, "HMS", exit ? "NOTICE" : hmsLevel(entry.path("level").asInt(0)),
                        key, message, entry, ts);
            }
            return;
        }
        if (exit) {
            // device_exit_hms 未携带明细时仍记一条恢复事件
            record(sn, "HMS", "NOTICE", null, "HMS 告警恢复", null, ts);
        }
    }

    /** OSD 派生安全告警：低电量（阈值下穿触发一次）与 RTK 断连/恢复（状态边沿触发） */
    public void onDroneOsd(String sn, OsdDrone drone) {
        if (drone == null) {
            return;
        }
        long ts = System.currentTimeMillis();
        checkBattery(sn, drone.batteryPercent(), ts);
        checkRtk(sn, drone.positionState(), ts);
    }

    private void checkBattery(String sn, Integer percent, long ts) {
        if (percent == null) {
            return;
        }
        Integer prev = lastBatteryPercent.put(sn, percent);
        if (percent > BATTERY_WARN_PERCENT || (prev != null && prev <= percent)) {
            return;
        }
        // 只在阈值下穿瞬间告警一次：直接击穿严重阈值记 CRITICAL，否则记 WARN
        if (percent <= BATTERY_CRITICAL_PERCENT && (prev == null || prev > BATTERY_CRITICAL_PERCENT)) {
            record(sn, "BATTERY", "CRITICAL", "low_battery_critical",
                    "电量严重不足：" + percent + "%（临近强制降落）", batteryPayload(percent, null, null), ts);
        } else if (prev == null || prev > BATTERY_WARN_PERCENT) {
            record(sn, "BATTERY", "WARN", "low_battery",
                    "低电量：" + percent + "%", batteryPayload(percent, null, null), ts);
        }
    }

    private void checkRtk(String sn, PositionState ps, long ts) {
        Boolean isFixed = ps == null ? null : ps.isFixed();
        Integer rtkNumber = ps == null ? null : ps.rtkNumber();
        if (isFixed == null && rtkNumber == null) {
            return;
        }
        RtkState prev = lastRtkState.put(sn, new RtkState(isFixed, rtkNumber));
        boolean nowOk = rtkNumber != null ? rtkNumber > 0 : Boolean.TRUE.equals(isFixed);
        if (prev == null || signalOk(prev) == nowOk) {
            return;
        }
        if (signalOk(prev) && !nowOk) {
            record(sn, "RTK", "WARN", "rtk_disconnect", "RTK 高精度定位信号断开",
                    batteryPayload(null, rtkNumber, isFixed), ts);
        } else {
            record(sn, "RTK", "NOTICE", "rtk_recovered", "RTK 定位恢复",
                    batteryPayload(null, rtkNumber, isFixed), ts);
        }
    }

    private boolean signalOk(RtkState state) {
        return state.rtkNumber() != null ? state.rtkNumber() > 0 : Boolean.TRUE.equals(state.isFixed());
    }

    /** HMS level 映射（官方 0 提示/1 注意/2 警告/3 严重）→ NOTICE/WARN/CRITICAL */
    private String hmsLevel(int level) {
        return switch (level) {
            case 3 -> "CRITICAL";
            case 2 -> "WARN";
            default -> "NOTICE";
        };
    }

    private JsonNode batteryPayload(Integer percent, Integer rtkNumber, Boolean isFixed) {
        ObjectNode node = objectMapper.createObjectNode();
        if (percent != null) {
            node.put("capacity_percent", percent);
        }
        if (rtkNumber != null) {
            node.put("rtk_number", rtkNumber);
        }
        if (isFixed != null) {
            node.put("is_fixed", isFixed);
        }
        return node;
    }

    private void record(String sn, String category, String level, String eventKey,
                        String message, JsonNode payload, long occurredAt) {
        log.warn("设备告警 sn={} category={} level={} key={} message={}", sn, category, level, eventKey, message);
        DeviceAlertEntity entity = new DeviceAlertEntity();
        entity.setSn(sn);
        entity.setCategory(category);
        entity.setLevel(level);
        entity.setEventKey(eventKey);
        entity.setMessage(message);
        entity.setPayload(payload == null ? null : payload.toString());
        entity.setOccurredAt(occurredAt);
        entity.setWorkspaceId(1L);
        try {
            alertMapper.insert(entity);
        } catch (Exception e) {
            log.warn("告警落库失败（仅推送前端）, sn={}: {}", sn, e.getMessage());
        }
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("sn", sn);
        push.put("category", category);
        push.put("level", level);
        push.put("eventKey", eventKey);
        push.put("message", message);
        push.put("occurredAt", occurredAt);
        wsPushService.push(WS_TYPE, push);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public List<DeviceAlertEntity> list(String sn, String level, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        QueryWrapper<DeviceAlertEntity> query = new QueryWrapper<DeviceAlertEntity>()
                .orderByDesc("id")
                .last("LIMIT " + safeLimit);
        if (sn != null && !sn.isBlank()) {
            query.eq("sn", sn);
        }
        if (level != null && !level.isBlank()) {
            query.eq("level", level);
        }
        return alertMapper.selectList(query);
    }
}
