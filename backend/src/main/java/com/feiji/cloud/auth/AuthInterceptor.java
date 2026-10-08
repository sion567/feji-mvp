package com.feiji.cloud.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.common.api.ResultCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_LOGIN_USER = "loginUser";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                LoginUser user = jwtUtil.parse(authorization.substring(7));

                RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
                if (required == null) {
                    required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
                }
                if (required != null && !user.roleEnum().atLeast(required.value())) {
                    return reject(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN, "无权限执行该操作");
                }

                request.setAttribute(ATTR_LOGIN_USER, user);
                return true;
            } catch (JwtException | IllegalArgumentException e) {
                // token 无效或过期，统一按未认证处理
            }
        }
        return reject(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getMessage());
    }

    private boolean reject(HttpServletResponse response, int status, ResultCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(HttpResult.error(code.getCode(), message)));
        return false;
    }
}
