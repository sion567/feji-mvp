package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 设备属性推送（thing/product/{sn}/state）。
 * TODO 二期: 机场远程配置（属性设置）的结果处理。
 */
@Component
public class StateHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(StateHandler.class);

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.STATE;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        log.debug("state sn={} payload={}", sn, message);
    }
}
