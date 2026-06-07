package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.dto.RegisterDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/auth")
public class AppAuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.appLogin(loginDTO));
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterDTO registerDTO) {
        return Result.success(authService.register(registerDTO));
    }

    @PutMapping("/password")
    public Result<?> changePassword(@RequestBody PasswordChangeDTO passwordChangeDTO) {
        authService.changePassword(passwordChangeDTO);
        return Result.success();
    }

    @GetMapping("/logout")
    public Result<?> logout() {
        // JWT 无状态，客户端删除 token 即可
        // 后续可接入 Redis 黑名单实现主动失效
        return Result.success();
    }
}
