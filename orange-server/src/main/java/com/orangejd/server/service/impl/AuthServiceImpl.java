package com.orangejd.server.service.impl;

import com.orangejd.common.exception.LoginFailedException;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public LoginVO adminLogin(LoginDTO loginDTO) {
        if (!"admin".equals(loginDTO.getUsername()) || !"123456".equals(loginDTO.getPassword())) {
            throw new LoginFailedException("Username or password is invalid");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1L);
        claims.put("username", loginDTO.getUsername());

        LoginVO loginVO = new LoginVO();
        loginVO.setId(1L);
        loginVO.setUsername(loginDTO.getUsername());
        loginVO.setToken(JwtUtil.createJwt(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims));
        return loginVO;
    }
}

