package com.orangejd.server.interceptor;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.server.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token == null || token.isEmpty()) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        if (blacklistService.isBlacklisted(token)) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getAdminSecretKey(), token);
            BaseContext.setCurrentId(Long.valueOf(claims.get("id").toString()));
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.clear();
    }
}

