package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.mapper.TopUpMapper;
import com.orangejd.server.service.EpayService;
import com.orangejd.server.service.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;

@RestController
public class EpayController {

    private static final Logger log = LoggerFactory.getLogger(EpayController.class);

    @Autowired private EpayService epayService;
    @Autowired private QuotaService quotaService;
    @Autowired private TopUpMapper topUpMapper;
    @Autowired private EpayProperties epayProperties;

    /** 创建支付 */
    @PostMapping("/app/epay/create")
    public Result<Map<String, String>> create(@RequestBody Map<String, String> body) {
        if (!epayProperties.isConfigured()) return Result.error("支付功能暂未开通");

        Long accountId = BaseContext.getCurrentId();
        int amount = Integer.parseInt(body.getOrDefault("amount", "1000")); // segments
        String method = body.getOrDefault("method", "wxpay");

        // 生成唯一 tradeNo
        String tradeNo = "EPAY_" + accountId + "_" + UUID.randomUUID().toString().substring(0, 12);

        // 写 top_up 记录
        TopUp topUp = new TopUp();
        topUp.setAccountId(accountId);
        topUp.setAmount(amount);
        topUp.setMoney(new BigDecimal(amount).divide(new BigDecimal(100)));
        topUp.setTradeNo(tradeNo);
        topUp.setPaymentMethod(method);
        topUp.setPaymentProvider("epay");
        topUp.setStatus("pending");
        topUpMapper.insert(topUp);

        // 创建支付链接
        String payUrl = epayService.createPayment(tradeNo, amount, method);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("payUrl", payUrl);
        result.put("tradeNo", tradeNo);
        return Result.success(result);
    }

    /** Epay 回调通知（POST） */
    @PostMapping("/api/epay/notify")
    @Transactional
    public String notify(HttpServletRequest request) {
        Map<String, String> params = parseParams(request);
        log.info("Epay 回调: tradeNo={}, status={}", params.get("out_trade_no"), params.get("trade_status"));

        // ① 验签
        if (!epayService.verify(params)) {
            log.warn("Epay 签名验证失败: tradeNo={}", params.get("out_trade_no"));
            return "fail";
        }

        String tradeNo = params.get("out_trade_no");
        if (tradeNo == null) return "fail";

        // ② 处理订单
        try {
            completeTopUp(tradeNo);
        } catch (Exception e) {
            log.error("Epay 回调处理失败: tradeNo={}, error={}", tradeNo, e.getMessage());
            return "fail";
        }

        return "success";
    }

    /** Epay 回调通知（GET，部分支付网关用 GET） */
    @GetMapping("/api/epay/notify")
    @Transactional
    public String notifyGet(HttpServletRequest request) {
        return notify(request);
    }

    /** 管理员补单 */
    @PostMapping("/admin/topup/{tradeNo}/retry")
    public Result<?> retry(@PathVariable String tradeNo) {
        try {
            completeTopUp(tradeNo);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 订单列表（管理端） */
    @GetMapping("/admin/topup/list")
    public Result<List<TopUp>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "30") int size) {
        int offset = (page - 1) * size;
        return Result.success(topUpMapper.selectAll(offset, Math.min(size, 100)));
    }

    // ==================== 内部方法 ====================

    private void completeTopUp(String tradeNo) {
        // FOR UPDATE 锁订单行
        TopUp topUp = topUpMapper.selectByTradeNoForUpdate(tradeNo);
        if (topUp == null) {
            log.warn("订单不存在: tradeNo={}", tradeNo);
            return;
        }
        if (!"pending".equals(topUp.getStatus())) {
            log.info("订单已处理: tradeNo={}, status={}", tradeNo, topUp.getStatus());
            return; // 幂等
        }

        // 更新状态
        topUpMapper.updateStatus(tradeNo, "success");

        // 加配额
        quotaService.recharge(topUp.getAccountId(), topUp.getAmount(), "EPAY",
                "Epay 在线充值 " + topUp.getMoney() + " 元 → " + topUp.getAmount() + " segments");
        log.info("Epay 充值完成: tradeNo={}, accountId={}, amount={}", tradeNo, topUp.getAccountId(), topUp.getAmount());
    }

    private Map<String, String> parseParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }
}
