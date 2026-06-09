package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.BrowseHistory;
import com.orangejd.pojo.vo.HistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BrowseHistoryMapper {

    int insert(BrowseHistory history);

    List<BrowseHistory> selectByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    List<HistoryVO> selectVOByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    int deleteOldest(@Param("accountId") Long accountId, @Param("keepCount") int keepCount);
}
