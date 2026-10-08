package com.feiji.cloud.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiji.cloud.auth.AuthInterceptor;
import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int MAX_DETAIL_LENGTH = 800;

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String username = currentUsername();
        String detail = describeArgs(joinPoint);
        try {
            Object result = joinPoint.proceed();
            auditService.record(username, audited.value(), detail, true);
            return result;
        } catch (Throwable t) {
            auditService.record(username, audited.value(), detail + " | 失败: " + t.getMessage(), false);
            throw t;
        }
    }

    private String currentUsername() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object user = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_LOGIN_USER);
            if (user instanceof LoginUser loginUser) {
                return loginUser.username();
            }
        }
        return "anonymous";
    }

    private String describeArgs(ProceedingJoinPoint joinPoint) {
        try {
            List<Object> safeArgs = Arrays.stream(joinPoint.getArgs())
                    .map(arg -> (arg instanceof byte[] || arg instanceof MultipartFile)
                            ? arg.getClass().getSimpleName() : arg)
                    .toList();
            String json = objectMapper.writeValueAsString(safeArgs);
            return json.length() > MAX_DETAIL_LENGTH ? json.substring(0, MAX_DETAIL_LENGTH) : json;
        } catch (Exception e) {
            return Arrays.toString(joinPoint.getArgs());
        }
    }
}
