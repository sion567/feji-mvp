package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.alert.AlertService;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DRC 上行通道（thing/product/{sn}/drc/up）：指令 ack / 设备推送。
 * 处理飞行中的 HMS 推送（method=hms，与 events/device_hms 同构）；ack 等仅记录日志。
 */
@Component
public class DrcUpHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(DrcUpHandler.class);

    private final AlertService alertService;

    public DrcUpHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.DRC_UP;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        if ("hms".equals(message.path("method").asText(null))) {
            alertService.onHms(sn, message.path("data"), false);
            return;
        }
        log.debug("drc/up sn={} payload={}", sn, message);
    }
}
