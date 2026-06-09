package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Quote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuoteMapper {
    int insert(Quote quote);
    Quote selectById(@Param("id") Long id);
    List<Quote> selectByConversationId(@Param("conversationId") Long conversationId);
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    List<Quote> selectExpired(@Param("status") String status, @Param("now") LocalDateTime now);
}
