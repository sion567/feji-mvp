package com.feiji.cloud.live;

import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.config.AppProperties;
import com.feiji.cloud.mqtt.ServicesReplyListener;
import com.feiji.cloud.ws.WsPushService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直播服务：下发 live_start_push/live_stop_push 指令，跟踪会话状态机，
 * 生成 ZLMediaKit 播放地址并通过 WebSocket 推送直播事件。
 *
 * RTMP 推流地址按「完整地址（含流名）」下发；真机联调如确认 Pilot/机场只接受
 * host/app 形式并自行追加流名，需调整 {@link #buildPushUrl} 与播放地址的对应关系。
 */
@Service
@ConditionalOnProperty(prefix = "app.live", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveStreamService implements ServicesReplyListener {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamService.class);

    private final com.feiji.cloud.vendor.spi.CommandRouter commandRouter;
    private final WsPushService wsPushService;
    private final AppProperties appProperties;

    /** bid → 会话；应答到达或超时巡检后移除/更新 */
    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    /** sn → 最近一次上报的直播能力（live_capacity） */
    private final Map<String, JsonNode> capacities = new ConcurrentHashMap<>();

    private volatile RestClient zlmClient;

    public LiveStreamService(com.feiji.cloud.vendor.spi.CommandRouter commandRouter,
                             WsPushService wsPushService, AppProperties appProperties) {
        this.commandRouter = commandRouter;
        this.wsPushService = wsPushService;
        this.appProperties = appProperties;
    }

    // ---------------- 对外接口 ----------------

    public LiveSession start(String gatewaySn, String droneSn, Integer videoQuality) {
        String childSn = droneSn == null || droneSn.isBlank() ? gatewaySn : droneSn;
        String cameraIndex = resolveCameraIndex(gatewaySn);
        String videoId = gatewaySn + "/" + childSn + "/" + cameraIndex + "/normal";

        // 同一 videoId 同时只允许一路直播
        LiveSession existing = findByVideoId(videoId);
        if (existing != null && existing.state() != LiveSessionState.FAILED) {
            throw new BusinessException("该设备已在直播中: " + videoId);
        }

        String streamId = (gatewaySn + "_" + childSn + "_" + cameraIndex + "_normal")
                .replaceAll("[^a-zA-Z0-9_-]", "_");

        String bid = commandRouter.route(gatewaySn).startLive(new com.feiji.cloud.vendor.spi.VendorAdapter.LiveStartCommand(
                gatewaySn, videoId, buildPushUrl(streamId), videoQuality));

        LiveSession session = new LiveSession(bid, gatewaySn, videoId, streamId,
                videoQuality == null ? 0 : videoQuality, LiveSessionState.STARTING,
                System.currentTimeMillis(), buildPlayUrls(streamId));
        sessions.put(bid, session);
        wsPushService.push("live_update", session.toView());
        log.info("下发开播指令: gateway={}, videoId={}, streamId={}", gatewaySn, videoId, streamId);
        return session;
    }

    public void stop(String videoId) {
        LiveSession session = findByVideoId(videoId);
        if (session == null) {
            throw new BusinessException("直播会话不存在: " + videoId);
        }
        if (session.state() == LiveSessionState.STOPPING) {
            return;
        }
        // 复用会话 bid 发停播指令，应答按同一 bid 配对
        commandRouter.route(session.gatewaySn()).stopLive(new com.feiji.cloud.vendor.spi.VendorAdapter.LiveStopCommand(
                session.gatewaySn(), session.bid(), videoId));
        sessions.put(session.bid(), session.withState(LiveSessionState.STOPPING));
        closeZlmStream(session.streamId());
        wsPushService.push("live_update", session.withState(LiveSessionState.STOPPING).toView());
        log.info("下发停播指令: videoId={}", videoId);
    }

    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        sessions.values().stream()
                .sorted((a, b) -> Long.compare(b.startedAt(), a.startedAt()))
                .forEach(session -> result.add(session.toView()));
        return result;
    }

    public JsonNode capacityOf(String sn) {
        return capacities.get(sn);
    }

    /** 设备上报直播能力（events: live_capacity_update）时调用 */
    public void onLiveCapacity(String sn, JsonNode data) {
        JsonNode capacity = data.has("live_capacity") ? data.path("live_capacity") : data;
        capacities.put(sn, capacity);
        wsPushService.push("live_capacity_update", Map.of("sn", sn, "capacity", capacity));
        log.info("直播能力更新: sn={}", sn);
    }

    // ---------------- services_reply 配对 ----------------

    @Override
    public void onServicesReply(String sn, String method, String bid, JsonNode data) {
        LiveSession session = bid == null ? null : sessions.get(bid);
        if (session == null) {
            return;
        }
        int result = data.path("result").asInt(-1);
        switch (method) {
            case "live_start_push" -> {
                if (result == 0) {
                    sessions.put(bid, session.withState(LiveSessionState.LIVE));
                    wsPushService.push("live_update", session.withState(LiveSessionState.LIVE).toView());
                    log.info("开播成功: videoId={}", session.videoId());
                } else {
                    sessions.remove(bid);
                    wsPushService.push("live_update", session.withState(LiveSessionState.FAILED).toView());
                    log.warn("开播失败: videoId={}, result={}", session.videoId(), result);
                }
            }
            case "live_stop_push" -> {
                sessions.remove(bid);
                wsPushService.push("live_update", session.toView());
                log.info("停播完成: videoId={}", session.videoId());
            }
            default -> {
            }
        }
    }

    // ---------------- 超时巡检 ----------------

    @Scheduled(fixedDelay = 5000, initialDelay = 10_000)
    public void sweepTimeoutSessions() {
        long timeout = appProperties.live().replyTimeoutMs();
        long now = System.currentTimeMillis();
        sessions.values().stream()
                .filter(session -> session.state() == LiveSessionState.STARTING && now - session.startedAt() > timeout)
                .forEach(session -> {
                    sessions.remove(session.bid());
                    wsPushService.push("live_update", session.withState(LiveSessionState.FAILED).toView());
                    log.warn("开播应答超时: videoId={}", session.videoId());
                });
        sessions.values().stream()
                .filter(session -> session.state() == LiveSessionState.STOPPING && now - session.startedAt() > timeout)
                .forEach(session -> {
                    sessions.remove(session.bid());
                    wsPushService.push("live_update", session.toView());
                    log.warn("停播应答超时, 强制移除: videoId={}", session.videoId());
                });
    }

    // ---------------- 内部工具 ----------------

    private LiveSession findByVideoId(String videoId) {
        return sessions.values().stream()
                .filter(session -> session.videoId().equals(videoId))
                .findFirst()
                .orElse(null);
    }

    private String resolveCameraIndex(String gatewaySn) {
        JsonNode capacity = capacities.get(gatewaySn);
        if (capacity != null) {
            JsonNode camera = capacity.path("camera_list").path(0).path("camera_index");
            if (!camera.isMissingNode() && !camera.asText().isBlank()) {
                return camera.asText();
            }
        }
        return "39-0-7";
    }

    private String buildPushUrl(String streamId) {
        AppProperties.Live live = appProperties.live();
        return "rtmp://" + live.rtmpHost() + "/" + live.app() + "/" + streamId;
    }

    private Map<String, String> buildPlayUrls(String streamId) {
        AppProperties.Live live = appProperties.live();
        String base = live.playBaseUrl();
        String http = base + "/" + live.app() + "/" + streamId;
        String wsBase = base.replaceFirst("^http", "ws");
        // ZLM 与 SRS 的播放地址规则不同，按配置的流媒体类型生成
        if ("srs".equalsIgnoreCase(live.mediaServerType())) {
            return Map.of(
                    "http-flv", http + ".flv",
                    "hls", http + ".m3u8");
        }
        return Map.of(
                "http-flv", http + ".live.flv",
                "ws-flv", wsBase + "/" + live.app() + "/" + streamId + ".live.flv",
                "hls", http + "/hls.m3u8");
    }

    /** 停播时尽力关闭流媒体服务器上的流（SRS 无关流 API 或未配置 secret 则跳过） */
    private void closeZlmStream(String streamId) {
        AppProperties.Live live = appProperties.live();
        if ("srs".equalsIgnoreCase(live.mediaServerType())
                || live.zlmSecret() == null || live.zlmSecret().isBlank()) {
            return;
        }
        try {
            if (zlmClient == null) {
                synchronized (this) {
                    if (zlmClient == null) {
                        zlmClient = RestClient.create();
                    }
                }
            }
            zlmClient.get().uri(live.zlmUrl() + "/index/api/close_streams?secret={secret}&vhost=__defaultVhost__&app={app}&stream={stream}",
                            Map.of("secret", live.zlmSecret(), "app", live.app(), "stream", streamId))
                    .retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("关闭 ZLM 流失败, streamId={}: {}", streamId, e.getMessage());
        }
    }
}
