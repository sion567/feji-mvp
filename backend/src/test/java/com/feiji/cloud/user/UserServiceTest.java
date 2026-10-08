package com.feiji.cloud.user;

import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.workspace.WorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户体系测试（H2 内存库）：种子管理员、登录、创建用户、角色层级。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:usertest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Test
    void shouldSeedAdminAndAuthenticate() {
        LoginUser admin = userService.authenticate("admin", "admin123");
        assertEquals(Role.ADMIN, admin.roleEnum());
        assertNotNull(admin.workspaceId());
        assertTrue(workspaceMapper.selectCount(null) > 0);
    }

    @Test
    void shouldRejectWrongPassword() {
        assertThrows(BusinessException.class, () -> userService.authenticate("admin", "wrong-password"));
    }

    @Test
    void shouldCreateUserAndLogin() {
        userService.create("operator1", "operator-pass", "操作员一号", Role.OPERATOR.name(), 1L);

        LoginUser user = userService.authenticate("operator1", "operator-pass");
        assertEquals(Role.OPERATOR, user.roleEnum());
        assertEquals(1L, user.workspaceId());

        // OPERATOR 不满足 ADMIN 要求
        assertTrue(!user.roleEnum().atLeast(Role.ADMIN));
        assertTrue(user.roleEnum().atLeast(Role.OPERATOR));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        userService.create("dup-user", "pass1", null, Role.OBSERVER.name(), 1L);
        assertThrows(BusinessException.class,
                () -> userService.create("dup-user", "pass2", null, Role.OBSERVER.name(), 1L));
    }
}
