package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Account;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.pojo.entity.QuotaOperation;
import com.orangejd.pojo.entity.RedemptionCode;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.QuotaLogMapper;
import com.orangejd.server.mapper.QuotaOperationMapper;
import com.orangejd.server.mapper.RedemptionCodeMapper;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.PricingSnapshot;
import com.orangejd.server.service.QuotaCharge;
import com.orangejd.server.service.QuotaRequestIdentity;
import com.orangejd.server.service.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {
    private static final Logger log = LoggerFactory.getLogger(QuotaServiceImpl.class);
    private static final long NEW_USER_BONUS = 100L;
    private static final long MAX_QUOTA = 1_000_000_000_000L;

    private final AccountMapper accountMapper;
    private final QuotaLogMapper quotaLogMapper;
    private final QuotaOperationMapper quotaOperationMapper;
    private final RedemptionCodeMapper redemptionCodeMapper;
    private final PricingService pricingService;

    @Override
    @Transactional
    public QuotaCharge consume(Long accountId, String serviceKey, String description,
                               QuotaRequestIdentity identity) {
        PricingSnapshot price = pricingService.getSnapshot(serviceKey);
        return consumeExact(accountId, price.segmentCost(), serviceKey, price.version(),
                description, identity);
    }

    @Override
    @Transactional
    public long consume(Long accountId, long amount, String description, String requestId) {
        return consumeExact(accountId, amount, null, null, description,
                QuotaRequestIdentity.legacy("quota:legacy-consume", requestId)).balance();
    }

    private QuotaCharge consumeExact(Long accountId, long amount, String serviceKey,
                                     Long priceVersion, String description,
                                     QuotaRequestIdentity identity) {
        requirePositiveAmount(amount);
        Account account = requireAccountForUpdate(accountId);
        QuotaOperation existing = quotaOperationMapper.selectByRequestIdForUpdate(identity.requestId());
        if (existing != null) {
            validateReplay(existing, accountId, identity);
            if ("REFUNDED".equals(existing.getStatus())) {
                throw new BaseException("该请求已失败并退回配额，请重新发起");
            }
            if (!"CONSUMED".equals(existing.getStatus())) {
                throw new BaseException("配额请求状态冲突");
            }
            return new QuotaCharge(account.getQuota(), existing.getAmount(),
                    existing.getPriceVersion() == null ? 0L : existing.getPriceVersion(),
                    existing.getOperationNo(), true);
        }

        if (accountMapper.decreaseQuota(accountId, amount) != 1) {
            throw new BaseException("余额不足，当前 " + account.getQuota()
                    + " segments，需 " + amount + " segments");
        }
        long balance = Math.subtractExact(account.getQuota(), amount);
        QuotaOperation operation = operation(accountId, amount, serviceKey, priceVersion,
                "CONSUMED", identity);
        quotaOperationMapper.insert(operation);
        QuotaLog entry = writeLog(operation, accountId, "CONSUME", -amount, balance,
                null, serviceKey, priceVersion, description, identity.requestId(),
                identity.requestHash(), null);
        if (quotaOperationMapper.attachDebitLog(operation.getId(), entry.getId()) != 1) {
            throw new BaseException("配额流水关联失败");
        }
        log.info("配额消费: operationNo={}, accountId={}, serviceKey={}, amount={}, balance={}",
                operation.getOperationNo(), accountId, serviceKey, amount, balance);
        return new QuotaCharge(balance, amount, priceVersion == null ? 0L : priceVersion,
                operation.getOperationNo(), false);
    }

    @Override
    @Transactional
    public long recharge(Long accountId, long amount, String source, String description) {
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("quota:direct-credit",
                "credit_" + UUID.randomUUID());
        return creditExact(accountId, amount, "RECHARGE", source, null, null,
                description, identity);
    }

    @Override
    @Transactional
    public long refund(Long accountId, String requestId, String description) {
        return refundInternal(accountId, null, requestId, description);
    }

    @Override
    @Transactional
    public long refund(Long accountId, long expectedAmount, String requestId, String description) {
        requirePositiveAmount(expectedAmount);
        return refundInternal(accountId, expectedAmount, requestId, description);
    }

    private long refundInternal(Long accountId, Long expectedAmount, String requestId,
                                String description) {
        if (requestId == null || requestId.isBlank()) throw new BaseException("原配额请求号不能为空");
        Account account = requireAccountForUpdate(accountId);
        QuotaOperation operation = quotaOperationMapper.selectByRequestIdForUpdate(requestId);
        if (operation == null) {
            return refundLegacy(account, expectedAmount, requestId, description);
        }
        if (!accountId.equals(operation.getAccountId())) throw new BaseException("原配额流水不属于当前账户");
        if (expectedAmount != null && expectedAmount.longValue() != operation.getAmount()) {
            throw new BaseException("退款金额与原消费不一致");
        }
        if ("REFUNDED".equals(operation.getStatus())) return account.getQuota();
        if (!"CONSUMED".equals(operation.getStatus()) || operation.getDebitLogId() == null) {
            throw new BaseException("原配额流水不可退款");
        }

        long amount = operation.getAmount();
        ensureCreditWithinLimit(account.getQuota(), amount);
        if (accountMapper.increaseQuota(accountId, amount) != 1) throw new BaseException("配额退款失败");
        long balance = Math.addExact(account.getQuota(), amount);
        String refundRequestId = refundRequestId(requestId);
        QuotaLog entry = writeLog(operation, accountId, "REFUND", amount, balance,
                null, operation.getServiceKey(), operation.getPriceVersion(), description,
                refundRequestId, operation.getRequestHash(), operation.getDebitLogId());
        if (quotaOperationMapper.markRefunded(operation.getId(), entry.getId()) != 1) {
            throw new BaseException("配额退款状态更新失败");
        }
        log.info("配额退款: operationNo={}, accountId={}, amount={}, balance={}",
                operation.getOperationNo(), accountId, amount, balance);
        return balance;
    }

    private long refundLegacy(Account account, Long expectedAmount, String requestId,
                              String description) {
        QuotaLog original = quotaLogMapper.selectByRequestIdForUpdate(requestId);
        if (original == null || !"CONSUME".equals(original.getType())
                || !account.getId().equals(original.getAccountId()) || original.getAmount() >= 0) {
            throw new BaseException("原配额消费流水不存在");
        }
        long amount = Math.negateExact(original.getAmount());
        if (expectedAmount != null && expectedAmount.longValue() != amount) {
            throw new BaseException("退款金额与原消费不一致");
        }
        String refundRequestId = refundRequestId(requestId);
        if (quotaLogMapper.existsByRequestId(refundRequestId) > 0) return account.getQuota();
        ensureCreditWithinLimit(account.getQuota(), amount);
        if (accountMapper.increaseQuota(account.getId(), amount) != 1) throw new BaseException("配额退款失败");
        long balance = Math.addExact(account.getQuota(), amount);
        writeLog(null, account.getId(), "REFUND", amount, balance, null,
                original.getServiceKey(), original.getPriceVersion(), description,
                refundRequestId, original.getRequestHash(), original.getId());
        return balance;
    }

    @Override
    @Transactional
    public long redeemCode(Long accountId, String code) {
        RedemptionCode redemption = redemptionCodeMapper.selectByCodeForUpdate(code);
        if (redemption == null) throw new BaseException("充值码不存在");
        if (redemption.getStatus() == 3) throw new BaseException("此充值码已被使用");
        if (redemption.getStatus() == 2) throw new BaseException("此充值码已禁用");
        requirePositiveAmount(redemption.getQuota());
        redemptionCodeMapper.updateUsed(code, accountId);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("quota:redemption",
                "redemption_" + redemption.getId());
        return creditExact(accountId, redemption.getQuota(), "RECHARGE", "REDEMPTION",
                null, null, "兑换充值码 +" + redemption.getQuota() + " segments", identity);
    }

    @Override
    @Transactional
    public long adminAdjust(Long accountId, long newQuota, Long adminId) {
        if (newQuota < 0 || newQuota > MAX_QUOTA) throw new BaseException("配额调整值超出允许范围");
        Account account = requireAccountForUpdate(accountId);
        long delta = Math.subtractExact(newQuota, account.getQuota());
        if (delta == 0) return newQuota;
        if (accountMapper.setQuota(accountId, newQuota) != 1) throw new BaseException("配额调整失败");

        long amount = Math.abs(delta);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("quota:admin-adjust",
                "admin_adjust_" + UUID.randomUUID());
        QuotaOperation operation = operation(accountId, amount, null, null,
                delta > 0 ? "CREDITED" : "CONSUMED", identity);
        quotaOperationMapper.insert(operation);
        QuotaLog entry = writeLog(operation, accountId, "ADMIN", delta, newQuota,
                "MANUAL", null, null,
                "管理员 #" + adminId + " 调整配额: " + account.getQuota() + " → " + newQuota,
                identity.requestId(), identity.requestHash(), null);
        quotaOperationMapper.attachDebitLog(operation.getId(), entry.getId());
        log.info("管理员调整配额: operationNo={}, accountId={}, old={}, new={}, adminId={}",
                operation.getOperationNo(), accountId, account.getQuota(), newQuota, adminId);
        return newQuota;
    }

    @Override
    public long getBalance(Long accountId) {
        Account account = accountMapper.selectById(accountId);
        return account == null ? 0L : account.getQuota();
    }

    @Override
    @Transactional
    public long grantNewUserBonus(Long accountId) {
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("quota:registration-bonus",
                "registration_bonus_" + accountId);
        return creditExact(accountId, NEW_USER_BONUS, "REWARD", "REGISTER", null, 1L,
                "新用户注册赠送 " + NEW_USER_BONUS + " segments", identity);
    }

    @Override
    @Transactional
    public long grantReward(Long accountId, String serviceKey, String businessReference,
                            String description) {
        PricingSnapshot price = pricingService.getSnapshot(serviceKey);
        if (price.segmentCost() == 0) return getBalance(accountId);
        String reference = businessReference == null ? "unknown" : businessReference.trim();
        String requestId = "reward_" + QuotaRequestIdentity.sha256(
                accountId + "\n" + serviceKey + "\n" + reference);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("quota:reward", requestId);
        return creditExact(accountId, price.segmentCost(), "REWARD", reference,
                serviceKey, price.version(), description, identity);
    }

    private long creditExact(Long accountId, long amount, String type, String source,
                             String serviceKey, Long priceVersion, String description,
                             QuotaRequestIdentity identity) {
        requirePositiveAmount(amount);
        Account account = requireAccountForUpdate(accountId);
        QuotaOperation existing = quotaOperationMapper.selectByRequestIdForUpdate(identity.requestId());
        if (existing != null) {
            validateReplay(existing, accountId, identity);
            if (!"CREDITED".equals(existing.getStatus())) throw new BaseException("配额请求状态冲突");
            return account.getQuota();
        }
        ensureCreditWithinLimit(account.getQuota(), amount);
        if (accountMapper.increaseQuota(accountId, amount) != 1) throw new BaseException("配额入账失败");
        long balance = Math.addExact(account.getQuota(), amount);
        QuotaOperation operation = operation(accountId, amount, serviceKey, priceVersion,
                "CREDITED", identity);
        quotaOperationMapper.insert(operation);
        QuotaLog entry = writeLog(operation, accountId, type, amount, balance, source,
                serviceKey, priceVersion, description, identity.requestId(),
                identity.requestHash(), null);
        if (quotaOperationMapper.attachDebitLog(operation.getId(), entry.getId()) != 1) {
            throw new BaseException("配额流水关联失败");
        }
        log.info("配额入账: operationNo={}, accountId={}, source={}, amount={}, balance={}",
                operation.getOperationNo(), accountId, source, amount, balance);
        return balance;
    }

    private QuotaOperation operation(Long accountId, long amount, String serviceKey,
                                     Long priceVersion, String status,
                                     QuotaRequestIdentity identity) {
        QuotaOperation operation = new QuotaOperation();
        operation.setOperationNo(transactionNo());
        operation.setAccountId(accountId);
        operation.setIdempotencyScope(identity.scope());
        operation.setRequestId(identity.requestId());
        operation.setIdempotencyKeyHash(identity.idempotencyKeyHash());
        operation.setRequestHash(identity.requestHash());
        operation.setServiceKey(serviceKey);
        operation.setPriceVersion(priceVersion);
        operation.setAmount(amount);
        operation.setStatus(status);
        return operation;
    }

    private QuotaLog writeLog(QuotaOperation operation, Long accountId, String type,
                              long amount, long balance, String source, String serviceKey,
                              Long priceVersion, String description, String requestId,
                              String requestHash, Long originalLogId) {
        QuotaLog entry = new QuotaLog();
        entry.setTransactionNo(transactionNo());
        entry.setOperationId(operation == null ? null : operation.getId());
        entry.setOriginalLogId(originalLogId);
        entry.setAccountId(accountId);
        entry.setType(type);
        entry.setAmount(amount);
        entry.setBalance(balance);
        entry.setSource(source);
        entry.setServiceKey(serviceKey);
        entry.setPriceVersion(priceVersion);
        entry.setDescription(description == null ? type : description);
        entry.setRequestId(requestId);
        entry.setRequestHash(requestHash);
        entry.setStatus("POSTED");
        if (quotaLogMapper.insert(entry) != 1) throw new BaseException("配额流水写入失败");
        return entry;
    }

    private Account requireAccountForUpdate(Long accountId) {
        if (accountId == null || accountId <= 0) throw new BaseException("账户不存在");
        Account account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null) throw new BaseException("账户不存在");
        return account;
    }

    private void validateReplay(QuotaOperation operation, Long accountId,
                                QuotaRequestIdentity identity) {
        if (!accountId.equals(operation.getAccountId())
                || !identity.scope().equals(operation.getIdempotencyScope())) {
            throw new BaseException("配额幂等键作用域冲突");
        }
        if (!Objects.equals(identity.requestHash(), operation.getRequestHash())) {
            throw new BaseException("同一幂等键不能用于不同请求内容");
        }
    }

    private void ensureCreditWithinLimit(long balance, long amount) {
        if (amount > MAX_QUOTA || balance > MAX_QUOTA - amount) {
            throw new BaseException("配额余额超出允许范围");
        }
    }

    private void requirePositiveAmount(long amount) {
        if (amount <= 0 || amount > MAX_QUOTA) throw new BaseException("配额数量必须大于 0");
    }

    private String refundRequestId(String originalRequestId) {
        return "refund_" + QuotaRequestIdentity.sha256(originalRequestId);
    }

    private String transactionNo() {
        return "OVCQ_" + UUID.randomUUID().toString().replace("-", "");
    }
}
