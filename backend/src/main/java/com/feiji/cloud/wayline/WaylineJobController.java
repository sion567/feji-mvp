package com.feiji.cloud.wayline;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/wayline-jobs")
public class WaylineJobController {

    private final WaylineJobService jobService;

    public WaylineJobController(WaylineJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @RequireRole(Role.OPERATOR)
    @Audited("创建航线任务")
    public HttpResult<Map<String, Object>> create(@Valid @RequestBody WaylineJobService.CreateJobRequest request,
                                                  @CurrentUser LoginUser user) {
        WaylineJobEntity job = jobService.create(request, user);
        Map<String, Object> view = jobService.toView(job);
        String warning = jobService.complianceWarningOf(job);
        view.put("complianceWarning", warning != null);
        view.put("complianceMessage", warning);
        return HttpResult.ok(view);
    }

    public record ReportRequest(
            @NotBlank(message = "报备状态不能为空") String reportStatus,
            String reportNo) {
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/report")
    @RequireRole(Role.OPERATOR)
    @Audited("任务报备登记")
    public HttpResult<Map<String, Object>> updateReport(@PathVariable Long id,
                                                        @Valid @RequestBody ReportRequest request) {
        jobService.updateReport(id, request.reportStatus(), request.reportNo());
        return HttpResult.ok(jobService.toView(jobService.requireById(id)));
    }

    @GetMapping
    public HttpResult<List<Map<String, Object>>> list(@CurrentUser LoginUser user) {
        return HttpResult.ok(jobService.list(user.workspaceId()).stream().map(jobService::toView).toList());
    }

    @PostMapping("/{id}/cancel")
    @RequireRole(Role.OPERATOR)
    @Audited("取消航线任务")
    public HttpResult<Void> cancel(@PathVariable Long id, @CurrentUser LoginUser user) {
        jobService.cancel(id, user);
        return HttpResult.ok();
    }
}
