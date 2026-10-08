package com.feiji.cloud.auth;

/**
 * 当前登录用户（JWT 解析结果）。
 */
public record LoginUser(String username, Long workspaceId, String role) {

    public Role roleEnum() {
        return Role.of(role);
    }
}
