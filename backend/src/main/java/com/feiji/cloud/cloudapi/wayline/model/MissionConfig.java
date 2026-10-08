package com.feiji.cloud.cloudapi.wayline.model;

/**
 * 任务全局配置（template.kml 与 waylines.wpml 共用的 wpml:missionConfig）。
 */
public record MissionConfig(
        String flyToWaylineMode,
        String finishAction,
        String exitOnRCLost,
        String executeRCLostAction,
        Double takeOffSecurityHeight,
        Double globalTransitionalSpeed) {
}
