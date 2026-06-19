package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.TrendHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TrendHistoryMapper {
    int insert(TrendHistory record);
    List<TrendHistory> selectByBrowserId(@Param("accountId") String accountId, @Param("limit") int limit);
    int deleteById(@Param("id") Long id, @Param("accountId") String accountId);
    int deleteByBrowserId(@Param("accountId") String accountId);
}
