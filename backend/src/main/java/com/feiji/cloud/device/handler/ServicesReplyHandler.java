package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import com.feiji.cloud.mqtt.ServicesReplyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 指令执行结果（thing/product/{sn}/services_reply）：按 method 通知各业务监听器。
 */
@Component
public class ServicesReplyHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(ServicesReplyHandler.class);

    private final List<ServicesReplyListener> listeners;

    public ServicesReplyHandler(List<ServicesReplyListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.SERVICES_REPLY;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        String method = message.path("method").asText(null);
        String bid = message.path("bid").asText(null);
        JsonNode data = message.path("data");
        if (method == null) {
            return;
        }
        log.info("services_reply sn={} method={} data={}", sn, method, data);
        for (ServicesReplyListener listener : listeners) {
            try {
                listener.onServicesReply(sn, method, bid, data);
            } catch (Exception e) {
                log.error("services_reply 处理失败, method={}", method, e);
            }
        }
    }
}
