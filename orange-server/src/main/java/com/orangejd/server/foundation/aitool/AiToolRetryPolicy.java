package com.orangejd.server.foundation.aitool;

import java.net.SocketTimeoutException;

public final class AiToolRetryPolicy {
    private AiToolRetryPolicy() {}

    public static boolean isRetryable(Throwable throwable) {
        AiToolProviderException provider = AiToolProviderException.from(throwable);
        if (provider != null) {
            int status = provider.httpStatus();
            return status == 401 || status == 403 || status == 408 || status == 409
                    || status == 425 || status == 429 || status >= 500;
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) return true;
            String message = current.getMessage();
            if (message == null) continue;
            String normalized = message.replace(" ", "").toLowerCase();
            if (normalized.contains("已退回") || normalized.contains("结果保存失败")) return false;
            if (normalized.contains("系统将自动重试")
                    || normalized.contains("ai分析失败")
                    || normalized.contains("ai调用失败")
                    || normalized.contains("ai匹配失败")
                    || normalized.contains("ai生成失败")
                    || normalized.contains("指南生成失败")
                    || normalized.contains("aireturned")
                    || normalized.contains("ai返回")
                    || normalized.contains("候选不足")
                    || normalized.contains("ai服务繁忙")
                    || normalized.contains("请求过于频繁")) return true;
        }
        return false;
    }
}
