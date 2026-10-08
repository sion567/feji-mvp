package com.feiji.cloud.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 记录审计日志；写库失败仅告警，不影响业务 */
    public void record(String username, String action, String detail, boolean success) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setUsername(username);
            entity.setAction(action);
            entity.setDetail(detail);
            entity.setSuccess(success);
            entity.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    public List<AuditLogEntity> latest(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return auditLogMapper.selectList(new QueryWrapper<AuditLogEntity>()
                .orderByDesc("id")
                .last("LIMIT " + safeLimit));
    }
}
