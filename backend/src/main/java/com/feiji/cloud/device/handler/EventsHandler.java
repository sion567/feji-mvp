package com.feiji.cloud.device.handler;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 目前处理直播能力更新与航线任务进度；HMS 等事件二期接入。
 */
@Component
public class EventsHandler implements MqttTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsHandler.class);

    private final LiveStreamService liveStreamService;
    private final WaylineJobService waylineJobService;

    public EventsHandler(ObjectProvider<LiveStreamService> liveStreamService,
                         ObjectProvider<WaylineJobService> waylineJobService) {
        this.liveStreamService = liveStreamService.getIfAvailable();
        this.waylineJobService = waylineJobService.getIfAvailable();
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
        // TODO: HMS 健康告警等事件处理
        log.debug("events sn={} method={} payload={}", sn, method, message);
    }
}
