package com.spark.app.web.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 认证拦截器 - 未登录用户访问受保护资源时返回 401。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER_KEY = "currentUser";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        Object user = request.getSession().getAttribute(SESSION_USER_KEY);
        if (user != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"未登录\"}");
        return false;
    }
}
