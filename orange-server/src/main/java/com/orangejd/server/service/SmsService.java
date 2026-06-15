package com.orangejd.server.service;

/**
 * 短信发送服务 —— dev 控制台输出，prod 对接阿里云
 */
public interface SmsService {
    void sendCode(String phone, String code);
}
