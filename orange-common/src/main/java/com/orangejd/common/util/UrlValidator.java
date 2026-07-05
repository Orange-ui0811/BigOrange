package com.orangejd.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URL;

/**
 * SSRF 防护：校验 URL 是否安全（禁止访问内网地址）
 */
public class UrlValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlValidator.class);

    public static boolean isSafe(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return false;
        try {
            URL url = new URL(urlStr);
            String host = url.getHost();
            if (host == null || host.isEmpty()) return false;

            // 白名单直通
            if (host.endsWith(".aliyuncs.com")
                    || host.endsWith(".github.com")
                    || host.endsWith(".githubusercontent.com")
                    || host.endsWith(".gitee.com")) {
                return true;
            }

            InetAddress addr = InetAddress.getByName(host);
            if (addr.isSiteLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()) {
                log.warn("[SSRF] 拦截内网地址: {}", urlStr);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[SSRF] URL 解析失败: {}", urlStr);
            return false;
        }
    }
}
