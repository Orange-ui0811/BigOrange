package com.orangejd.pojo.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$", message = "密码至少6位且必须包含字母和数字")
    private String password;
    private String nickname;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @NotBlank(message = "验证码不能为空")
    private String code;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
