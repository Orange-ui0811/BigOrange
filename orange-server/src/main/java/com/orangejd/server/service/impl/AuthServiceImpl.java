package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.common.exception.LoginFailedException;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.common.utils.JwtUtil;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.dto.PasswordChangeDTO;
import com.orangejd.pojo.dto.RegisterDTO;
import com.orangejd.pojo.entity.Account;
import com.orangejd.pojo.entity.UserProfile;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public LoginVO adminLogin(LoginDTO loginDTO) {
        Account account = accountMapper.selectByUsername(loginDTO.getUsername());
        if (account == null) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), account.getPassword())) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!"ADMIN".equals(account.getRole())) {
            throw new LoginFailedException("非管理员账号");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new LoginFailedException("账号已被禁用");
        }
        return buildLoginVO(account, jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl());
    }

    @Override
    public LoginVO appLogin(LoginDTO loginDTO) {
        Account account = accountMapper.selectByUsername(loginDTO.getUsername());
        if (account == null) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), account.getPassword())) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new LoginFailedException("账号已被禁用");
        }
        return buildLoginVO(account, jwtProperties.getAppSecretKey(), jwtProperties.getAppTtl());
    }

    @Override
    @Transactional
    public LoginVO register(RegisterDTO registerDTO) {
        // 检查用户名重复
        Account existing = accountMapper.selectByUsername(registerDTO.getUsername());
        if (existing != null) {
            throw new BaseException("用户名已存在");
        }

        // 创建账号
        Account account = new Account();
        account.setUsername(registerDTO.getUsername());
        account.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        account.setStatus("ACTIVE");
        account.setRole("USER");
        accountMapper.insert(account);

        // 创建用户资料
        UserProfile profile = new UserProfile();
        profile.setAccountId(account.getId());
        profile.setNickname(registerDTO.getNickname() != null ? registerDTO.getNickname() : registerDTO.getUsername());
        profile.setHomepageVisible(1);
        userProfileMapper.insert(profile);

        // 注册后直接返回登录态
        return buildLoginVO(account, jwtProperties.getAppSecretKey(), jwtProperties.getAppTtl());
    }

    @Override
    public void changePassword(PasswordChangeDTO dto) {
        // 从 BaseContext 获取当前登录用户 ID（由 JWT 拦截器注入）
        Long currentId = com.orangejd.common.context.BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BaseException("未登录");
        }

        Account account = accountMapper.selectById(currentId);
        if (account == null) {
            throw new BaseException("账号不存在");
        }

        // 校验旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), account.getPassword())) {
            throw new BaseException("旧密码错误");
        }

        // 更新为新密码
        accountMapper.updatePassword(currentId, passwordEncoder.encode(dto.getNewPassword()));
    }

    private LoginVO buildLoginVO(Account account, String secretKey, long ttl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", account.getId());
        claims.put("username", account.getUsername());

        LoginVO loginVO = new LoginVO();
        loginVO.setId(account.getId());
        loginVO.setUsername(account.getUsername());
        loginVO.setToken(JwtUtil.createJwt(secretKey, ttl, claims));
        return loginVO;
    }
}

