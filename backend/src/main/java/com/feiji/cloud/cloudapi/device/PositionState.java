package com.feiji.cloud.cloudapi.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 无人机定位状态 OSD（对齐官方 DronePositionState / 物模型 rtk_state）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionState(
        @JsonProperty("gps_number") Integer gpsNumber,
        @JsonProperty("is_fixed") Boolean isFixed,
        @JsonProperty("quality") Integer quality,
        @JsonProperty("rtk_number") Integer rtkNumber) {
}
