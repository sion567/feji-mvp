package com.feiji.cloud.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    public MqttPublisher(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    /** 向设备下发 services 指令 */
    public void publish(String topic, Object body) {
        if (!mqttClient.isConnected()) {
            log.warn("MQTT 未连接, 丢弃下发消息, topic={}", topic);
            return;
        }
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(body));
            message.setQos(1);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("MQTT 发布失败, topic={}", topic, e);
        }
    }
}
