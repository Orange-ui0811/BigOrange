package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Favorite;
import com.orangejd.pojo.vo.FavoriteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    int insert(Favorite favorite);

    int delete(@Param("accountId") Long accountId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    int deleteById(@Param("id") Long id, @Param("accountId") Long accountId);

    boolean exists(@Param("accountId") Long accountId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

    List<Favorite> selectByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    List<FavoriteVO> selectVOByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    int countByAccountId(@Param("accountId") Long accountId);

    int countByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
