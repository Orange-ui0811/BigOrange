package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.constant.QuoteStatus;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.dto.QuoteCreateDTO;
import com.orangejd.pojo.entity.Conversation;
import com.orangejd.pojo.entity.Quote;
import com.orangejd.server.mapper.ConversationMapper;
import com.orangejd.server.mapper.QuoteMapper;
import com.orangejd.server.service.IQuoteService;
import com.orangejd.server.service.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuoteServiceImpl implements IQuoteService {

    @Autowired private QuoteMapper quoteMapper;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private INotificationService notificationService;

    @Override
    public Quote create(QuoteCreateDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        Conversation conv = conversationMapper.selectById(dto.getConversationId());
        if (conv == null) throw new BaseException("会话不存在");
        if (!currentId.equals(conv.getInitiatorId()) && !currentId.equals(conv.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (dto.getPrice() == null || dto.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new BaseException("报价金额必须大于0");

        Quote q = new Quote();
        q.setConversationId(dto.getConversationId());
        q.setSenderId(currentId);
        q.setReceiverId(currentId.equals(conv.getInitiatorId()) ? conv.getReceiverId() : conv.getInitiatorId());
        q.setPrice(dto.getPrice());
        q.setDescription(dto.getDescription());
        q.setStatus(QuoteStatus.PENDING_CONFIRM);
        q.setExpireTime(LocalDateTime.now().plusDays(7));
        quoteMapper.insert(q);

        notificationService.send(q.getReceiverId(), "QUOTE", "你收到了一份报价 ¥" + dto.getPrice(), q.getId());
        return q;
    }

    @Override
    @Transactional
    public void accept(Long id) {
        Quote q = getAndCheckReceiver(id);
        if (!QuoteStatus.PENDING_CONFIRM.equals(q.getStatus()))
            throw new BaseException("报价单状态不允许接受");
        quoteMapper.updateStatus(id, QuoteStatus.ACCEPTED);
        notificationService.send(q.getSenderId(), "QUOTE", "你的报价已被接受", id);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        Quote q = getAndCheckReceiver(id);
        if (!QuoteStatus.PENDING_CONFIRM.equals(q.getStatus()))
            throw new BaseException("报价单状态不允许拒绝");
        quoteMapper.updateStatus(id, QuoteStatus.REJECTED);
        notificationService.send(q.getSenderId(), "QUOTE", "你的报价已被拒绝", id);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        Quote q = quoteMapper.selectById(id);
        if (q == null) throw new BaseException("报价单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(q.getSenderId()) && !currentId.equals(q.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!QuoteStatus.PENDING_CONFIRM.equals(q.getStatus()))
            throw new BaseException("只能取消待确认的报价");
        quoteMapper.updateStatus(id, QuoteStatus.CANCELLED);
    }

    @Override
    public Quote getDetail(Long id) {
        Quote q = quoteMapper.selectById(id);
        if (q == null) throw new BaseException("报价单不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(q.getSenderId()) && !currentId.equals(q.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        return q;
    }

    @Override
    public List<Quote> getByConversation(Long conversationId) {
        return quoteMapper.selectByConversationId(conversationId);
    }

    private Quote getAndCheckReceiver(Long id) {
        Quote q = quoteMapper.selectById(id);
        if (q == null) throw new BaseException("报价单不存在");
        if (!BaseContext.getCurrentId().equals(q.getReceiverId()))
            throw new BaseException("只有接收方可以操作报价");
        return q;
    }
}
