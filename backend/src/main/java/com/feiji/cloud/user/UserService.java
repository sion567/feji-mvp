package com.feiji.cloud.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.ResultCode;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.config.AppProperties;
import com.feiji.cloud.workspace.WorkspaceEntity;
import com.feiji.cloud.workspace.WorkspaceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@org.springframework.core.annotation.Order(100)
public class UserService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AppProperties appProperties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, WorkspaceMapper workspaceMapper, AppProperties appProperties) {
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.appProperties = appProperties;
    }

    /** 首次启动种子：默认工作空间 + 内置管理员（口令取 app.security 配置） */
    @Override
    public void run(ApplicationArguments args) {
        try {
            if (workspaceMapper.selectCount(null) == 0) {
                WorkspaceEntity ws = new WorkspaceEntity();
                ws.setName("默认工作空间");
                workspaceMapper.insert(ws);
                log.info("已创建默认工作空间 id={}", ws.getId());
            }
            if (userMapper.selectCount(null) == 0) {
                AppProperties.Security security = appProperties.security();
                UserEntity admin = new UserEntity();
                admin.setUsername(security.adminUsername());
                admin.setPasswordHash(encoder.encode(security.adminPassword()));
                admin.setDisplayName("系统管理员");
                admin.setRole(Role.ADMIN.name());
                admin.setWorkspaceId(1L);
                admin.setCreatedAt(LocalDateTime.now());
                admin.setUpdatedAt(LocalDateTime.now());
                userMapper.insert(admin);
                log.info("已创建内置管理员: {}", admin.getUsername());
            }
        } catch (Exception e) {
            log.error("用户种子数据初始化失败（数据库不可用?）: {}", e.getMessage());
        }
    }

    public LoginUser authenticate(String username, String rawPassword) {
        UserEntity user = userMapper.selectOne(new QueryWrapper<UserEntity>().eq("username", username));
        if (user == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return new LoginUser(user.getUsername(), user.getWorkspaceId(), user.getRole());
    }

    public List<UserEntity> list() {
        return userMapper.selectList(new QueryWrapper<UserEntity>().orderByAsc("id"));
    }

    public UserEntity create(String username, String rawPassword, String displayName, String role, Long workspaceId) {
        if (userMapper.selectCount(new QueryWrapper<UserEntity>().eq("username", username)) > 0) {
            throw new BusinessException("用户名已存在: " + username);
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setRole(Role.of(role).name());
        user.setWorkspaceId(workspaceId == null ? 1L : workspaceId);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public void delete(Long id, String currentUsername) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            return;
        }
        if (user.getUsername().equals(currentUsername)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        userMapper.deleteById(id);
    }

    public void updateRole(Long id, String role) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: id=" + id);
        }
        user.setRole(Role.of(role).name());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
