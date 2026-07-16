package com.orangejd.server.controller.app;

import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.result.Result;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.dto.RegisterDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import com.orangejd.server.service.MailService;
import com.orangejd.server.service.TokenBlacklistService;
import com.orangejd.server.service.TurnstileService;
import com.orangejd.server.service.RateLimitService;
import com.orangejd.server.service.WebSocketTicketService;
import com.orangejd.common.context.BaseContext;
import com.orangejd.server.util.ClientIpResolver;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

@RestController
@RequestMapping("/app/auth")
public class AppAuthController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired private AuthService authService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;
    @Autowired private MailService mailService;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;
    @Autowired private TurnstileService turnstileService;
    @Autowired private RateLimitService rateLimitService;
    @Autowired private WebSocketTicketService webSocketTicketService;

    @PostMapping("/ws-ticket")
    public Result<Map<String, String>> webSocketTicket() {
        return Result.success(Map.of("ticket", webSocketTicketService.issue(BaseContext.getCurrentId())));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        if (!rateLimitService.allow("app-login:ip:" + ip, 10, 300)
                || !rateLimitService.allow("app-login:user:" + loginDTO.getUsername(), 10, 300)) {
            return Result.error("登录尝试过多，请稍后重试");
        }
        return Result.success(authService.appLogin(loginDTO));
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = normalizeEmail(body.get("email"));
        if (!validEmail(email)) return Result.error("邮箱格式不正确");
        if (!rateLimitService.allow("mail-code:ip:" + ClientIpResolver.resolve(request), 10, 3600))
            return Result.error("验证码请求过多，请稍后重试");
        // 频率限制：同一邮箱 60 秒内只能发一次
        if (redisTemplate != null && redisTemplate.hasKey("mail:limit:" + email))
            return Result.error("验证码已发送，请60秒后再试");
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        mailService.sendCode(email, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("mail:code:" + email, code, 5, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set("mail:limit:" + email, "1", 60, TimeUnit.SECONDS);
        }
        return Result.success();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO registerDTO, HttpServletRequest request) {
        if (!rateLimitService.allow("register:ip:" + ClientIpResolver.resolve(request), 5, 3600))
            return Result.error("注册请求过多，请稍后重试");
        registerDTO.setEmail(normalizeEmail(registerDTO.getEmail()));
        if (!turnstileService.verify(registerDTO.getTurnstileToken())) {
            return Result.error("人机验证失败，请刷新页面重试");
        }
        return Result.success(authService.register(registerDTO));
    }

    @PostMapping("/send-reset-code")
    public Result<?> sendResetCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = normalizeEmail(body.get("email"));
        if (!validEmail(email)) return Result.error("邮箱格式不正确");
        if (!rateLimitService.allow("reset-code:ip:" + ClientIpResolver.resolve(request), 10, 3600))
            return Result.error("验证码请求过多，请稍后重试");
        authService.sendResetCode(email);
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<?> resetPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = normalizeEmail(body.get("email"));
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        if (!validEmail(email) || code == null || !code.matches("^\\d{6}$")
                || newPassword == null || newPassword.length() > 128) return Result.error("参数格式不正确");
        String ip = ClientIpResolver.resolve(request);
        if (!rateLimitService.allow("reset-attempt:ip:" + ip, 10, 900)
                || !rateLimitService.allow("reset-attempt:email:" + email, 5, 900))
            return Result.error("重置尝试过多，请15分钟后再试");
        authService.resetPassword(email, code, newPassword);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<?> changePassword(@Valid @RequestBody PasswordChangeDTO passwordChangeDTO) {
        authService.changePassword(passwordChangeDTO);
        return Result.success();
    }

    @GetMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getAppTokenName());
        if (token != null) {
            long ttl = getRemainingTtl(token, jwtProperties.getAppSecretKey());
            if (ttl > 0) blacklistService.blacklist(token, ttl);
        }
        return Result.success();
    }

    private long getRemainingTtl(String token, String secretKey) {
        try {
            Claims claims = JwtUtil.parseJwt(secretKey, token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception e) { return 0; }
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean validEmail(String email) {
        return email != null && email.length() <= 128
                && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
