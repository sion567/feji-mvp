package com.feiji.cloud.wayline;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 选点拍照航线：/plan 排序预览（前端画线确认），/compose 生成 KMZ 入航线库。
 * 请求 DTO 与校验注解见 {@link RouteComposeService}。
 */
@Validated
@RestController
@RequestMapping("/api/v1/route")
@RequireRole(Role.OPERATOR)
public class RouteController {

    private final RouteComposeService composeService;

    public RouteController(RouteComposeService composeService) {
        this.composeService = composeService;
    }

    /** 排序预览：不落库，返回优化后的点序与总距离 */
    @PostMapping("/plan")
    public HttpResult<RouteComposeService.PlanResponse> plan(
            @Valid @RequestBody RouteComposeService.PlanRequest request) {
        return HttpResult.ok(composeService.plan(request));
    }

    /** 生成 KMZ 入航线库，返回航线文件实体（后续用现有任务下发接口派发） */
    @PostMapping("/compose")
    @Audited("选点生成航线")
    public HttpResult<WaylineFileEntity> compose(
            @Valid @RequestBody RouteComposeService.ComposeRequest request,
            @CurrentUser LoginUser user) {
        return HttpResult.ok(composeService.compose(request, user));
    }
}
