package com.feiji.cloud.cloudapi.wayline.model;

import java.util.List;

/**
 * 航点（来自 Placemark(Point)）。height 与 ellipsoidHeight 是同一位置不同高程参考平面的表达，
 * executeHeight 为 waylines.wpml 中的实际执行高度。
 */
public record Waypoint(
        Integer index,
        Double longitude,
        Double latitude,
        Double height,
        Double ellipsoidHeight,
        Double executeHeight,
        Double speed,
        String turnMode,
        Double turnDampingDist,
        String headingMode,
        Double gimbalPitchAngle,
        List<WaypointActionGroup> actionGroups) {
}
