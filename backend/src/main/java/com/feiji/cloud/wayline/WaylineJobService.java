package com.feiji.cloud.wayline;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.mqtt.ServicesReplyListener;
import com.feiji.cloud.vendor.spi.CommandRouter;
import com.feiji.cloud.vendor.spi.VendorAdapter;
import com.feiji.cloud.wayline.storage.WaylineStorageService;
import com.feiji.cloud.ws.WsPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 航线任务：创建/派发/取消/进度跟踪 + 定时与每日巡检调度。
 *
 * 协议（对齐官方 flighttask 指令族）：
 * - flighttask_prepare: data={flight_id, execute_time, task_type(0立即/1定时), file:{url, fingerprint}}
 * - flighttask_execute: data={flight_id}（立即任务 prepare 应答后下发）
 * - flighttask_cancel:  data={flight_id}
 * - 事件 flighttask_progress: data={status, progress:{percent}, ext}
 *
 * MVP 简化：进度按「设备 SN + 活动状态」关联任务（单设备同时只跑一个任务）；
 * RECURRING(每日巡检) 到点派发一次，终态后自动复位 PENDING 等待次日。
 */
@Service
public class WaylineJobService implements ServicesReplyListener {

    private static final Logger log = LoggerFactory.getLogger(WaylineJobService.class);

    public static final String TASK_IMMEDIATE = "IMMEDIATE";
    public static final String TASK_TIMED = "TIMED";
    public static final String TASK_RECURRING = "RECURRING";

    private static final long DISPATCH_WINDOW_MS = 60_000;

    private final WaylineJobMapper jobMapper;
    private final WaylineLibraryService library;
    private final WaylineStorageService storage;
    private final CommandRouter commandRouter;
    private final WsPushService wsPushService;
    private final ObjectMapper objectMapper;

    /** 每日巡检任务当天是否已派发（单实例内存态，重启后当天最多重发一次，可接受） */
    private final Map<Long, LocalDate> recurringDispatchedDay = new ConcurrentHashMap<>();

    public WaylineJobService(WaylineJobMapper jobMapper, WaylineLibraryService library,
                             WaylineStorageService storage, CommandRouter commandRouter,
                             WsPushService wsPushService, ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.library = library;
        this.storage = storage;
        this.commandRouter = commandRouter;
        this.wsPushService = wsPushService;
        this.objectMapper = objectMapper;
    }

    // ---------------- 创建 ----------------

    public WaylineJobEntity create(CreateJobRequest request, LoginUser user) {
        WaylineFileEntity file = library.requireById(request.waylineFileId());
        long now = System.currentTimeMillis();

        WaylineJobEntity job = new WaylineJobEntity();
        job.setName(request.name() == null || request.name().isBlank()
                ? file.getName() + "-" + now : request.name());
        job.setFlightId(generateFlightId());
        job.setWaylineFileId(request.waylineFileId());
        job.setDroneSn(request.droneSn());
        job.setWorkspaceId(user.workspaceId());
        job.setCreator(user.username());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setProgress(0);

        switch (request.taskType()) {
            case TASK_IMMEDIATE -> {
                job.setTaskType(TASK_IMMEDIATE);
                job.setExecuteTime(now);
                job.setStatus("PENDING");
            }
            case TASK_TIMED -> {
                if (request.executeTime() == null || request.executeTime() < now) {
                    throw new BusinessException("定时任务需要一个未来的执行时间");
                }
                job.setTaskType(TASK_TIMED);
                job.setExecuteTime(request.executeTime());
                job.setStatus("PENDING");
            }
            case TASK_RECURRING -> {
                if (request.executeTime() == null || request.executeTime() < 0 || request.executeTime() >= 86_400_000L) {
                    throw new BusinessException("每日巡检需提供当天内的执行时刻（毫秒，自 00:00 起）");
                }
                job.setTaskType(TASK_RECURRING);
                job.setExecuteTime(request.executeTime());
                job.setStatus("PENDING");
                job.setMessage("每日 " + millisOfDayToText(request.executeTime()) + " 自动巡检");
            }
            default -> throw new BusinessException("不支持的任务类型: " + request.taskType());
        }
        jobMapper.insert(job);

        if (TASK_IMMEDIATE.equals(job.getTaskType())) {
            dispatch(job);
        }
        return job;
    }

    public record CreateJobRequest(String name, Long waylineFileId, String droneSn,
                                   String taskType, Long executeTime) {
    }

    // ---------------- 派发 ----------------

    private void dispatch(WaylineJobEntity job) {
        WaylineFileEntity file = library.requireById(job.getWaylineFileId());
        byte[] kmz = storage.load(file.getFileKey());
        boolean immediate = TASK_IMMEDIATE.equals(job.getTaskType());
        long executeTime = immediate ? System.currentTimeMillis() : nextDispatchTime(job);

        String bid = commandRouter.route(job.getDroneSn()).prepareMission(new VendorAdapter.PrepareMissionCommand(
                job.getDroneSn(), job.getFlightId(), executeTime, immediate,
                library.downloadUrl(file), DigestUtils.md5DigestAsHex(kmz), 100));

        job.setBid(bid);
        job.setStatus("DISPATCHING");
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        wsPushService.push("job_update", toView(job));
        log.info("航线任务已派发: id={}, flight_id={}, drone={}", job.getId(), job.getFlightId(), job.getDroneSn());
    }

