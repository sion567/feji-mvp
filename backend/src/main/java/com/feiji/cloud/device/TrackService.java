package com.feiji.cloud.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 历史轨迹（MVP）：Redis 环形列表存储最近 2000 个位置点（约 5 秒/点 ≈ 2.7 小时），保留 7 天。
 * 完整轨迹存储随二期落库扩展。
 */
@Service
public class TrackService {

    private static final Logger log = LoggerFactory.getLogger(TrackService.class);

    private static final String KEY_PREFIX = "feiji:track:";
    private static final int MAX_POINTS = 2000;
    private static final long MIN_INTERVAL_MS = 5000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> lastAppendAt = new ConcurrentHashMap<>();

    public TrackService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void append(String sn, Telemetry telemetry) {
        if (telemetry == null || telemetry.longitude() == null || telemetry.latitude() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAppendAt.get(sn);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        lastAppendAt.put(sn, now);
        try {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("lng", telemetry.longitude());
            point.put("lat", telemetry.latitude());
            point.put("alt", telemetry.altitude());
            point.put("ts", telemetry.timestamp() > 0 ? telemetry.timestamp() : now);
            String key = KEY_PREFIX + sn;
            redis.opsForList().leftPush(key, objectMapper.writeValueAsString(point));
            redis.opsForList().trim(key, 0, MAX_POINTS - 1);
            redis.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.debug("轨迹点写入失败, sn={}: {}", sn, e.getMessage());
        }
    }

    /** 按时间正序返回轨迹点 {lng, lat, alt, ts} */
    public List<Map<String, Object>> track(String sn) {
        try {
            List<String> raw = redis.opsForList().range(KEY_PREFIX + sn, 0, -1);
            List<Map<String, Object>> result = new ArrayList<>();
            if (raw == null) {
                return result;
            }
            for (int i = raw.size() - 1; i >= 0; i--) {
                result.add(objectMapper.readValue(raw.get(i), Map.class));
            }
            return result;
        } catch (Exception e) {
            log.warn("轨迹查询失败, sn={}: {}", sn, e.getMessage());
            return List.of();
        }
    }

    public void clear(String sn) {
        try {
            redis.delete(KEY_PREFIX + sn);
        } catch (Exception e) {
            log.warn("轨迹清除失败, sn={}: {}", sn, e.getMessage());
        }
    }
}
