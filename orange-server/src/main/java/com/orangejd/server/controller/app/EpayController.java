package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.entity.TopUp;
import com.orangejd.server.config.EpayProperties;
import com.orangejd.server.mapper.TopUpMapper;
import com.orangejd.server.service.EpayService;
import com.orangejd.server.service.PaymentApplicationService;
import com.orangejd.server.service.RateLimitService;
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
    @Autowired private TopUpMapper topUpMapper;
    @Autowired private EpayProperties epayProperties;
    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private RateLimitService rateLimitService;

    private static final Set<Integer> ALLOWED_AMOUNTS = Set.of(1000, 3000, 5000, 10000);
    private static final Set<String> ALLOWED_METHODS = Set.of("wxpay", "alipay");
    private static final int MAX_CALLBACK_FIELDS = 32;
    private static final int MAX_CALLBACK_NAME_LENGTH = 64;
    private static final int MAX_CALLBACK_VALUE_LENGTH = 1024;

    /** 创建支付 */
    @PostMapping("/app/epay/create")
    @Transactional
    public Result<Map<String, String>> create(@RequestBody Map<String, String> body) {
        if (!epayProperties.isConfigured()) return Result.error("支付功能暂未开通");

        Long accountId = BaseContext.getCurrentId();
        if (!rateLimitService.allow("payment-create:account:" + accountId, 10, 300)) {
            return Result.error("创建支付订单过于频繁，请稍后重试");
        }
        int amount;
        try {
            amount = Integer.parseInt(body.getOrDefault("amount", "1000"));
        } catch (NumberFormatException e) {
            return Result.error("充值套餐无效");
        }
        String method = body.getOrDefault("method", "wxpay");
        if (!ALLOWED_AMOUNTS.contains(amount)) return Result.error("充值套餐无效");
        if (!ALLOWED_METHODS.contains(method)) return Result.error("支付方式无效");

        // 生成唯一 tradeNo
        String tradeNo = "EPAY_" + accountId + "_" + UUID.randomUUID().toString().replace("-", "");

        // 写 top_up 记录
        TopUp topUp = new TopUp();
        topUp.setAccountId(accountId);
        topUp.setAmount(amount);
        topUp.setMoney(BigDecimal.valueOf(amount, 2));
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
    public String notify(HttpServletRequest request) {
        try {
            Map<String, String> params = parseParams(request);
            log.info("Epay 回调: tradeNo={}, status={}", safeLog(params.get("out_trade_no")), safeLog(params.get("trade_status")));
            paymentApplicationService.processEpayCallback(params);
        } catch (Exception e) {
            log.warn("Epay 回调处理失败: ip={}, error={}", request.getRemoteAddr(), e.getClass().getSimpleName());
            return "fail";
        }

        return "success";
    }

    /** Epay 回调通知（GET，部分支付网关用 GET） */
    @GetMapping("/api/epay/notify")
    public String notifyGet(HttpServletRequest request) {
        log.warn("拒绝 GET 支付回调: ip={}", request.getRemoteAddr());
        return "fail";
    }

    /** 管理员补单 */
    @PostMapping("/admin/topup/{tradeNo}/retry")
    public Result<?> retry(@PathVariable String tradeNo) {
        return Result.error("为避免错误入账，补单前必须通过支付渠道主动查询；当前渠道未提供查询接口");
    }

    /** 订单列表（管理端） */
    @GetMapping("/admin/topup/list")
    public Result<List<TopUp>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "30") int size) {
        int safeSize = PageUtil.capSize(size);
        return Result.success(topUpMapper.selectAll(PageUtil.offset(page, safeSize), safeSize));
    }

    private Map<String, String> parseParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (params.size() >= MAX_CALLBACK_FIELDS || name.length() > MAX_CALLBACK_NAME_LENGTH) {
                throw new IllegalArgumentException("支付回调参数过多");
            }
            String value = request.getParameter(name);
            if (value != null && value.length() > MAX_CALLBACK_VALUE_LENGTH) {
                throw new IllegalArgumentException("支付回调参数过长");
            }
            params.put(name, value);
        }
        return params;
    }

    private String safeLog(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(value.length(), 80));
    }
}
