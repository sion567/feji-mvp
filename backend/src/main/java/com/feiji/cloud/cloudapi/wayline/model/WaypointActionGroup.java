package com.feiji.cloud.cloudapi.wayline.model;

import java.util.List;

/**
 * 动作组：作用于 [startIndex, endIndex] 区间的航点，触发器到达条件后按 mode 执行 actions。
 * triggerType: reachPoint / multipleTiming(秒) / multipleDistance(米)，triggerParam 为对应间隔值。
 */
public record WaypointActionGroup(
        Integer groupId,
        Integer startIndex,
        Integer endIndex,
        String mode,
        String triggerType,
        String triggerParam,
        List<WaypointAction> actions) {
}
