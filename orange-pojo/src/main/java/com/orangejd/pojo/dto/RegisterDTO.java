package com.orangejd.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须为3到64个字符")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$", message = "密码需为6到128位且必须包含字母和数字")
    private String password;
    @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;
    @Size(max = 2048, message = "人机验证参数过长")
    private String turnstileToken;
    @NotNull(message = "请明确选择是否同意用户协议和隐私政策")
    @AssertTrue(message = "请先同意用户协议和隐私政策")
    private Boolean legalAccepted;
    @NotBlank(message = "协议版本不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "协议版本格式不正确")
    private String legalVersion;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTurnstileToken() { return turnstileToken; }
    public void setTurnstileToken(String turnstileToken) { this.turnstileToken = turnstileToken; }
    public Boolean getLegalAccepted() { return legalAccepted; }
    public void setLegalAccepted(Boolean legalAccepted) { this.legalAccepted = legalAccepted; }
    public String getLegalVersion() { return legalVersion; }
    public void setLegalVersion(String legalVersion) { this.legalVersion = legalVersion; }
}