    /** TIMED 用设定的未来时间；RECURRING 用当天时刻 + 已过则次日 */
    private long nextDispatchTime(WaylineJobEntity job) {
        if (TASK_RECURRING.equals(job.getTaskType())) {
            long dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long candidate = dayStart + job.getExecuteTime();
            return candidate <= System.currentTimeMillis() ? candidate + 86_400_000L : candidate;
        }
        return job.getExecuteTime();
    }

    // ---------------- 取消 ----------------

    public void cancel(Long jobId, LoginUser user) {
        WaylineJobEntity job = requireById(jobId);
        if (List.of("SUCCESS", "FAILED", "CANCELED").contains(job.getStatus())) {
            return;
        }
        if ("PENDING".equals(job.getStatus()) && job.getBid() == null) {
            // 尚未派发过（定时未到），直接本地取消
            updateStatus(job, "CANCELED", "手动取消（未派发）");
            return;
        }
        commandRouter.route(job.getDroneSn()).cancelMission(
                job.getDroneSn(), job.getBid(), job.getFlightId());
        log.info("航线任务取消指令已下发: id={}", jobId);
    }

    // ---------------- 进度 ----------------

    public void onProgress(String sn, JsonNode data) {
        String status = data.path("status").asText("");
        int percent = data.path("progress").path("percent").asInt(-1);
        WaylineJobEntity job = activeJobBySn(sn);
        if (job == null) {
            log.debug("收到 flighttask_progress 但无活动任务, sn={}, status={}", sn, status);
            return;
        }
        switch (status) {
            case "in_progress" -> {
                job.setStatus("RUNNING");
                if (percent >= 0) {
                    job.setProgress(percent);
                }
            }
            case "ok" -> {
                job.setStatus("SUCCESS");
                job.setProgress(100);
            }
            case "failed" -> job.setStatus("FAILED");
            case "canceled" -> job.setStatus("CANCELED");
            default -> log.debug("未知任务进度状态: {}", status);
        }
        String message = data.path("ext").isEmpty() ? null : data.path("ext").toString();
        if (message != null && message.length() > 490) {
            message = message.substring(0, 490);
        }
        if (message != null) {
            job.setMessage(message);
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        wsPushService.push("job_update", toView(job));

        // 每日巡检：终态后复位等待次日
        if (List.of("SUCCESS", "FAILED", "CANCELED").contains(job.getStatus())
                && TASK_RECURRING.equals(job.getTaskType())) {
            job.setStatus("PENDING");
            job.setProgress(0);
            job.setMessage("每日 " + millisOfDayToText(job.getExecuteTime()) + " 自动巡检");
            jobMapper.updateById(job);
        }
        log.info("任务进度: jobId={}, status={}, percent={}", job.getId(), status, percent);
    }

    // ---------------- services_reply 配对 ----------------

    @Override
    public void onServicesReply(String sn, String method, String bid, JsonNode data) {
        if (bid == null) {
            return;
        }
        WaylineJobEntity job = jobMapper.selectOne(new QueryWrapper<WaylineJobEntity>().eq("bid", bid)
                .orderByDesc("id").last("LIMIT 1"));
        if (job == null) {
            return;
        }
        int result = data.path("result").asInt(-1);
        switch (method) {
            case "flighttask_prepare" -> {
                if (result != 0) {
                    updateStatus(job, "FAILED", "任务准备失败, result=" + result);
                    return;
                }
                if (TASK_IMMEDIATE.equals(job.getTaskType())) {
                    String executeBid = commandRouter.route(job.getDroneSn())
                            .executeMission(job.getDroneSn(), job.getFlightId());
                    job.setBid(executeBid);
                    job.setUpdatedAt(LocalDateTime.now());
                    jobMapper.updateById(job);
                }
            }
            case "flighttask_execute" -> {
                if (result == 0) {
                    updateStatus(job, "RUNNING", "设备已接受任务");
                } else {
                    updateStatus(job, "FAILED", "任务执行被拒绝, result=" + result);
                }
            }
            case "flighttask_cancel" -> {
                if (result == 0) {
                    WaylineJobEntity latest = requireById(job.getId());
                    if (TASK_RECURRING.equals(latest.getTaskType())) {
                        updateStatus(latest, "PENDING", "已取消本次执行");
                    } else {
                        updateStatus(latest, "CANCELED", "手动取消");
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---------------- 调度 ----------------

    /** initialDelay 避免调度首跑早于建表初始化 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void sweepScheduledJobs() {
        long now = System.currentTimeMillis();
        // 超时兜底：DISPATCHING/RUNNING 超过 30 分钟无任何更新视为失败（设备断联等）
        for (WaylineJobEntity job : jobMapper.selectList(new QueryWrapper<WaylineJobEntity>()
                .in("status", List.of("DISPATCHING", "RUNNING")))) {
            if (job.getUpdatedAt() != null
                    && job.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() < now - 30 * 60_000L) {
                updateStatus(job, "FAILED", "执行超时（30 分钟无进度）");
            }
        }
        // 定时任务到点派发
        for (WaylineJobEntity job : byStatus("PENDING")) {
            if (TASK_TIMED.equals(job.getTaskType()) && job.getExecuteTime() != null
                    && job.getExecuteTime() <= now) {
                dispatch(job);
            }
        }
        // 每日巡检：到达当天时刻（60 秒窗口）且今日未派发
        for (WaylineJobEntity job : byStatus("PENDING")) {
            if (!TASK_RECURRING.equals(job.getTaskType()) || job.getExecuteTime() == null) {
                continue;
            }
            long dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long due = dayStart + job.getExecuteTime();
            boolean inWindow = now >= due && now - due < DISPATCH_WINDOW_MS;
            boolean notDispatchedToday = !LocalDate.now().equals(recurringDispatchedDay.get(job.getId()));
            if (inWindow && notDispatchedToday) {
                recurringDispatchedDay.put(job.getId(), LocalDate.now());
                job.setFlightId(generateFlightId());
                dispatch(job);
            }
        }
    }

    // ---------------- 查询 ----------------

    public List<WaylineJobEntity> list(Long workspaceId) {
        return jobMapper.selectList(new QueryWrapper<WaylineJobEntity>()
                .eq("workspace_id", workspaceId)
                .orderByDesc("id"));
    }

    public WaylineJobEntity requireById(Long id) {
        WaylineJobEntity job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException("任务不存在: id=" + id);
        }
        return job;
    }

    public Map<String, Object> toView(WaylineJobEntity job) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", job.getId());
        view.put("name", job.getName());
        view.put("flightId", job.getFlightId());
        view.put("waylineFileId", job.getWaylineFileId());
        view.put("droneSn", job.getDroneSn());
        view.put("taskType", job.getTaskType());
        view.put("executeTime", job.getExecuteTime());
        view.put("status", job.getStatus());
        view.put("progress", job.getProgress());
        view.put("message", job.getMessage());
        view.put("reportStatus", job.getReportStatus() == null ? "NONE" : job.getReportStatus());
        view.put("reportNo", job.getReportNo());
        view.put("creator", job.getCreator());
        view.put("updatedAt", job.getUpdatedAt() == null ? null : job.getUpdatedAt().toString());
        return view;
    }

    /** 报备登记（NONE/FILED/APPROVED + 批复号）；报备责任在运营单位，平台留痕 */
    public void updateReport(Long id, String reportStatus, String reportNo) {
        WaylineJobEntity job = requireById(id);
        if (!List.of("NONE", "FILED", "APPROVED").contains(reportStatus)) {
            throw new BusinessException("非法报备状态: " + reportStatus);
        }
        job.setReportStatus(reportStatus);
        job.setReportNo("APPROVED".equals(reportStatus) ? reportNo : null);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    /** 超 120 米航线生成合规提醒（适飞空域真高 120 米以下免申请，超过需 UOM 飞行活动申请） */
    public String complianceWarningOf(WaylineJobEntity job) {
        Double maxH;
        try {
            maxH = library.maxExecuteHeight(library.requireById(job.getWaylineFileId()));
        } catch (Exception e) {
            return null;
        }
        if (maxH != null && maxH > 120) {
            return "航线最大执行高度 " + maxH + " 米（超过 120 米）：适飞空域内超 120 米飞行需提前 1 日 12 时前"
                    + "在民航局 UOM 平台提出飞行活动申请，管制空域一律需要申请。请确认已获批复后再执行。";
        }
        return null;
    }

    // ---------------- 内部 ----------------

    private WaylineJobEntity activeJobBySn(String sn) {
        return jobMapper.selectOne(new QueryWrapper<WaylineJobEntity>()
                .eq("drone_sn", sn)
                .in("status", List.of("DISPATCHING", "RUNNING"))
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    private List<WaylineJobEntity> byStatus(String status) {
        return jobMapper.selectList(new QueryWrapper<WaylineJobEntity>().eq("status", status));
    }

    private void updateStatus(WaylineJobEntity job, String status, String message) {
        job.setStatus(status);
        job.setMessage(message);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        wsPushService.push("job_update", toView(job));
    }

    /** flight_id 不允许 . _ 等字符（官方正则约束），时间戳+短随机即满足 */
    private String generateFlightId() {
        return "task" + System.currentTimeMillis()
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String millisOfDayToText(Long millisOfDay) {
        long totalMinutes = millisOfDay / 60000;
        return "%02d:%02d".formatted(totalMinutes / 60, totalMinutes % 60);
    }
}
