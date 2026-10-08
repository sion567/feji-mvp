package com.feiji.cloud.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiji.cloud.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WsPushService {

    private static final Logger log = LoggerFactory.getLogger(WsPushService.class);

    private final WsSessionManager sessionManager;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    /** 每台设备最近一次向前端推送 OSD 的时间戳，用于节流 */
    private final Map<String, Long> lastOsdPushAt = new ConcurrentHashMap<>();

    public WsPushService(WsSessionManager sessionManager, AppProperties appProperties, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public void pushOsd(String sn, String role, Object data) {
        long now = System.currentTimeMillis();
        long interval = appProperties.osd().pushIntervalMs();
        Long last = lastOsdPushAt.get(sn);
        if (last != null && now - last < interval) {
            return;
        }
        lastOsdPushAt.put(sn, now);
        push("osd_update", Map.of("sn", sn, "role", role, "data", data));
    }

    public void push(String type, Object data) {
        try {
            String text = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "timestamp", System.currentTimeMillis(),
                    "data", data));
            sessionManager.broadcast(text);
        } catch (Exception e) {
            log.error("构造 WebSocket 推送消息失败, type={}", type, e);
        }
    }
}
