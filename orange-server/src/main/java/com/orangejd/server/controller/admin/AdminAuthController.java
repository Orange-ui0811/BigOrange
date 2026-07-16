package com.orangejd.server.controller.admin;

import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.result.Result;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import com.orangejd.server.service.TokenBlacklistService;
import com.orangejd.server.service.RateLimitService;
import com.orangejd.server.util.ClientIpResolver;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;
    @Autowired private RateLimitService rateLimitService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        if (!rateLimitService.allow("admin-login:ip:" + ClientIpResolver.resolve(request), 5, 300)) {
            return Result.error("登录尝试过多，请稍后重试");
        }
        return Result.success(authService.adminLogin(loginDTO));
    }

    @PutMapping("/password")
    public Result<?> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        authService.changePassword(dto);
        return Result.success();
    }

    @GetMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token != null) {
            long ttl = getRemainingTtl(token, jwtProperties.getAdminSecretKey());
            if (ttl > 0) blacklistService.blacklist(token, ttl);
        }
        return Result.success();
    }

    private long getRemainingTtl(String token, String secretKey) {
        try {
            Claims claims = JwtUtil.parseJwt(secretKey, token);
            return Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 0);
        } catch (Exception e) { return 0; }
    }
}
