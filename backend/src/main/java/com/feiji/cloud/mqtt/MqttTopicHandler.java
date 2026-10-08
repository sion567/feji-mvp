package com.feiji.cloud.mqtt;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 按设备 Topic 分发的消息处理器；message 为上云API 的完整信封 {tid, bid, timestamp, data}。
 */
public interface MqttTopicHandler {

    MqttTopicEnum topic();

    void handle(String sn, JsonNode message) throws Exception;
}
