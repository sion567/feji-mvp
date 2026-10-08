package com.feiji.cloud.vendor.dji;

import com.feiji.cloud.common.Vendors;
import com.feiji.cloud.vendor.spi.DeviceCapabilities;
import com.feiji.cloud.vendor.spi.VendorAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大疆上云API 适配器：把统一命令对象翻译为上云API 指令（live_start_push、flighttask 系列、drc 系列）。
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DjiVendorAdapter implements VendorAdapter {

    private final DjiCommandSender sender;

    public DjiVendorAdapter(DjiCommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String vendor() {
        return Vendors.DJI;
    }

    @Override
    public boolean supports(String vendor) {
        return Vendors.DJI.equals(vendor);
    }

    @Override
    public DeviceCapabilities capabilities() {
        return new DeviceCapabilities(true, true, true, true, "WPML");
    }

    @Override
    public String startLive(LiveStartCommand cmd) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url_type", 1);
        data.put("url", cmd.pushUrl());
        data.put("video_id", cmd.videoId());
        data.put("video_quality", cmd.quality() == null ? 0 : cmd.quality());
        return sender.sendService(cmd.gatewaySn(), "live_start_push", data);
    }

    @Override
    public String stopLive(LiveStopCommand cmd) {
        return sender.sendService(cmd.gatewaySn(), cmd.bid(), "live_stop_push", Map.of("video_id", cmd.videoId()));
    }

    @Override
    public String prepareMission(PrepareMissionCommand cmd) {
        // data 即 FlighttaskPrepareRequest 本体（对齐官方 SDK 字段平铺）
        Map<String, Object> flighttask = new LinkedHashMap<>();
        flighttask.put("flight_id", cmd.flightId());
        flighttask.put("execute_time", cmd.executeTime());
        flighttask.put("task_type", cmd.immediate() ? 0 : 1);
        flighttask.put("file", Map.of("url", cmd.fileUrl(), "fingerprint", cmd.fingerprint()));
        flighttask.put("rth_altitude", cmd.rthAltitude());
        flighttask.put("out_of_control_action", 0);
        return sender.sendService(cmd.sn(), "flighttask_prepare", flighttask);
    }

    @Override
    public String executeMission(String sn, String flightId) {
        return sender.sendService(sn, "flighttask_execute", Map.of("flight_id", flightId));
    }

    @Override
    public void cancelMission(String sn, String bid, String flightId) {
        sender.sendService(sn, bid, "flighttask_cancel", Map.of("flight_id", flightId));
    }

    @Override
    public void drcEnter(String sn) {
        sender.sendService(sn, "drc_enter", Map.of());
    }

    @Override
    public void drcExit(String sn) {
        sender.sendService(sn, "drc_exit", Map.of());
    }

    @Override
    public void flyTo(FlyToCommand cmd) {
        sender.sendService(cmd.sn(), "drc_flyto", Map.of(
                "lat", cmd.lat(), "lng", cmd.lng(),
                "height", cmd.height(), "safety_height", cmd.safetyHeight()));
    }

    @Override
    public void takeOff(TakeOffCommand cmd) {
        sender.sendService(cmd.sn(), "takeoff_to_point", Map.of(
                "lat", cmd.lat(), "lng", cmd.lng(),
                "height", cmd.height(), "safety_height", cmd.safetyHeight()));
    }

    @Override
    public void joystick(JoystickCommand cmd) {
        sender.sendDrcDown(cmd.sn(), "drone_control", Map.of("x", cmd.x(), "y", cmd.y(), "z", cmd.z()));
    }

    @Override
    public void heartbeat(String sn) {
        sender.sendDrcDown(sn, "heartbeat", Map.of());
    }
}
