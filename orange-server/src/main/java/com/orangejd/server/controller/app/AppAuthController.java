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
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/app/auth")
public class AppAuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;
    @Autowired private MailService mailService;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;
    @Autowired private TurnstileService turnstileService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.appLogin(loginDTO));
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || !email.contains("@")) return Result.error("邮箱格式不正确");
        // 频率限制：同一邮箱 60 秒内只能发一次
        if (redisTemplate != null && redisTemplate.hasKey("mail:limit:" + email))
            return Result.error("验证码已发送，请60秒后再试");
        String code = String.format("%06d", new Random().nextInt(999999));
        mailService.sendCode(email, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("mail:code:" + email, code, 5, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set("mail:limit:" + email, "1", 60, TimeUnit.SECONDS);
        }
        return Result.success();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        if (!turnstileService.verify(registerDTO.getTurnstileToken())) {
            return Result.error("人机验证失败，请刷新页面重试");
        }
        return Result.success(authService.register(registerDTO));
    }

    @PostMapping("/send-reset-code")
    public Result<?> sendResetCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || !email.contains("@")) return Result.error("邮箱格式不正确");
        authService.sendResetCode(email);
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        if (email == null || code == null || newPassword == null) return Result.error("参数不完整");
        authService.resetPassword(email, code, newPassword);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<?> changePassword(@RequestBody PasswordChangeDTO passwordChangeDTO) {
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
}
