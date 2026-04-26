package com.ecommerce.config;

import com.ecommerce.common.Result;
import com.ecommerce.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 拦截需要登录的接口，验证 Token 有效性
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            log.warn("JwtInterceptor.preHandle 请求缺少Token: {}", request.getRequestURI());
            writeError(response, 401, "请先登录");
            return false;
        }

        try {
            if (!jwtUtil.validateToken(token)) {
                writeError(response, 401, "登录已过期，请重新登录");
                return false;
            }
            // 将用户ID放入请求属性，供后续使用
            String userId = jwtUtil.getUserIdFromToken(token);
            request.setAttribute("currentUserId", userId);
            return true;
        } catch (Exception e) {
            log.warn("JwtInterceptor.preHandle Token验证失败: {}", e.getMessage());
            writeError(response, 401, "登录已过期，请重新登录");
            return false;
        }
    }

    /**
     * 从请求中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 写入错误响应
     */
    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
