package com.orangejd.pojo.entity;

public class Account extends BaseEntity {
    private String username;
    private String password;
    private String phone;
    private String email;
    private Long quota;
    private String status;
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getQuota() { return quota; }
    public void setQuota(Long quota) { this.quota = quota; }
}
