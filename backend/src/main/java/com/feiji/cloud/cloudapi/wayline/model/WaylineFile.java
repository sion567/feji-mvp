package com.feiji.cloud.cloudapi.wayline.model;

import java.util.List;

/**
 * 内部统一航线模型（从 KMZ/WPML 解析而来，与 WPML 解耦，前端/任务下发/编辑器均复用此模型）。
 */
public record WaylineFile(
        String name,
        String author,
        Long createTime,
        Long updateTime,
        String droneModelKey,
        String droneModelName,
        String payloadModelKey,
        String payloadModelName,
        MissionConfig missionConfig,
        List<WaylineTemplate> templates,
        List<Wayline> waylines) {
}
