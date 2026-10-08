package com.feiji.cloud.device;

import java.util.Map;

/**
 * 统一遥测模型（厂商无关）。各厂商适配器负责把自有协议翻译成本模型，
 * 轨迹存储/未来消费者只依赖此结构；raw 保留原始字段供扩展展示。
 */
public record Telemetry(
        String sn,
        long timestamp,
        String kind,
        Double latitude,
        Double longitude,
        Double altitude,
        Double elevation,
        Double horizontalSpeed,
        Double verticalSpeed,
        Double heading,
        String mode,
        Integer batteryPercent,
        Map<String, Object> raw) {
}
