package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.feiji.cloud.alert.AlertService;
import com.feiji.cloud.live.LiveStreamService;
import com.feiji.cloud.mqtt.MqttTopicEnum;
import com.feiji.cloud.mqtt.MqttTopicHandler;
import com.feiji.cloud.wayline.WaylineJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 设备事件上报（thing/product/{sn}/events）。
 * 处理直播能力更新、航线任务进度与 HMS 健康告警（device_hms / device_exit_hms）。
 */
@Component
public class EventsHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsHandler.class);

    private final LiveStreamService liveStreamService;
    private final WaylineJobService waylineJobService;
    private final AlertService alertService;

    public EventsHandler(ObjectProvider<LiveStreamService> liveStreamService,
                         ObjectProvider<WaylineJobService> waylineJobService,
                         AlertService alertService) {
        this.liveStreamService = liveStreamService.getIfAvailable();
        this.waylineJobService = waylineJobService.getIfAvailable();
        this.alertService = alertService;
    }

    @Override
    public MqttTopicEnum topic() {
        return MqttTopicEnum.EVENTS;
    }

    @Override
    public void handle(String sn, JsonNode message) {
        String method = message.path("method").asText(null);
        JsonNode data = message.path("data");
        if ("live_capacity_update".equals(method) && liveStreamService != null) {
            liveStreamService.onLiveCapacity(sn, data);
            return;
        }
        if ("flighttask_progress".equals(method) && waylineJobService != null) {
            waylineJobService.onProgress(sn, data);
            return;
        }
        if ("device_hms".equals(method)) {
            alertService.onHms(sn, data, false);
            return;
        }
        if ("device_exit_hms".equals(method)) {
            alertService.onHms(sn, data, true);
            return;
        }
        log.debug("events sn={} method={} payload={}", sn, method, message);
    }
}
