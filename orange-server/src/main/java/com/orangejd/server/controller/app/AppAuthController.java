package com.orangejd.server.controller.app;

import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.result.Result;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.dto.RegisterDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import com.orangejd.server.service.SmsService;
import com.orangejd.server.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/app/auth")
public class AppAuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private TokenBlacklistService blacklistService;
    @Autowired private SmsService smsService;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.appLogin(loginDTO));
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.length() != 11) return Result.error("手机号格式不正确");
        // 频率限制：同一手机号 60 秒内只能发一次
        if (redisTemplate != null && redisTemplate.hasKey("sms:limit:" + phone))
            return Result.error("验证码已发送，请60秒后再试");
        String code = String.format("%06d", new Random().nextInt(999999));
        smsService.sendCode(phone, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("sms:code:" + phone, code, 5, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set("sms:limit:" + phone, "1", 60, TimeUnit.SECONDS);
        }
        return Result.success();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get("sms:code:" + registerDTO.getPhone());
            if (cached == null || !cached.equals(registerDTO.getCode()))
                return Result.error("验证码错误或已过期");
            redisTemplate.delete("sms:code:" + registerDTO.getPhone());
        }
        return Result.success(authService.register(registerDTO));
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
