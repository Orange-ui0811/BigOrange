package com.orangejd.server.service;

import com.orangejd.pojo.entity.Notification;

import java.util.List;

public interface NotificationService {
    /** 发送通知（供其他 Service 调用） */
    void send(Long accountId, String type, String message, Long relatedId);
    List<Notification> listMyNotifications(int page, int size);
    void markAsRead(Long id);
    int unreadCount();
    void batchDelete(java.util.List<Long> ids);
}
