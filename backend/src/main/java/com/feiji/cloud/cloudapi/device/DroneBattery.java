package com.feiji.cloud.cloudapi.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 无人机电池 OSD（对齐官方 DroneBattery / 物模型 battery 结构）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneBattery(
        @JsonProperty("capacity_percent") Integer capacityPercent,
        @JsonProperty("remain_flight_time") Integer remainFlightTime,
        @JsonProperty("return_home_power") Integer returnHomePower,
        @JsonProperty("landing_power") Integer landingPower) {
}
