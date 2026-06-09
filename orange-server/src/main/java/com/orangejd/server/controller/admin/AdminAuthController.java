package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.adminLogin(loginDTO));
    }

    @PutMapping("/password")
    public Result<?> changePassword(@RequestBody PasswordChangeDTO dto) {
        authService.changePassword(dto);
        return Result.success();
    }
}

