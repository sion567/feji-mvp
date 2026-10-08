package com.feiji.cloud.live;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/live")
@ConditionalOnProperty(prefix = "app.live", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveController {

    private final LiveStreamService liveStreamService;

    public LiveController(LiveStreamService liveStreamService) {
        this.liveStreamService = liveStreamService;
    }

    public record StartRequest(
            @NotBlank(message = "网关设备 SN 不能为空") String gatewaySn,
            String droneSn,
            Integer videoQuality) {
    }

    public record StopRequest(@NotBlank(message = "videoId 不能为空") String videoId) {
    }

    @PostMapping("/start")
    @RequireRole(Role.OPERATOR)
    @Audited("开始直播")
    public HttpResult<Map<String, Object>> start(@Validated @RequestBody StartRequest request) {
        LiveSession session = liveStreamService.start(
                request.gatewaySn(), request.droneSn(), request.videoQuality());
        return HttpResult.ok(session.toView());
    }

    @PostMapping("/stop")
    @RequireRole(Role.OPERATOR)
    @Audited("停止直播")
    public HttpResult<Void> stop(@Validated @RequestBody StopRequest request) {
        liveStreamService.stop(request.videoId());
        return HttpResult.ok();
    }

    @GetMapping("/sessions")
    public HttpResult<List<Map<String, Object>>> sessions() {
        return HttpResult.ok(liveStreamService.listSessions());
    }

    @GetMapping("/capacity/{sn}")
    public HttpResult<Object> capacity(@org.springframework.web.bind.annotation.PathVariable String sn) {
        return HttpResult.ok(liveStreamService.capacityOf(sn));
    }
}
