package com.feiji.cloud.user;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(Role.ADMIN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String displayName,
            @NotBlank(message = "角色不能为空") String role,
            Long workspaceId) {
    }

    public record UpdateRoleRequest(@NotNull(message = "用户 id 不能为空") Long id,
                                    @NotBlank(message = "角色不能为空") String role) {
    }

    @GetMapping
    public HttpResult<List<UserEntity>> list() {
        return HttpResult.ok(userService.list());
    }

    @PostMapping
    @Audited("创建用户")
    public HttpResult<UserEntity> create(@Valid @RequestBody CreateUserRequest request) {
        UserEntity user = userService.create(request.username(), request.password(),
                request.displayName(), request.role(), request.workspaceId());
        user.setPasswordHash(null);
        return HttpResult.ok(user);
    }

    @PatchMapping("/{id}/role")
    @Audited("修改用户角色")
    public HttpResult<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        userService.updateRole(id, request.role());
        return HttpResult.ok();
    }

    @DeleteMapping("/{id}")
    @Audited("删除用户")
    public HttpResult<Void> delete(@PathVariable Long id,
                                   @com.feiji.cloud.auth.CurrentUser com.feiji.cloud.auth.LoginUser loginUser) {
        userService.delete(id, loginUser.username());
        return HttpResult.ok();
    }
}
