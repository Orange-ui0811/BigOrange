package com.orangejd.server.foundation.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiConfigCrypto {
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public AiConfigCrypto(@Value("${orange.ai-config.encryption-key:}") String encryptionKey) {
        this.key = StringUtils.hasText(encryptionKey) ? sha256(encryptionKey) : null;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) return "";
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception ex) {
            throw new IllegalStateException("AI API Key 加密失败", ex);
        }
    }

    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) return "";
        requireKey();
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalArgumentException("不支持的 AI 配置密文格式");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (packed.length <= IV_LENGTH) throw new IllegalArgumentException("AI 配置密文无效");
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[packed.length - IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, IV_LENGTH);
            System.arraycopy(packed, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("AI API Key 解密失败，请重新保存该配置", ex);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("未配置 AI_CONFIG_ENCRYPTION_KEY，不能保存或读取动态 AI Key");
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化 AI 配置加密", ex);
        }
    }
}
