package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.entity.Notification;
import com.orangejd.server.mapper.NotificationMapper;
import com.orangejd.server.service.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements INotificationService {

    @Autowired private NotificationMapper notificationMapper;

    @Override
    public void send(Long accountId, String type, String message, Long relatedId) {
        Notification n = new Notification();
        n.setAccountId(accountId);
        n.setType(type);
        n.setMessage(message);
        n.setRelatedId(relatedId);
        notificationMapper.insert(n);
    }

    @Override
    public List<Notification> listMyNotifications(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = (page - 1) * size;
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
}
