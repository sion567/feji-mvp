package com.feiji.cloud.flight;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.device.DeviceArchiveService;
import com.feiji.cloud.device.DeviceRegistryService;
import com.feiji.cloud.device.Telemetry;
import com.feiji.cloud.wayline.WaylineJobEntity;
import com.feiji.cloud.wayline.WaylineJobMapper;
import com.feiji.cloud.ws.WsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 架次记录：基于统一遥测的飞行状态机。
 * 起飞判定：mode 进入 TAKEOFF/FLYING/MISSION/RTH/LANDING；落地判定：离开上述状态持续 15 秒。
 * 架次内每 3 秒采样一条全量遥测落库；开架次时自动关联当前执行中的航线任务并快照其报备状态。
 */
@Service
public class FlightRecordService {

    private static final Logger log = LoggerFactory.getLogger(FlightRecordService.class);

    private static final Set<String> AIRBORNE_MODES = Set.of("TAKEOFF", "FLYING", "MISSION", "RTH", "LANDING");
    private static final long GROUND_CONFIRM_MS = 15_000;
    private static final long SAMPLE_INTERVAL_MS = 3_000;
    private static final long STALE_OPEN_MS = 10 * 60_000;

    private final FlightRecordMapper recordMapper;
    private final FlightTelemetryMapper telemetryMapper;
    private final WaylineJobMapper jobMapper;
    private final DeviceRegistryService registry;
    private final DeviceArchiveService archive;
    private final WsPushService wsPushService;

    private final Map<String, FlightState> states = new ConcurrentHashMap<>();

    private static final class FlightState {
        Long openId;
        Long groundSince;
        Long lastSampleTs;
        Double lastLat;
        Double lastLng;
        double distanceM;
        double maxHeight;
        Long lastTelemetryTs;
    }

    public FlightRecordService(FlightRecordMapper recordMapper, FlightTelemetryMapper telemetryMapper,
                               WaylineJobMapper jobMapper, DeviceRegistryService registry,
                               DeviceArchiveService archive, WsPushService wsPushService) {
        this.recordMapper = recordMapper;
        this.telemetryMapper = telemetryMapper;
        this.jobMapper = jobMapper;
        this.registry = registry;
        this.archive = archive;
        this.wsPushService = wsPushService;
    }

    /** 由 OSD 处理器喂入无人机遥测（kind=drone） */
    public void onTelemetry(Telemetry t) {
        if (!"drone".equals(t.kind())) {
            return;
        }
        FlightState st = states.computeIfAbsent(t.sn(), key -> new FlightState());
        synchronized (st) {
            boolean airborne = t.mode() != null && AIRBORNE_MODES.contains(t.mode());
            if (airborne) {
                st.groundSince = null;
                if (st.openId == null) {
                    open(st, t);
                }
                update(st, t);
            } else {
                if (st.groundSince == null) {
                    st.groundSince = t.timestamp();
                }
                if (st.openId != null && t.timestamp() - st.groundSince >= GROUND_CONFIRM_MS) {
                    close(st, t);
                }
            }
        }
    }

    private void open(FlightState st, Telemetry t) {
        WaylineJobEntity job = activeJobOf(t.sn());
        FlightRecordEntity record = new FlightRecordEntity();
        record.setDroneSn(t.sn());
        record.setStatus("FLYING");
        record.setTakeoffTime(t.timestamp());
        record.setTakeoffLat(t.latitude());
        record.setTakeoffLng(t.longitude());
        record.setDistanceM(0.0);
        record.setMaxHeight(heightOf(t));
        record.setWorkspaceId(job != null ? job.getWorkspaceId() : archive.workspaceOf(t.sn()));
        if (job != null) {
            record.setJobId(job.getId());
            record.setJobName(job.getName());
            record.setReportStatus(job.getReportStatus() == null ? "NONE" : job.getReportStatus());
            record.setReportNo(job.getReportNo());
        } else {
            record.setReportStatus("NONE");
        }
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.insert(record);
        st.openId = record.getId();
        st.distanceM = 0;
        st.maxHeight = heightOf(t) == null ? 0 : heightOf(t);
        st.lastLat = t.latitude();
        st.lastLng = t.longitude();
        wsPushService.push("flight_update", Map.of(
                "id", record.getId(), "status", "FLYING", "droneSn", t.sn()));
        log.info("架次开始: id={}, sn={}, jobId={}", record.getId(), t.sn(), job == null ? null : job.getId());
    }

