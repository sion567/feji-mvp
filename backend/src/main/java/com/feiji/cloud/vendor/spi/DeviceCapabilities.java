package com.feiji.cloud.vendor.spi;

/**
 * 厂商能力声明：missionFileFormat 如 WPML / MAVLINK_MISSION。
 */
public record DeviceCapabilities(
        boolean live,
        boolean mission,
        boolean drc,
        boolean dock,
        String missionFileFormat) {
}
