package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.dto.MessageSendDTO;
import com.orangejd.pojo.entity.Conversation;
import com.orangejd.pojo.entity.Message;
import com.orangejd.server.mapper.ConversationMapper;
import com.orangejd.server.mapper.MessageMapper;
import com.orangejd.server.service.IMessageService;
import com.orangejd.server.service.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageServiceImpl implements IMessageService {

    @Autowired private MessageMapper messageMapper;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private INotificationService notificationService;

    @Override
    @Transactional
    public Message send(MessageSendDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        if (dto.getContent() == null || dto.getContent().trim().isEmpty())
            throw new BaseException("消息不能为空");

        Conversation conv = conversationMapper.selectById(dto.getConversationId());
        if (conv == null) throw new BaseException("会话不存在");

        // 规则3: 非参与方不能发送消息
        if (!currentId.equals(conv.getInitiatorId()) && !currentId.equals(conv.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);

        Message msg = new Message();
        msg.setConversationId(dto.getConversationId());
        msg.setSenderId(currentId);
        msg.setContent(dto.getContent().trim());
        messageMapper.insert(msg);

        // 更新会话时间
        conversationMapper.updateTime(dto.getConversationId());

        // 不创建通知——聊天未读数单独显示在「聊天」角标
        // WebSocket 实时推送
        Long receiverId = currentId.equals(conv.getInitiatorId()) ? conv.getReceiverId() : conv.getInitiatorId();
        com.orangejd.server.websocket.WebSocketServer.sendToUser(
                String.valueOf(receiverId),
                "{\"type\":\"message\",\"convId\":" + dto.getConversationId() + "}");

        return msg;
    }

    @Override
    public List<Message> getMessages(Long conversationId, int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new BaseException("会话不存在");
        if (!currentId.equals(conv.getInitiatorId()) && !currentId.equals(conv.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);

        int offset = (page - 1) * size;
        return messageMapper.selectByConversationId(conversationId, offset, size);
    }

    @Override
    public void markAsRead(Long conversationId) {
        Long currentId = BaseContext.getCurrentId();
        messageMapper.markAsRead(conversationId, currentId);
    }
}
