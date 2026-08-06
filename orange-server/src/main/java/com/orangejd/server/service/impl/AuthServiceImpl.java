package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.exception.LoginFailedException;
import lombok.extern.slf4j.Slf4j;
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
import com.orangejd.server.service.EmailVerificationCodeService;
import com.orangejd.server.service.QuotaService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String CURRENT_LEGAL_VERSION = "2026-07-19";

    private final AccountMapper accountMapper;

    private final UserProfileMapper userProfileMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtProperties jwtProperties;

    private final QuotaService quotaService;
    private final EmailVerificationCodeService verificationCodeService;

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
        return buildLoginVO(account, jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), "ADMIN");
    }

    @Override
    public LoginVO appLogin(LoginDTO loginDTO) {
        String identity = loginDTO.getUsername().trim();
        Account account = accountMapper.selectByUsername(identity);
        if (account == null && identity.contains("@")) {
            account = accountMapper.selectByEmail(identity.toLowerCase(Locale.ROOT));
        }
        if (account == null) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), account.getPassword())) {
            throw new LoginFailedException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new LoginFailedException("账号已被禁用");
        }
        return buildLoginVO(account, jwtProperties.getAppSecretKey(), jwtProperties.getAppTtl(), "APP");
    }

    @Override
    @Transactional
    public LoginVO register(RegisterDTO registerDTO) {
        if (!Boolean.TRUE.equals(registerDTO.getLegalAccepted())
                || !CURRENT_LEGAL_VERSION.equals(registerDTO.getLegalVersion())) {
            throw new BaseException("请阅读并同意当前版本的用户协议和隐私政策");
        }
        // 检查用户名重复
        Account existing = accountMapper.selectByUsername(registerDTO.getUsername());
        if (existing != null) {
            throw new BaseException(MessageConstant.USERNAME_EXISTS);
        }

        // 检查邮箱已注册（一个邮箱只能注册一个账号，侵权追溯用）
        Account emailAccount = accountMapper.selectByEmail(registerDTO.getEmail());
        if (emailAccount != null) {
            throw new BaseException("该邮箱已注册，请直接登录");
        }

        String email = registerDTO.getEmail().trim().toLowerCase(Locale.ROOT);
        verificationCodeService.verifyAndConsumeRegistrationCode(email, registerDTO.getCode());

        // 创建账号
        Account account = new Account();
        account.setUsername(registerDTO.getUsername());
        account.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        account.setStatus("ACTIVE");
        account.setRole("USER");
        account.setEmail(email);
        account.setLegalVersion(registerDTO.getLegalVersion());
        accountMapper.insert(account);

        // 创建用户资料
        UserProfile profile = new UserProfile();
        profile.setAccountId(account.getId());
        profile.setNickname(registerDTO.getNickname() != null ? registerDTO.getNickname() : registerDTO.getUsername());
        profile.setHomepageVisible(1);
        userProfileMapper.insert(profile);
        log.info("用户注册 accountId={} username={}", account.getId(), registerDTO.getUsername());

        // 新用户赠送试用额度
        ((QuotaServiceImpl) quotaService).grantNewUserBonus(account.getId());

        // 注册后直接返回登录态
        return buildLoginVO(account, jwtProperties.getAppSecretKey(), jwtProperties.getAppTtl(), "APP");
    }

    @Override
    public void changePassword(PasswordChangeDTO dto) {
        // 从 BaseContext 获取当前登录用户 ID（由 JWT 拦截器注入）
        Long currentId = com.orangejd.common.context.BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BaseException(MessageConstant.NOT_LOGIN);
        }

        Account account = accountMapper.selectById(currentId);
        if (account == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 校验旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), account.getPassword())) {
            throw new BaseException(MessageConstant.OLD_PASSWORD_ERROR);
        }

        // 更新为新密码
        accountMapper.updatePassword(currentId, passwordEncoder.encode(dto.getNewPassword()));
    }

    @Override
    public void sendResetCode(String email) {
        email = email.trim().toLowerCase(Locale.ROOT);
        // 检查邮箱是否已注册
        Account account = accountMapper.selectByEmail(email);
        if (account == null) {
            log.info("忽略未注册邮箱的密码重置请求");
            return;
        }
        verificationCodeService.sendPasswordResetCode(email);
        log.info("密码重置验证码已发送 accountId={}", account.getId());
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        email = email.trim().toLowerCase(Locale.ROOT);
        if (newPassword == null || !newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).{6,128}$")) {
            throw new BaseException("密码需为6到128位且必须包含字母和数字");
        }
        Account account = accountMapper.selectByEmail(email);
        if (account == null) {
            throw new BaseException("验证码已过期，请重新获取");
        }
        verificationCodeService.verifyAndConsumePasswordResetCode(email, code);
        accountMapper.updatePassword(account.getId(), passwordEncoder.encode(newPassword));
        log.info("密码重置成功 accountId={}", account.getId());
    }

    private LoginVO buildLoginVO(Account account, String secretKey, long ttl, String tokenType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", account.getId());
        claims.put("username", account.getUsername());
        claims.put("role", account.getRole());
        claims.put("passwordVersion",
                account.getPasswordVersion() == null ? 0L : account.getPasswordVersion());
        claims.put("tokenType", tokenType);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iss", "orangevc");
        claims.put("aud", tokenType);

        LoginVO loginVO = new LoginVO();
        loginVO.setId(account.getId());
        loginVO.setUsername(account.getUsername());
        loginVO.setToken(JwtUtil.createJwt(secretKey, ttl, claims));
        return loginVO;
    }
}
