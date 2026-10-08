package com.feiji.cloud.control;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/drc")
@RequireRole(Role.OPERATOR)
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DrcController {

    private final DrcService drcService;

    public DrcController(DrcService drcService) {
        this.drcService = drcService;
    }

    public record TargetRequest(
            Double lat,
            Double lng,
            @NotNull(message = "高度不能为空") @Min(5) @Max(1500) Double height,
            @Min(5) @Max(1500) Double safetyHeight) {
    }

    public record ControlRequest(
            @NotNull @Min(-1024) @Max(1024) Integer x,
            @NotNull @Min(-1024) @Max(1024) Integer y,
            @NotNull @Min(-1024) @Max(1024) Integer z) {
    }

    @PostMapping("/{sn}/enter")
    @Audited("进入DRC指令飞行")
    public HttpResult<Void> enter(@PathVariable @NotBlank String sn) {
        drcService.enter(sn);
        return HttpResult.ok();
    }

    @PostMapping("/{sn}/exit")
    @Audited("退出DRC指令飞行")
    public HttpResult<Void> exit(@PathVariable @NotBlank String sn) {
        drcService.exit(sn);
        return HttpResult.ok();
    }

    @PostMapping("/{sn}/flyto")
    @Audited("指点飞行")
    public HttpResult<Void> flyTo(@PathVariable @NotBlank String sn, @Valid @RequestBody TargetRequest request) {
        drcService.flyTo(sn, request.lat(), request.lng(), request.height(),
                request.safetyHeight() == null ? 80 : request.safetyHeight());
        return HttpResult.ok();
    }

    /** 一键起飞：lat/lng 缺省时取设备当前定位 */
    @PostMapping("/{sn}/takeoff")
    @Audited("一键起飞")
    public HttpResult<Void> takeOff(@PathVariable @NotBlank String sn, @Valid @RequestBody TargetRequest request) {
        drcService.takeOffToPoint(sn, request.lat(), request.lng(), request.height(),
                request.safetyHeight() == null ? 80 : request.safetyHeight());
        return HttpResult.ok();
    }

    @PostMapping("/{sn}/control")
    public HttpResult<Void> control(@PathVariable @NotBlank String sn, @Valid @RequestBody ControlRequest request) {
        drcService.droneControl(sn, request.x(), request.y(), request.z());
        return HttpResult.ok();
    }

    @GetMapping("/state")
    public HttpResult<List<String>> state() {
        return HttpResult.ok(drcService.activeList());
    }
}
