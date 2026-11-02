package com.orangejd.server.service;

public interface QuotaService {

    /** Versioned, idempotent AI service debit. */
    QuotaCharge consume(Long accountId, String serviceKey, String description,
                        QuotaRequestIdentity identity);

    /** Compatibility entry point for non-priced internal callers. */
    long consume(Long accountId, long amount, String description, String requestId);

    /** Direct credit retained for payment callbacks; payment state owns its idempotency. */
    long recharge(Long accountId, long amount, String source, String description);

    /** Refunds exactly the original debit and never reads the current price. */
    long refund(Long accountId, String requestId, String description);

    /** Compatibility overload that additionally verifies the expected original amount. */
    long refund(Long accountId, long expectedAmount, String requestId, String description);

    long redeemCode(Long accountId, String code);

    long adminAdjust(Long accountId, long newQuota, Long adminId);

    long getBalance(Long accountId);

    long grantNewUserBonus(Long accountId);

    long grantReward(Long accountId, String serviceKey, String businessReference,
                     String description);
}
