package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import com.orangejd.common.constant.OrderStatus;
import com.orangejd.common.constant.PayStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.dto.OrderCreateDTO;
import com.orangejd.pojo.dto.DeliverySubmitDTO;
import com.orangejd.pojo.entity.*;
import com.orangejd.pojo.vo.DeliveryAttachmentVO;
import com.orangejd.pojo.vo.OrderVO;
import com.orangejd.server.mapper.*;
import com.orangejd.server.service.OrderService;
import com.orangejd.server.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final PaymentStageMapper paymentStageMapper;
    private final DeliveryMapper deliveryMapper;
    private final WorkMapper workMapper;
    private final QuoteMapper quoteMapper;
    private final ConversationMapper conversationMapper;
    private final NotificationService notificationService;
    private final WorkSaleOptionMapper workSaleOptionMapper;
    private final com.orangejd.server.service.ContentAccessService contentAccessService;
    private final ManagedFileStorageService managedFileStorageService;
    @Value("${orange.trade.simulated-payment-enabled:false}")
    private boolean simulatedPaymentEnabled;

    @Override
    @Transactional
    public OrderVO create(OrderCreateDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        if (dto.getSourceId() == null) throw new BaseException("订单来源不能为空");
        BigDecimal deposit = money(dto.getDeposit(), "定金");
        BigDecimal finalAmount = money(dto.getFinalAmount(), "尾款");
        BigDecimal requestedTotal = money(dto.getTotalAmount(), "订单总额");
        if (requestedTotal.signum() <= 0) throw new BaseException("订单金额必须大于0");
        if (deposit.signum() <= 0 || finalAmount.signum() <= 0) {
            throw new BaseException("当前交付流程要求定金和尾款都大于0");
        }

        Order order = new Order();
        order.setSourceType(dto.getSourceType());
        order.setSourceId(dto.getSourceId());
        order.setBuyerId(currentId);
        order.setOrderStatus(OrderStatus.CREATED);

        BigDecimal authoritativeTotal;

        if ("QUOTE".equals(dto.getSourceType())) {
            Quote q = quoteMapper.selectByIdForUpdate(dto.getSourceId());
            if (q == null) throw new BaseException("报价单不存在");
            if (!currentId.equals(q.getSenderId())) throw new BaseException("只有报价发起方可以从报价创建订单");
            if (!com.orangejd.common.constant.QuoteStatus.ACCEPTED.equals(q.getStatus()))
                throw new BaseException("报价单未被接受，无法创建订单");
            if (q.getExpireTime() != null && q.getExpireTime().isBefore(LocalDateTime.now()))
                throw new BaseException("报价单已过期");
            if (orderMapper.countBySource("QUOTE", q.getId()) > 0)
                throw new BaseException("该报价已创建订单");
            order.setSellerId(q.getReceiverId()); // 报价接收方是卖家
            authoritativeTotal = q.getPrice();
            Conversation conv = conversationMapper.selectById(q.getConversationId());
            if (conv != null) { order.setWorkId(conv.getWorkId()); }
        } else if ("WORK".equals(dto.getSourceType())) {
            Work w = workMapper.selectById(dto.getSourceId());
            if (w == null) throw new BaseException("作品不存在");
            contentAccessService.requireAccessibleOwner("WORK", w.getId(), currentId);
            boolean priceExists = workSaleOptionMapper.selectByWorkId(w.getId()).stream()
                    .anyMatch(option -> option.getPrice() != null && option.getPrice().compareTo(requestedTotal) == 0);
            if (!priceExists) throw new BaseException("订单金额与作品售卖项不一致");
            order.setSellerId(w.getOwnerId());
            order.setWorkId(w.getId());
            authoritativeTotal = requestedTotal;
        } else {
            throw new BaseException("订单来源类型无效");
        }

        if (currentId.equals(order.getSellerId())) throw new BaseException("不能向自己下单");
        if (authoritativeTotal == null || authoritativeTotal.signum() <= 0
                || authoritativeTotal.compareTo(new BigDecimal("1000000")) > 0)
            throw new BaseException("订单金额超出允许范围");
        if (requestedTotal.compareTo(authoritativeTotal) != 0)
            throw new BaseException("订单金额与报价不一致");
        if (deposit.add(finalAmount).compareTo(authoritativeTotal) != 0)
            throw new BaseException("定金与尾款之和必须等于订单总额");

        order.setTotalAmount(authoritativeTotal);
        order.setDeposit(deposit);
        order.setFinalAmount(finalAmount);
        order.setOrderStatus(OrderStatus.WAITING_DEPOSIT);
        orderMapper.insert(order);

        // 创建定金支付阶段
        if (dto.getDeposit() != null && dto.getDeposit().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PaymentStage ps = new PaymentStage();
            ps.setOrderId(order.getId()); ps.setStageType("DEPOSIT"); ps.setAmount(dto.getDeposit());
            paymentStageMapper.insert(ps);
        }
        // 创建尾款支付阶段
        if (dto.getFinalAmount() != null && dto.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PaymentStage ps2 = new PaymentStage();
            ps2.setOrderId(order.getId()); ps2.setStageType("FINAL"); ps2.setAmount(dto.getFinalAmount());
            paymentStageMapper.insert(ps2);
        }

        notificationService.send(order.getSellerId(), "ORDER", "你有一个新订单", order.getId());
        notificationService.send(order.getBuyerId(), "ORDER", "订单已创建，请支付定金", order.getId());
        return getDetail(order.getId());
    }

    @Override
    public OrderVO getDetail(Long id) {
        OrderVO vo = orderMapper.selectVOById(id);
        if (vo == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(vo.getBuyerId()) && !currentId.equals(vo.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        vo.setPaymentStages(paymentStageMapper.selectByOrderId(id).stream().map(p -> {
            OrderVO.PaymentStageVO pvo = new OrderVO.PaymentStageVO();
            pvo.setId(p.getId()); pvo.setStageType(p.getStageType()); pvo.setAmount(p.getAmount());
            pvo.setPayStatus(p.getPayStatus()); pvo.setPayTime(p.getPayTime()); return pvo;
        }).collect(Collectors.toList()));
        vo.setDeliveries(deliveryMapper.selectByOrderId(id).stream().map(d -> {
            OrderVO.DeliveryVO dvo = new OrderVO.DeliveryVO();
            dvo.setId(d.getId()); dvo.setDelivererId(d.getDelivererId());
            dvo.setContent(d.getContent()); dvo.setAttachmentUrl(d.getAttachmentUrl());
            dvo.setAttachmentName(d.getAttachmentName());
            dvo.setAttachmentContentType(d.getAttachmentContentType());
            dvo.setAttachmentSize(d.getAttachmentSize());
            dvo.setCreateTime(d.getCreateTime()); return dvo;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override @Transactional
    public void payDeposit(Long id) {
        requirePaymentIntegration();
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.WAITING_DEPOSIT.equals(o.getOrderStatus()))
            throw new BaseException("订单状态不允许支付定金");
        orderMapper.updateStatus(id, OrderStatus.DEPOSIT_PAID);
        log.info("支付定金 orderId={} buyerId={}", id, o.getBuyerId());
        updatePaymentStage(id, "DEPOSIT");
        notificationService.send(o.getSellerId(), "ORDER", "买家已支付定金", id);
        notificationService.send(o.getBuyerId(), "ORDER", "定金支付成功，等待卖家交付", id);
    }

    @Override
    public DeliveryAttachmentVO uploadDeliveryAttachment(Long id, MultipartFile file) {
        Order order = getAndCheckSeller(id);
        if (!OrderStatus.DEPOSIT_PAID.equals(order.getOrderStatus()))
            throw new BaseException("定金支付后才能上传交付附件");
        ManagedFileStorageService.StoredAttachment stored =
                managedFileStorageService.uploadDelivery(file, id, BaseContext.getCurrentId());
        return new DeliveryAttachmentVO(stored.objectKey(), stored.originalName(),
                stored.contentType(), stored.size());
    }

    @Override
    public void deletePendingDeliveryAttachment(Long id, String objectKey) {
        Order order = getAndCheckSeller(id);
        if (!OrderStatus.DEPOSIT_PAID.equals(order.getOrderStatus()))
            throw new BaseException("已提交的交付附件不可删除");
        managedFileStorageService.deleteDelivery(objectKey, id, BaseContext.getCurrentId());
    }

    @Override
    public String getDeliveryAttachmentUrl(Long orderId, Long deliveryId) {
        Order order = getAndCheckParticipant(orderId);
        Delivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null || !order.getId().equals(delivery.getOrderId())
                || delivery.getAttachmentUrl() == null || delivery.getAttachmentUrl().isBlank()) {
            throw new BaseException("交付附件不存在");
        }
        return managedFileStorageService.createDownloadUrl(delivery.getAttachmentUrl(), orderId);
    }

    @Override @Transactional
    public void deliver(Long id, DeliverySubmitDTO dto) {
        Order o = getAndCheckSeller(id);
        if (!OrderStatus.DEPOSIT_PAID.equals(o.getOrderStatus()))
            throw new BaseException("定金支付后才能交付");
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.length() > 2048) throw new BaseException("交付内容不能超过 2048 字");
        String attachmentUrl = blankToNull(dto.getAttachmentUrl());
        if (content.isEmpty() && attachmentUrl == null) throw new BaseException("请填写交付内容或上传附件");
        if (attachmentUrl != null) {
            ManagedFileStorageService.requireDeliveryOwner(attachmentUrl, id, BaseContext.getCurrentId());
            if (!managedFileStorageService.existsDelivery(attachmentUrl, id, BaseContext.getCurrentId()))
                throw new BaseException("交付附件不存在或已被删除");
        }
        orderMapper.updateStatus(id, OrderStatus.WAITING_FINAL_PAYMENT);
        Delivery d = new Delivery();
        d.setOrderId(id); d.setDelivererId(BaseContext.getCurrentId());
        d.setContent(content); d.setAttachmentUrl(attachmentUrl);
        if (attachmentUrl != null) {
            d.setAttachmentName(normalizeAttachmentName(dto.getAttachmentName()));
            d.setAttachmentContentType(contentTypeFor(attachmentUrl));
            Long size = dto.getAttachmentSize();
            d.setAttachmentSize(size != null && size >= 0 && size <= 50L * 1024 * 1024 ? size : null);
        }
        deliveryMapper.insert(d);
        notificationService.send(o.getBuyerId(), "ORDER", "卖家已提交交付，请确认并支付尾款", id);
        notificationService.send(o.getSellerId(), "ORDER", "交付已提交，等待买家确认并支付尾款", id);
    }

    @Override @Transactional
    public void confirmDelivery(Long id) {
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.WAITING_FINAL_PAYMENT.equals(o.getOrderStatus()))
            throw new BaseException("当前状态不允许确认交付");
        orderMapper.updateStatus(id, OrderStatus.DELIVERED);
    }

    @Override @Transactional
    public void payFinal(Long id) {
        requirePaymentIntegration();
        Order o = getAndCheckBuyer(id);
        if (!OrderStatus.DELIVERED.equals(o.getOrderStatus()))
            throw new BaseException("请先确认交付，再支付尾款");
        orderMapper.updateStatus(id, OrderStatus.FINAL_PAID);
        updatePaymentStage(id, "FINAL");
        orderMapper.updateStatus(id, OrderStatus.COMPLETED);
        log.info("支付尾款 orderId={} buyerId={}", id, o.getBuyerId());
        notificationService.send(o.getSellerId(), "ORDER", "买家已支付尾款，订单完成", id);
        notificationService.send(o.getBuyerId(), "ORDER", "尾款支付成功，订单已完成", id);
    }

    @Override @Transactional
    public void cancel(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(o.getBuyerId()) && !currentId.equals(o.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!OrderStatus.WAITING_DEPOSIT.equals(o.getOrderStatus()))
            throw new BaseException("已进入履约或已付款的订单不能直接取消，请通过争议和退款流程处理");
        orderMapper.updateStatus(id, OrderStatus.CANCELLED);
        Long otherId = currentId.equals(o.getBuyerId()) ? o.getSellerId() : o.getBuyerId();
        notificationService.send(otherId, "ORDER", "订单已被取消", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(o.getBuyerId()) && !currentId.equals(o.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!OrderStatus.COMPLETED.equals(o.getOrderStatus()) && !OrderStatus.CANCELLED.equals(o.getOrderStatus()))
            throw new BaseException("只能删除已完成或已取消的订单");
        if (currentId.equals(o.getBuyerId())) orderMapper.hideForBuyer(id, currentId);
        else orderMapper.hideForSeller(id, currentId);
    }

    @Override
    public PageResult<OrderVO> listBuy(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = PageUtil.offset(page, size);
        int total = orderMapper.countByBuyerId(currentId);
        return new PageResult<>(total, orderMapper.selectByBuyerId(currentId, offset, size));
    }

    @Override
    public PageResult<OrderVO> listSell(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = PageUtil.offset(page, size);
        int total = orderMapper.countBySellerId(currentId);
        return new PageResult<>(total, orderMapper.selectBySellerId(currentId, offset, size));
    }

    private Order getAndCheckBuyer(Long id) {
        Order o = orderMapper.selectById(id); if (o == null) throw new BaseException("订单不存在");
        if (!BaseContext.getCurrentId().equals(o.getBuyerId())) throw new BaseException("只有买家可以此操作");
        return o;
    }
    private Order getAndCheckSeller(Long id) {
        Order o = orderMapper.selectById(id); if (o == null) throw new BaseException("订单不存在");
        if (!BaseContext.getCurrentId().equals(o.getSellerId())) throw new BaseException("只有卖家可以此操作");
        return o;
    }
    private Order getAndCheckParticipant(Long id) {
        Order o = orderMapper.selectById(id); if (o == null) throw new BaseException("订单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(o.getBuyerId()) && !currentId.equals(o.getSellerId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        return o;
    }

    private String normalizeAttachmentName(String value) {
        if (value == null || value.isBlank()) return "交付附件";
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 180 || name.contains("\r") || name.contains("\n"))
            throw new BaseException("附件文件名无效");
        return name;
    }

    private String contentTypeFor(String objectKey) {
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/zip";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private void updatePaymentStage(Long orderId, String stageType) {
        List<PaymentStage> stages = paymentStageMapper.selectByOrderId(orderId);
        for (PaymentStage ps : stages) {
            if (stageType.equals(ps.getStageType()) && PayStatus.UNPAID.equals(ps.getPayStatus())) {
                paymentStageMapper.updatePayStatus(ps.getId(), PayStatus.PAID);
            }
        }
    }

    private BigDecimal money(BigDecimal amount, String name) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.signum() < 0 || value.scale() > 2) throw new BaseException(name + "格式无效");
        return value;
    }

    private void requirePaymentIntegration() {
        if (!simulatedPaymentEnabled)
            throw new BaseException("订单资金支付尚未接入真实渠道，生产环境禁止模拟确认付款");
    }
}
