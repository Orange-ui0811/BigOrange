package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Work;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkMapper {

    int insert(Work work);

    int update(Work work);

    Work selectById(@Param("id") Long id);

    List<Work> selectByOwnerId(@Param("ownerId") Long ownerId, @Param("offset") int offset, @Param("limit") int limit);

    int countByOwnerId(@Param("ownerId") Long ownerId);

    int deleteById(@Param("id") Long id);

    int updateAuditStatus(@Param("id") Long id, @Param("auditStatus") String auditStatus);

    int updateVisibility(@Param("id") Long id, @Param("visibilityStatus") String visibilityStatus);

    /** 市场查询（仅已审核+公开） */
    List<Work> selectPublic(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                            @Param("orderBy") String orderBy, @Param("offset") int offset, @Param("limit") int limit);

    int countPublic(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /** 管理端查询（全部） */
    List<Work> selectAll(@Param("auditStatus") String auditStatus, @Param("offset") int offset, @Param("limit") int limit);

    int countAll(@Param("auditStatus") String auditStatus);

    /** 热门作品按收藏数 */
    java.util.List<java.util.Map<String, Object>> selectTopByFavorites(@Param("limit") int limit);

    /** 按日期统计新增作品 */
    int countByCreateDate(@Param("date") String date);
}
