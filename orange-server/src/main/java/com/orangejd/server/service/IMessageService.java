package com.orangejd.server.service;

import com.orangejd.pojo.dto.MessageSendDTO;
import com.orangejd.pojo.entity.Message;

import java.util.List;

public interface IMessageService {
    Message send(MessageSendDTO dto);
    List<Message> getMessages(Long conversationId, int page, int size);
    void markAsRead(Long conversationId);
}
