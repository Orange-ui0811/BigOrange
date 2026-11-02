package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.pojo.entity.QuotaOperation;
import com.orangejd.server.mapper.QuotaLogMapper;
import com.orangejd.server.mapper.QuotaOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaLedgerService {
    private final QuotaLogMapper quotaLogMapper;
    private final QuotaOperationMapper quotaOperationMapper;

    public QuotaOperation findOperationForUpdate(String requestId) {
        return quotaOperationMapper.selectByRequestIdForUpdate(requestId);
    }

    public QuotaLog findLegacyDebitForUpdate(String requestId) {
        return quotaLogMapper.selectByRequestIdForUpdate(requestId);
    }

    public boolean legacyRequestExists(String requestId) {
        return quotaLogMapper.existsByRequestId(requestId) > 0;
    }

    public QuotaOperation createOperation(Long accountId, long amount, String serviceKey,
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
        if (quotaOperationMapper.insert(operation) != 1) {
            throw new BaseException("配额业务操作写入失败");
        }
        return operation;
    }

    public QuotaLog writeLog(QuotaOperation operation, Long accountId, String type,
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

    public void attachDebitLog(QuotaOperation operation, QuotaLog entry) {
        if (quotaOperationMapper.attachDebitLog(operation.getId(), entry.getId()) != 1) {
            throw new BaseException("配额流水关联失败");
        }
    }

    public void markRefunded(QuotaOperation operation, QuotaLog entry) {
        if (quotaOperationMapper.markRefunded(operation.getId(), entry.getId()) != 1) {
            throw new BaseException("配额退款状态更新失败");
        }
    }

    public void validateReplay(QuotaOperation operation, Long accountId,
                               QuotaRequestIdentity identity) {
        if (!accountId.equals(operation.getAccountId())
                || !identity.scope().equals(operation.getIdempotencyScope())) {
            throw new BaseException("配额幂等键作用域冲突");
        }
        if (!Objects.equals(identity.requestHash(), operation.getRequestHash())) {
            throw new BaseException("同一幂等键不能用于不同请求内容");
        }
    }

    public String refundRequestId(String originalRequestId) {
        return "refund_" + QuotaRequestIdentity.sha256(originalRequestId);
    }

    private String transactionNo() {
        return "OVCQ_" + UUID.randomUUID().toString().replace("-", "");
    }
}
