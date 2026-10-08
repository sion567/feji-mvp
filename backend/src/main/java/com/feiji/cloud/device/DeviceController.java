package com.feiji.cloud.device;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备列表 = MySQL 档案（名称/型号/拓扑，按工作空间隔离） ⊕ Redis 实时态（在线/最近活动/OSD 识别类型）。
 * 数据库不可用时自动退化为 Redis-only 列表。
 */
@Validated
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceRegistryService registry;
    private final DeviceArchiveService archive;
    private final TrackService trackService;

    public DeviceController(DeviceRegistryService registry, DeviceArchiveService archive, TrackService trackService) {
        this.registry = registry;
        this.archive = archive;
        this.trackService = trackService;
    }

    public record RenameRequest(@NotBlank(message = "设备名称不能为空") String deviceName) {
    }

    @GetMapping
    public HttpResult<List<Map<String, Object>>> list(@CurrentUser LoginUser user) {
        // Redis 实时态
        Map<String, DeviceRegistryService.DeviceEntry> realtime = new LinkedHashMap<>();
        for (DeviceRegistryService.DeviceEntry entry : registry.listDevices()) {
            realtime.put(entry.sn(), entry);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        List<DeviceEntity> archived;
        try {
            archived = archive.listByWorkspace(user.workspaceId());
        } catch (Exception e) {
            log.warn("设备档案查询失败，退化为 Redis-only 列表: {}", e.getMessage());
            archived = List.of();
        }

        for (DeviceEntity entity : archived) {
            result.add(merge(entity, realtime.remove(entity.getSn())));
        }
        // 档案之外、仅出现在实时态的设备（档案尚未同步或库不可用）
        for (DeviceRegistryService.DeviceEntry entry : realtime.values()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("sn", entry.sn());
            view.put("vendor", null);
            view.put("deviceName", entry.deviceName());
            view.put("modelName", entry.modelName());
            view.put("model", entry.model());
            view.put("kind", entry.kind());
            view.put("online", entry.online());
            view.put("parentSn", entry.parentSn());
            view.put("lastSeen", entry.lastSeen());
            result.add(view);
        }
        return HttpResult.ok(result);
    }

    private Map<String, Object> merge(DeviceEntity entity, DeviceRegistryService.DeviceEntry realtime) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sn", entity.getSn());
        view.put("vendor", entity.getVendor());
        view.put("deviceName", entity.getDeviceName());
        view.put("modelName", entity.getModelName() != null ? entity.getModelName()
                : realtime != null ? realtime.modelName() : null);
        view.put("model", entity.getModelKey() != null ? entity.getModelKey()
                : realtime != null ? realtime.model() : null);
        view.put("kind", entity.getKind() != null ? entity.getKind()
                : realtime != null ? realtime.kind() : null);
        view.put("online", realtime != null && realtime.online());
        view.put("parentSn", entity.getParentSn() != null ? entity.getParentSn()
                : realtime != null ? realtime.parentSn() : null);
        view.put("lastSeen", realtime != null ? realtime.lastSeen() : 0L);
        return view;
    }

    @PatchMapping("/{sn}")
    @RequireRole(Role.OPERATOR)
    @Audited("设备改名")
    public HttpResult<Void> rename(@PathVariable String sn, @Valid @RequestBody RenameRequest request) {
        archive.rename(sn, request.deviceName());
        return HttpResult.ok();
    }

    @DeleteMapping("/{sn}")
    @RequireRole(Role.OPERATOR)
    @Audited("移除设备")
    public HttpResult<Void> unbind(@PathVariable String sn) {
        archive.unbind(sn);
        registry.remove(sn);
        trackService.clear(sn);
        return HttpResult.ok();
    }

    @GetMapping("/{sn}/osd")
    public HttpResult<Object> latestOsd(@PathVariable String sn) {
        return HttpResult.ok(registry.latestOsd(sn));
    }

    @GetMapping("/{sn}/track")
    public HttpResult<List<Map<String, Object>>> track(@PathVariable String sn) {
        return HttpResult.ok(trackService.track(sn));
    }
}
