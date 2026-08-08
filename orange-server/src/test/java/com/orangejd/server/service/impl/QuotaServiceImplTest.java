package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Account;
import com.orangejd.pojo.entity.QuotaLog;
import com.orangejd.pojo.entity.QuotaOperation;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.QuotaLogMapper;
import com.orangejd.server.mapper.QuotaOperationMapper;
import com.orangejd.server.mapper.RedemptionCodeMapper;
import com.orangejd.server.service.PricingService;
import com.orangejd.server.service.PricingSnapshot;
import com.orangejd.server.service.QuotaCharge;
import com.orangejd.server.service.QuotaRequestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaServiceImplTest {
    private AccountMapper accounts;
    private QuotaLogMapper logs;
    private QuotaOperationMapper operations;
    private PricingService pricing;
    private QuotaServiceImpl service;
    private AtomicReference<QuotaOperation> storedOperation;
    private List<QuotaLog> storedLogs;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountMapper.class);
        logs = mock(QuotaLogMapper.class);
        operations = mock(QuotaOperationMapper.class);
        pricing = mock(PricingService.class);
        service = new QuotaServiceImpl(accounts, logs, operations,
                mock(RedemptionCodeMapper.class), pricing);
        storedOperation = new AtomicReference<>();
        storedLogs = new ArrayList<>();
        AtomicLong operationIds = new AtomicLong(10);
        AtomicLong logIds = new AtomicLong(100);
        when(operations.selectByRequestIdForUpdate(any())).thenAnswer(invocation -> {
            QuotaOperation operation = storedOperation.get();
            return operation != null && operation.getRequestId().equals(invocation.getArgument(0))
                    ? operation : null;
        });
        when(operations.insert(any())).thenAnswer(invocation -> {
            QuotaOperation operation = invocation.getArgument(0);
            operation.setId(operationIds.incrementAndGet());
            storedOperation.set(operation);
            return 1;
        });
        when(logs.insert(any())).thenAnswer(invocation -> {
            QuotaLog entry = invocation.getArgument(0);
            entry.setId(logIds.incrementAndGet());
            storedLogs.add(entry);
            return 1;
        });
        when(operations.attachDebitLog(any(), any())).thenAnswer(invocation -> {
            storedOperation.get().setDebitLogId(invocation.getArgument(1));
            return 1;
        });
        when(operations.markRefunded(any(), any())).thenAnswer(invocation -> {
            storedOperation.get().setStatus("REFUNDED");
            storedOperation.get().setRefundLogId(invocation.getArgument(1));
            return 1;
        });
    }

    @Test
    void identicalRequestIsDebitedOnlyOnce() {
        when(pricing.getSnapshot("ai:plan")).thenReturn(new PricingSnapshot("ai:plan", 40, 7));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account(7L, 100), account(7L, 60));
        when(accounts.decreaseQuota(7L, 40)).thenReturn(1);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("ai:plan", "request-1");

        QuotaCharge first = service.consume(7L, "ai:plan", "方案设计", identity);
        QuotaCharge replay = service.consume(7L, "ai:plan", "方案设计", identity);

        assertEquals(60, first.balance());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(40, replay.amount());
        assertEquals(7, replay.priceVersion());
        verify(accounts, times(1)).decreaseQuota(7L, 40);
        verify(logs, times(1)).insert(any());
    }

    @Test
    void sameIdempotencyKeyCannotBeReusedForDifferentPayload() {
        when(pricing.getSnapshot("ai:plan")).thenReturn(new PricingSnapshot("ai:plan", 40, 3));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account(7L, 100), account(7L, 60));
        when(accounts.decreaseQuota(7L, 40)).thenReturn(1);
        QuotaRequestIdentity first = new QuotaRequestIdentity(
                "ai:plan", "idem-request", "key-hash", "payload-a");
        QuotaRequestIdentity changed = new QuotaRequestIdentity(
                "ai:plan", "idem-request", "key-hash", "payload-b");

        service.consume(7L, "ai:plan", "方案设计", first);

        assertThrows(BaseException.class,
                () -> service.consume(7L, "ai:plan", "方案设计", changed));
        verify(accounts, times(1)).decreaseQuota(7L, 40);
    }

    @Test
    void refundUsesOriginalAmountAndIsPostedOnlyOnce() {
        when(pricing.getSnapshot("ai:plan")).thenReturn(new PricingSnapshot("ai:plan", 40, 2));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(
                account(7L, 100), account(7L, 60), account(7L, 100));
        when(accounts.decreaseQuota(7L, 40)).thenReturn(1);
        when(accounts.increaseQuota(7L, 40)).thenReturn(1);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("ai:plan", "request-refund");
        service.consume(7L, "ai:plan", "方案设计", identity);

        long firstRefundBalance = service.refund(7L, identity.requestId(), "失败退款");
        long repeatedRefundBalance = service.refund(7L, identity.requestId(), "重复通知");

        assertEquals(100, firstRefundBalance);
        assertEquals(100, repeatedRefundBalance);
        assertEquals(2, storedLogs.size());
        QuotaLog debit = storedLogs.get(0);
        QuotaLog refund = storedLogs.get(1);
        assertEquals(-40, debit.getAmount());
        assertEquals(40, refund.getAmount());
        assertEquals(debit.getId(), refund.getOriginalLogId());
        assertEquals(debit.getPriceVersion(), refund.getPriceVersion());
        verify(accounts, times(1)).increaseQuota(7L, 40);
    }

    @Test
    void compatibilityRefundRejectsAnAmountDifferentFromOriginalDebit() {
        when(pricing.getSnapshot("ai:plan")).thenReturn(new PricingSnapshot("ai:plan", 40, 1));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account(7L, 100), account(7L, 60));
        when(accounts.decreaseQuota(7L, 40)).thenReturn(1);
        QuotaRequestIdentity identity = QuotaRequestIdentity.legacy("ai:plan", "request-wrong-refund");
        service.consume(7L, "ai:plan", "方案设计", identity);

        assertThrows(BaseException.class,
                () -> service.refund(7L, 99, identity.requestId(), "错误金额"));
        verify(accounts, never()).increaseQuota(any(), any(Long.class));
    }

    @Test
    void businessRewardReferenceMakesRewardIdempotent() {
        when(pricing.getSnapshot(PricingService.REWARD_WORK_APPROVED)).thenReturn(
                new PricingSnapshot(PricingService.REWARD_WORK_APPROVED, 25, 4));
        when(accounts.selectByIdForUpdate(7L)).thenReturn(account(7L, 0), account(7L, 25));
        when(accounts.increaseQuota(7L, 25)).thenReturn(1);

        service.grantReward(7L, PricingService.REWARD_WORK_APPROVED,
                "work:9", "审核奖励");
        service.grantReward(7L, PricingService.REWARD_WORK_APPROVED,
                "work:9", "审核奖励");

        verify(accounts, times(1)).increaseQuota(7L, 25);
        ArgumentCaptor<QuotaOperation> operation = ArgumentCaptor.forClass(QuotaOperation.class);
        verify(operations).insert(operation.capture());
        assertTrue(operation.getValue().getRequestId().startsWith("reward_"));
        assertEquals(4, operation.getValue().getPriceVersion());
    }

    private Account account(Long id, long quota) {
        Account account = new Account();
        account.setId(id);
        account.setQuota(quota);
        return account;
    }
}
