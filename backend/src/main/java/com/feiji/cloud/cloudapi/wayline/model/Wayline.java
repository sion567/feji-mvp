package com.feiji.cloud.cloudapi.wayline.model;

import java.util.List;

/**
 * 可执行航线（来自 waylines.wpml 的 Folder，一个 Folder 一条航线）。
 */
public record Wayline(
        Integer waylineId,
        Integer templateId,
        Double autoFlightSpeed,
        String executeHeightMode,
        List<Waypoint> waypoints) {
}