    private void update(FlightState st, Telemetry t) {
        // 里程与最大高度
        if (t.latitude() != null && t.longitude() != null) {
            if (st.lastLat != null && st.lastLng != null) {
                st.distanceM += haversine(st.lastLat, st.lastLng, t.latitude(), t.longitude());
            }
            st.lastLat = t.latitude();
            st.lastLng = t.longitude();
        }
        Double h = heightOf(t);
        if (h != null && h > st.maxHeight) {
            st.maxHeight = h;
        }
        st.lastTelemetryTs = t.timestamp();

        // 采样落库（3 秒/点），并刷新架次行（供列表与超时兜底）
        if (st.lastSampleTs == null || t.timestamp() - st.lastSampleTs >= SAMPLE_INTERVAL_MS) {
            st.lastSampleTs = t.timestamp();
            FlightTelemetryEntity sample = new FlightTelemetryEntity();
            sample.setFlightId(st.openId);
            sample.setTs(t.timestamp());
            sample.setLat(t.latitude());
            sample.setLng(t.longitude());
            sample.setAlt(t.altitude());
            sample.setElevation(t.elevation());
            sample.setSpeed(t.horizontalSpeed());
            sample.setHeading(t.heading());
            sample.setBattery(t.batteryPercent());
            sample.setMode(t.mode());
            telemetryMapper.insert(sample);

            FlightRecordEntity record = recordMapper.selectById(st.openId);
            if (record != null) {
                record.setDistanceM(Math.round(st.distanceM * 10) / 10.0);
                record.setMaxHeight(Math.round(st.maxHeight * 10) / 10.0);
                record.setUpdatedAt(LocalDateTime.now());
                recordMapper.updateById(record);
            }
        }
    }

    private void close(FlightState st, Telemetry t) {
        FlightRecordEntity record = recordMapper.selectById(st.openId);
        if (record != null) {
            record.setStatus("CLOSED");
            record.setLandingTime(st.groundSince);
            record.setLandingLat(t.latitude());
            record.setLandingLng(t.longitude());
            record.setDistanceM(Math.round(st.distanceM * 10) / 10.0);
            record.setMaxHeight(Math.round(st.maxHeight * 10) / 10.0);
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
            wsPushService.push("flight_update", Map.of(
                    "id", record.getId(), "status", "CLOSED", "droneSn", t.sn()));
            log.info("架次结束: id={}, sn={}, 时长={}s, 里程={}m",
                    record.getId(), t.sn(),
                    (st.groundSince - record.getTakeoffTime()) / 1000,
                    record.getDistanceM());
        }
        st.openId = null;
        st.groundSince = null;
        st.lastSampleTs = null;
        st.lastLat = null;
        st.lastLng = null;
        st.distanceM = 0;
        st.maxHeight = 0;
    }

    /** 设备长时间失联（OSD 停止）兜底：开启超 10 分钟无遥测的架次强制关闭 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void sweepStaleFlights() {
        long now = System.currentTimeMillis();
        for (FlightState st : states.values()) {
            synchronized (st) {
                if (st.openId == null || st.lastTelemetryTs == null
                        || now - st.lastTelemetryTs < STALE_OPEN_MS) {
                    continue;
                }
                FlightRecordEntity record = recordMapper.selectById(st.openId);
                if (record != null) {
                    record.setStatus("CLOSED");
                    record.setLandingTime(st.lastTelemetryTs);
                    record.setDistanceM(Math.round(st.distanceM * 10) / 10.0);
                    record.setMaxHeight(Math.round(st.maxHeight * 10) / 10.0);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                    log.warn("架次因失联强制关闭: id={}", record.getId());
                }
                st.openId = null;
            }
        }
    }

    // ---------------- 查询 ----------------

    public List<FlightRecordEntity> list(Long workspaceId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return recordMapper.selectList(new QueryWrapper<FlightRecordEntity>()
                .eq("workspace_id", workspaceId)
                .orderByDesc("id")
                .last("LIMIT " + safeLimit));
    }

    public FlightRecordEntity requireById(Long id) {
        FlightRecordEntity record = recordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("架次不存在: id=" + id);
        }
        return record;
    }

    public List<FlightTelemetryEntity> telemetry(Long flightId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20_000);
        return telemetryMapper.selectList(new QueryWrapper<FlightTelemetryEntity>()
                .eq("flight_id", flightId)
                .orderByAsc("ts")
                .last("LIMIT " + safeLimit));
    }

    // ---------------- 内部 ----------------

    private Double heightOf(Telemetry t) {
        return t.elevation() != null ? t.elevation() : t.altitude();
    }

    /** 活动任务匹配：任务登记在网关（机场）SN，遥测来自无人机 SN，两者都查 */
    private WaylineJobEntity activeJobOf(String droneSn) {
        List<WaylineJobEntity> jobs = jobMapper.selectList(new QueryWrapper<WaylineJobEntity>()
                .in("status", List.of("DISPATCHING", "RUNNING"))
                .orderByDesc("id"));
        if (jobs.isEmpty()) {
            return null;
        }
        for (WaylineJobEntity job : jobs) {
            if (droneSn.equals(job.getDroneSn())) {
                return job;
            }
        }
        var entry = registry.entry(droneSn);
        if (entry != null && entry.parentSn() != null) {
            for (WaylineJobEntity job : jobs) {
                if (entry.parentSn().equals(job.getDroneSn())) {
                    return job;
                }
            }
        }
        return null;
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
