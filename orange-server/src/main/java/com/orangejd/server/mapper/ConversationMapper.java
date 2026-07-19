package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Conversation;
import com.orangejd.pojo.vo.ConversationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationMapper {

    int insert(Conversation conversation);

    Conversation selectById(@Param("id") Long id);

    Conversation selectExisting(@Param("initiatorId") Long initiatorId, @Param("receiverId") Long receiverId,
                                @Param("workId") Long workId, @Param("requirementId") Long requirementId);

    List<ConversationVO> selectByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    int updateTime(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    int deleteMessagesByConvId(@Param("conversationId") Long conversationId);

    int hideByUser(@Param("id") Long id, @Param("userId") Long userId, @Param("isInitiator") boolean isInitiator);

    int showForAll(@Param("id") Long id);

    int deleteMessagesByUser(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
