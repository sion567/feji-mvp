package com.feiji.cloud.cloudapi.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 机场 OSD（对齐官方 SDK OsdDock，MVP 保留大屏展示所需字段，未知字段忽略）。
 * 来源：thing/product/{dock_sn}/osd 的 data 为扁平结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsdDock(
        @JsonProperty("mode_code") Integer modeCode,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("height") Double height,
        @JsonProperty("cover_state") Integer coverState,
        @JsonProperty("putter_state") Integer putterState,
        @JsonProperty("drone_in_dock") Boolean droneInDock,
        @JsonProperty("drone_charge_state") Integer droneChargeState,
        @JsonProperty("humidity") Integer humidity,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("environment_temperature") Double environmentTemperature,
        @JsonProperty("rainfall") Integer rainfall,
        @JsonProperty("wind_speed") Double windSpeed,
        @JsonProperty("job_number") Integer jobNumber,
        @JsonProperty("alarm_state") Boolean alarmState,
        @JsonProperty("supplement_light_state") Boolean supplementLightState,
        @JsonProperty("emergency_stop_state") Boolean emergencyStopState,
        @JsonProperty("drc_state") Integer drcState) {
}
