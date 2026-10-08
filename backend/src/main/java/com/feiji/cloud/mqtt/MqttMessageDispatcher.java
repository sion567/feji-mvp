package com.feiji.cloud.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MqttMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageDispatcher.class);

    private final ObjectMapper objectMapper;
    private final Map<MqttTopicEnum, MqttTopicHandler> handlers = new EnumMap<>(MqttTopicEnum.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "mqtt-dispatcher");
        thread.setDaemon(true);
        return thread;
    });

    public MqttMessageDispatcher(ObjectMapper objectMapper, List<MqttTopicHandler> handlers) {
        this.objectMapper = objectMapper;
        handlers.forEach(handler -> this.handlers.put(handler.topic(), handler));
    }

    /** Paho 回调线程只做入队，避免业务处理阻塞 MQTT 收包 */
    public void dispatch(String topic, byte[] payload) {
        executor.execute(() -> doDispatch(topic, payload));
    }

    private void doDispatch(String topic, byte[] payload) {
        try {
            String sn = MqttTopicEnum.extractSn(topic);
            MqttTopicEnum topicEnum = MqttTopicEnum.of(MqttTopicEnum.extractTopicName(topic));
            if (sn == null || topicEnum == null) {
                log.warn("无法识别的 MQTT Topic: {}", topic);
                return;
            }
            MqttTopicHandler handler = handlers.get(topicEnum);
            if (handler == null) {
                log.debug("Topic {} 暂无处理器, 忽略", topic);
                return;
            }
            JsonNode envelope = objectMapper.readTree(payload);
            handler.handle(sn, envelope);
        } catch (Exception e) {
            log.error("处理 MQTT 消息失败, topic={}", topic, e);
        }
    }
}
