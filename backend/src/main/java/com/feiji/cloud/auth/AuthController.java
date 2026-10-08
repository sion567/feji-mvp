package com.feiji.cloud.auth;

import com.feiji.cloud.audit.AuditService;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    public AuthController(UserService userService, JwtUtil jwtUtil, AuditService auditService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    @PostMapping("/login")
    public HttpResult<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        LoginUser user;
        try {
            user = userService.authenticate(request.username(), request.password());
        } catch (BusinessException e) {
            auditService.record(request.username(), "用户登录", "失败: " + e.getMessage(), false);
            throw e;
        }
        auditService.record(user.username(), "用户登录", "成功", true);
        String token = jwtUtil.issue(user.username(), user.workspaceId(), user.roleEnum());
        return HttpResult.ok(Map.of(
                "token", token,
                "username", user.username(),
                "workspaceId", user.workspaceId(),
                "role", user.role()));
    }

    @GetMapping("/me")
    public HttpResult<LoginUser> me(@RequestAttribute(AuthInterceptor.ATTR_LOGIN_USER) LoginUser user) {
        return HttpResult.ok(user);
    }
}
