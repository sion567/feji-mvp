package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DRC 上行通道（thing/product/{sn}/drc/up）：指令 ack / 设备推送。
 * MVP 仅记录日志；真机联调时在此解析 ack 与 HMS 推送。
 */
@Component
public class DrcUpHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(DrcUpHandler.class);

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.DRC_UP;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        log.debug("drc/up sn={} payload={}", sn, message);
    }
}
