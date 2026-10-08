package com.feiji.cloud.cloudapi.wayline.model;

import java.util.Map;

/**
 * 单个动作。func: takePhoto / startRecord / stopRecord / focus / zoom / customDirName /
 * gimbalRotate / rotateYaw / hover；params 以字符串 Map 全量保真（避免为每个动作建类）。
 */
public record WaypointAction(Integer actionId, String func, Map<String, String> params) {
}
