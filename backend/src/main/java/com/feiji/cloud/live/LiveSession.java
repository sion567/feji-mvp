package com.feiji.cloud.live;

import java.util.Map;

/**
 * 直播会话（不可变，状态变更用 withState 生成新实例替换）。
 */
public record LiveSession(
        String bid,
        String gatewaySn,
        String videoId,
        String streamId,
        Integer videoQuality,
        LiveSessionState state,
        long startedAt,
        Map<String, String> playUrls) {

    public LiveSession withState(LiveSessionState newState) {
        return new LiveSession(bid, gatewaySn, videoId, streamId, videoQuality, newState, startedAt, playUrls);
    }

    public Map<String, Object> toView() {
        return Map.of(
                "videoId", videoId,
                "gatewaySn", gatewaySn,
                "streamId", streamId,
                "videoQuality", videoQuality == null ? 0 : videoQuality,
                "state", state.name(),
                "startedAt", startedAt,
                "playUrls", playUrls);
    }
}
