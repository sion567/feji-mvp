package com.feiji.cloud.mqtt;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大疆上云API 设备侧 Topic 的最后一段，完整 Topic 形如 thing/product/{sn}/osd 或 sys/product/{sn}/status。
 */
public enum MqttTopicEnum {

    OSD("osd"),
    STATE("state"),
    SERVICES("services"),
    SERVICES_REPLY("services_reply"),
    EVENTS("events"),
    SYS_STATUS("status"),
    DRC_UP("up");

    private final String topicName;

    private static final Map<String, MqttTopicEnum> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(MqttTopicEnum::getTopicName, Function.identity()));

    MqttTopicEnum(String topicName) {
        this.topicName = topicName;
    }

    public String getTopicName() {
        return topicName;
    }

    public static MqttTopicEnum of(String topicName) {
        return LOOKUP.get(topicName);
    }

    /** 云端向设备下发 services 指令的 Topic */
    public static String servicesTopic(String sn) {
        return "thing/product/" + sn + "/services";
    }

    public static String extractSn(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 4 ? parts[2] : null;
    }

    public static String extractTopicName(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }
}
