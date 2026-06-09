package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    int insert(Message message);

    List<Message> selectByConversationId(@Param("conversationId") Long conversationId, @Param("offset") int offset, @Param("limit") int limit);

    int markAsRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    int countUnread(@Param("userId") Long userId);
}
