package com.orangejd.server.interceptor;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.server.service.TokenBlacklistService;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.pojo.entity.Account;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;
    @Autowired private AccountMapper accountMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token == null || token.isEmpty()) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        if (blacklistService.isBlacklisted(token)) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getAdminSecretKey(), token);
            // Legacy tokens are accepted until their short TTL expires; all newly issued tokens carry both claims.
            if ((claims.get("tokenType") != null && !"ADMIN".equals(claims.get("tokenType")))
                    || (claims.get("role") != null && !"ADMIN".equals(claims.get("role")))
                    || (claims.get("iss") != null
                        && !java.util.Set.of("orangejd", "orangevc").contains(String.valueOf(claims.get("iss"))))
                    || (claims.getAudience() != null && !claims.getAudience().contains("ADMIN"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            Long adminId = Long.valueOf(claims.get("id").toString());
            Account account = accountMapper.selectById(adminId);
            if (account == null || !"ACTIVE".equals(account.getStatus()) || !"ADMIN".equals(account.getRole())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            BaseContext.setCurrentId(adminId);
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
