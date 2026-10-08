package com.feiji.cloud.vendor.dji;

import com.feiji.cloud.mqtt.MqttEnvelopes;
import com.feiji.cloud.mqtt.MqttPublisher;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 大疆上云API 指令下发收敛点（Phase 0 防腐层）。
 * 业务服务（直播/任务/DRC）只与本类交互，不直接拼 DJI 报文；
 * Phase 1 抽 VendorAdapter SPI 时，本类即 vendor-dji 适配器的指令发送核心。
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DjiCommandSender {

    private final MqttPublisher mqttPublisher;

    public DjiCommandSender(MqttPublisher mqttPublisher) {
        this.mqttPublisher = mqttPublisher;
    }

    /** 下发 services 指令（生成新 bid），返回 bid 供 services_reply 配对 */
    public String sendService(String sn, String method, Object data) {
        Map<String, Object> envelope = MqttEnvelopes.services(method, data);
        mqttPublisher.publish(MqttTopicEnum.servicesTopic(sn), envelope);
        return MqttEnvelopes.bid(envelope);
    }

    /** 以指定 bid 下发（会话级指令复用同一 bid，便于应答配对） */
    public String sendService(String sn, String bid, String method, Object data) {
        Map<String, Object> envelope = MqttEnvelopes.services(bid, method, data);
        mqttPublisher.publish(MqttTopicEnum.servicesTopic(sn), envelope);
        return MqttEnvelopes.bid(envelope);
    }

    /** DRC 下行通道指令（thing/product/{sn}/drc/down，fire-and-forget） */
    public void sendDrcDown(String sn, String method, Object data) {
        mqttPublisher.publish("thing/product/" + sn + "/drc/down", MqttEnvelopes.services(method, data));
    }
}
