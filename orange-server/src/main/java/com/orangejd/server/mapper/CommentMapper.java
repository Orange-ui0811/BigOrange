package com.orangejd.server.mapper;

import com.orangejd.pojo.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    int insert(com.orangejd.pojo.entity.Comment comment);

    int deleteById(@Param("id") Long id);

    List<CommentVO> selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                                   @Param("offset") int offset, @Param("limit") int limit);

    int countByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    List<CommentVO> selectByAccountId(@Param("accountId") Long accountId, @Param("offset") int offset, @Param("limit") int limit);

    int countByAccountId(@Param("accountId") Long accountId);

    /** 查询给当前用户作品/需求的评论 */
    List<CommentVO> selectOnMyContent(@Param("ownerId") Long ownerId, @Param("offset") int offset, @Param("limit") int limit);

    int countOnMyContent(@Param("ownerId") Long ownerId);

    List<CommentVO> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    int countAll();
}
