package com.orangejd.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.entity.Notification;
import com.orangejd.server.mapper.NotificationMapper;
import com.orangejd.server.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired private NotificationMapper notificationMapper;
    @Autowired private ObjectMapper objectMapper;

    @Override
    public void send(Long accountId, String type, String message, Long relatedId) {
        Notification n = new Notification();
        n.setAccountId(accountId);
        n.setType(type);
        n.setMessage(message);
        n.setRelatedId(relatedId);
        notificationMapper.insert(n);
        // WebSocket 实时推送通知
        try {
            com.orangejd.server.websocket.WebSocketServer.sendToUser(
                    String.valueOf(accountId), objectMapper.writeValueAsString(
                            Map.of("type", "notification", "message", message)));
        } catch (JsonProcessingException e) {
            throw new BaseException("通知序列化失败");
        }
    }

    @Override
    public List<Notification> listMyNotifications(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = PageUtil.offset(page, size);
        return notificationMapper.selectByAccountId(currentId, offset, size);
    }

    @Override
    public void markAsRead(Long id) {
        Long currentId = BaseContext.getCurrentId();
        int rows = notificationMapper.markAsRead(id, currentId);
        if (rows == 0) throw new BaseException(MessageConstant.NO_PERMISSION);
    }

    @Override
    public int unreadCount() {
        return notificationMapper.countUnread(BaseContext.getCurrentId());
    }

    @Override
    public void batchDelete(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        notificationMapper.deleteByIds(ids, BaseContext.getCurrentId());
    }
}
