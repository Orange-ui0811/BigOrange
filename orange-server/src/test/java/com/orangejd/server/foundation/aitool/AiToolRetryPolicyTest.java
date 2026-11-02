package com.orangejd.server.foundation.aitool;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolRetryPolicyTest {

    @Test
    void retriesTransientProviderAndIncompleteResultFailures() {
        assertTrue(AiToolRetryPolicy.isRetryable(new AiToolProviderException(
                429, "RATE_LIMITED", 1000, "rate limited", null)));
        assertTrue(AiToolRetryPolicy.isRetryable(
                new IllegalStateException("AI 返回的趋势分析结果不完整")));
    }

    @Test
    void doesNotRetryPermanentRequestsOrResultPersistenceFailures() {
        assertFalse(AiToolRetryPolicy.isRetryable(new AiToolProviderException(
                400, "BAD_REQUEST", 0, "bad request", null)));
        assertFalse(AiToolRetryPolicy.isRetryable(
                new BaseException("分析结果保存失败，本次额度已自动退回，请重新发起分析")));
    }
}
