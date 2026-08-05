package com.orangejd.server.interceptor;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.server.service.TokenBlacklistService;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.security.PasswordSessionVersion;
import com.orangejd.pojo.entity.Account;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@lombok.RequiredArgsConstructor
public class JwtTokenAppInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final TokenBlacklistService blacklistService;
    private final AccountMapper accountMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader(jwtProperties.getAppTokenName());
        if (token == null || token.isEmpty()) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        if (blacklistService.isBlacklisted(token)) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return false; }
        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getAppSecretKey(), token);
            if ((claims.get("tokenType") != null && !"APP".equals(claims.get("tokenType")))
                    || (claims.get("iss") != null
                        && !java.util.Set.of("orangejd", "orangevc").contains(String.valueOf(claims.get("iss"))))
                    || (claims.getAudience() != null && !claims.getAudience().contains("APP"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            Long accountId = Long.valueOf(claims.get("id").toString());
            Account account = accountMapper.selectById(accountId);
            if (account == null || !"ACTIVE".equals(account.getStatus())
                    || !PasswordSessionVersion.matches(claims, account)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            BaseContext.setCurrentId(accountId);
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
