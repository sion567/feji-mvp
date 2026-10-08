package com.feiji.cloud.cloudapi.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 无人机 OSD（对齐官方 SDK OsdDockDrone / OsdRcDrone 的公共字段；
 * 来源：thing/product/{sn}/osd 的 data 为扁平结构，负载相机以 "type-subType-gimbalIndex"
 * 形式（如 43-0-0）平铺在 data 顶层，此处整体忽略）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsdDrone(
        @JsonProperty("mode_code") Integer modeCode,
        @JsonProperty("gear") Integer gear,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("height") Double height,
        @JsonProperty("elevation") Double elevation,
        @JsonProperty("horizontal_speed") Double horizontalSpeed,
        @JsonProperty("vertical_speed") Double verticalSpeed,
        @JsonProperty("attitude_head") Double attitudeHead,
        @JsonProperty("attitude_pitch") Double attitudePitch,
        @JsonProperty("attitude_roll") Double attitudeRoll,
        @JsonProperty("home_distance") Double homeDistance,
        @JsonProperty("wind_speed") Double windSpeed,
        @JsonProperty("wind_direction") Integer windDirection,
        @JsonProperty("total_flight_time") Double totalFlightTime,
        @JsonProperty("total_flight_distance") Double totalFlightDistance,
        @JsonProperty("firmware_version") String firmwareVersion,
        @JsonProperty("battery") DroneBattery battery,
        @JsonProperty("position_state") PositionState positionState) {

    public Integer batteryPercent() {
        return battery == null ? null : battery.capacityPercent();
    }
}
