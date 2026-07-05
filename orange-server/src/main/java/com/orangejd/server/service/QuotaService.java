package com.orangejd.server.service;

public interface QuotaService {

    /** 消费配额（原子扣减 + FOR UPDATE + 幂等） */
    int consume(Long accountId, int amount, String description, String requestId);

    /** 充值配额（原子增加 + 写日志） */
    int recharge(Long accountId, int amount, String source, String description);

    /** 退款配额 */
    int refund(Long accountId, int amount, String requestId, String description);

    /** 充值码兑换 */
    int redeemCode(Long accountId, String code);

    /** 管理员手动调整 */
    int adminAdjust(Long accountId, long newQuota, Long adminId);

    /** 查询余额 */
    long getBalance(Long accountId);
}
