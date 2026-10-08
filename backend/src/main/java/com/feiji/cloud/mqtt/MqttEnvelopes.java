package com.feiji.cloud.mqtt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 上云API MQTT 消息信封构建（{tid, bid, timestamp, method, data}）。
 */
public final class MqttEnvelopes {

    private MqttEnvelopes() {
    }

    /** 构建 services 下行指令信封；bid 由调用方取出用于配对 services_reply */
    public static Map<String, Object> services(String method, Object data) {
        return services(UUID.randomUUID().toString(), method, data);
    }

    /** 以指定 bid 构建（用于会话级指令复用同一 bid，便于应答配对） */
    public static Map<String, Object> services(String bid, String method, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tid", UUID.randomUUID().toString());
        envelope.put("bid", bid);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("method", method);
        envelope.put("data", data);
        return envelope;
    }

    public static String bid(Map<String, Object> envelope) {
        return String.valueOf(envelope.get("bid"));
    }
}
