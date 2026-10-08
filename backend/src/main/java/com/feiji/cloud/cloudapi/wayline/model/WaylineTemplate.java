package com.feiji.cloud.cloudapi.wayline.model;

/**
 * 模板信息（来自 template.kml 的 Folder）。templateType: waypoint/mapping2d/mapping3d/mappingStrip。
 * 测绘类模板的特有字段（重叠率、航向等）暂不建模，后续按需扩展。
 */
public record WaylineTemplate(
        Integer templateId,
        String templateType,
        Double autoFlightSpeed,
        String coordinateMode,
        String heightMode,
        Double globalHeight,
        Double globalEllipsoidHeight) {
}
