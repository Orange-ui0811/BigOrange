package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Account;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.pojo.entity.RedemptionCode;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.QuotaLogMapper;
import com.orangejd.server.mapper.RedemptionCodeMapper;
import com.orangejd.server.service.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaServiceImpl implements QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaServiceImpl.class);
    private static final int NEW_USER_BONUS = 100;

    @Autowired private AccountMapper accountMapper;
    @Autowired private QuotaLogMapper quotaLogMapper;
    @Autowired private RedemptionCodeMapper redemptionCodeMapper;

    // ==================== 消费 ====================

    @Override
    @Transactional
    public int consume(Long accountId, int amount, String description, String requestId) {
        // ① 幂等检查
        if (requestId != null && quotaLogMapper.existsByRequestId(requestId) > 0) {
            Account acc = accountMapper.selectById(accountId);
            return acc.getQuota().intValue();
        }

        // ② FOR UPDATE 锁行
        Account acc = accountMapper.selectByIdForUpdate(accountId);
        if (acc == null) throw new BaseException("账户不存在");

        // ③ 原子扣减
        int rows = accountMapper.decreaseQuota(accountId, amount);
        if (rows == 0) {
            throw new BaseException("余额不足，当前 " + acc.getQuota() + " segments，需 " + amount + " segments");
        }

        // ④ 写日志
        long balance = acc.getQuota() - amount;
        writeLog(accountId, "CONSUME", -amount, balance, null, description, requestId);
        log.info("配额消费: accountId={}, amount={}, balance={}, desc={}", accountId, amount, balance, description);
        return (int) balance;
    }

    // ==================== 充值 ====================

    @Override
    @Transactional
    public int recharge(Long accountId, int amount, String source, String description) {
        Account acc = accountMapper.selectByIdForUpdate(accountId);
        if (acc == null) throw new BaseException("账户不存在");

        accountMapper.increaseQuota(accountId, amount);
        long balance = acc.getQuota() + amount;

        writeLog(accountId, "RECHARGE", amount, balance, source, description, null);
        log.info("配额充值: accountId={}, amount={}, source={}, balance={}", accountId, amount, source, balance);
        return (int) balance;
    }

    // ==================== 退款 ====================

    @Override
    @Transactional
    public int refund(Long accountId, int amount, String requestId, String description) {
        Account acc = accountMapper.selectByIdForUpdate(accountId);
        if (acc == null) return 0;

        accountMapper.increaseQuota(accountId, amount);
        long balance = acc.getQuota() + amount;

        writeLog(accountId, "REFUND", amount, balance, null, description, requestId);
        log.info("配额退款: accountId={}, amount={}, balance={}", accountId, amount, balance);
        return (int) balance;
    }

    // ==================== 充值码兑换 ====================

    @Override
    @Transactional
    public int redeemCode(Long accountId, String code) {
        // ① FOR UPDATE 锁码行
        RedemptionCode rc = redemptionCodeMapper.selectByCodeForUpdate(code);
        if (rc == null) throw new BaseException("充值码不存在");
        if (rc.getStatus() == 3) throw new BaseException("此充值码已被使用");
        if (rc.getStatus() == 2) throw new BaseException("此充值码已禁用");

        // ② 标记已使用
        redemptionCodeMapper.updateUsed(code, accountId);

        // ③ 加配额
        Account acc = accountMapper.selectByIdForUpdate(accountId);
        accountMapper.increaseQuota(accountId, rc.getQuota());
        long balance = acc.getQuota() + rc.getQuota();

        writeLog(accountId, "RECHARGE", rc.getQuota(), balance, "REDEMPTION",
                "兑换充值码 +" + rc.getQuota() + " segments", null);
        log.info("充值码兑换: accountId={}, code={}, quota={}, balance={}", accountId, code, rc.getQuota(), balance);
        return (int) balance;
    }

    // ==================== 管理员调整 ====================

    @Override
    @Transactional
    public int adminAdjust(Long accountId, long newQuota, Long adminId) {
        Account acc = accountMapper.selectByIdForUpdate(accountId);
        if (acc == null) throw new BaseException("账户不存在");

        long old = acc.getQuota();
        long delta = newQuota - old;
        accountMapper.setQuota(accountId, newQuota);

        writeLog(accountId, "ADMIN", (int) delta, newQuota, "MANUAL",
                "管理员 #" + adminId + " 调整配额: " + old + " → " + newQuota, null);
        log.info("管理员调整配额: accountId={}, old={}, new={}, adminId={}", accountId, old, newQuota, adminId);
        return (int) newQuota;
    }

    // ==================== 查询 ====================

    @Override
    public long getBalance(Long accountId) {
        Account acc = accountMapper.selectById(accountId);
        return acc != null ? acc.getQuota() : 0;
    }

    // ==================== 新用户赠送 ====================

    public void grantNewUserBonus(Long accountId) {
        recharge(accountId, NEW_USER_BONUS, "REGISTER", "新用户注册赠送 " + NEW_USER_BONUS + " segments");
    }

    public void grantReward(Long accountId, int amount, String description) {
        recharge(accountId, amount, "REWARD", description);
    }

    // ==================== 内部方法 ====================

    private void writeLog(Long accountId, String type, int amount, long balance,
                          String source, String description, String requestId) {
        QuotaLog ql = new QuotaLog();
        ql.setAccountId(accountId);
        ql.setType(type);
        ql.setAmount(amount);
        ql.setBalance(balance);
        ql.setSource(source);
        ql.setDescription(description != null ? description : type);
        ql.setRequestId(requestId);
        quotaLogMapper.insert(ql);
    }
}
