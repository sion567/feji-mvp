package com.feiji.cloud.workspace;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceMapper workspaceMapper;

    public WorkspaceController(WorkspaceMapper workspaceMapper) {
        this.workspaceMapper = workspaceMapper;
    }

    public record CreateRequest(@NotBlank(message = "名称不能为空") String name) {
    }

    @GetMapping
    public HttpResult<List<WorkspaceEntity>> list() {
        return HttpResult.ok(workspaceMapper.selectList(new QueryWrapper<WorkspaceEntity>().orderByAsc("id")));
    }

    @PostMapping
    @RequireRole(Role.ADMIN)
    @Audited("创建工作空间")
    public HttpResult<WorkspaceEntity> create(@Valid @RequestBody CreateRequest request) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(request.name());
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);
        return HttpResult.ok(workspace);
    }
}
