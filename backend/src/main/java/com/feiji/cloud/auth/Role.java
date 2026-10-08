package com.feiji.cloud.auth;

/**
 * 平台角色（MVP 三级 RBAC），level 用于层级比较：ADMIN > OPERATOR > OBSERVER。
 */
public enum Role {

    OBSERVER(1),
    OPERATOR(2),
    ADMIN(3);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public boolean atLeast(Role required) {
        return this.level >= required.level;
    }

    public static Role of(String name) {
        if (name == null) {
            return OBSERVER;
        }
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            return OBSERVER;
        }
    }
}
