package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    int insert(Notification notification);

    int markAsRead(@Param("id") Long id, @Param("accountId") Long accountId);

    List<Notification> selectByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    int countUnread(@Param("accountId") Long accountId);

    int deleteOldRead(@Param("days") int days);
}
