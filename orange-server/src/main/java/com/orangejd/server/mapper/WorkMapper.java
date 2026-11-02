package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Work;
import com.orangejd.server.annotation.AutoFill;
import com.orangejd.common.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Work work);

    @AutoFill(OperationType.UPDATE)
    int update(Work work);

    Work selectById(@Param("id") Long id);

    Work selectByIdForUpdate(@Param("id") Long id);

    List<Work> selectByOwnerId(@Param("ownerId") Long ownerId, @Param("offset") int offset, @Param("limit") int limit);

    int countByOwnerId(@Param("ownerId") Long ownerId);

    int deleteById(@Param("id") Long id);

    int updateAuditStatus(@Param("id") Long id, @Param("auditStatus") String auditStatus);

    int approveAndMarkRewarded(@Param("id") Long id);

    int approvePreviouslyRewarded(@Param("id") Long id);

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

    /** 榜单: 全时段作品收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardWorks(@Param("categoryId") Long categoryId, @Param("limit") int limit);

    /** 榜单: 本周作品收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardWorksWeekly(@Param("categoryId") Long categoryId, @Param("weekStart") String weekStart, @Param("limit") int limit);

    /** 榜单: 作品总数（全时段） */
    int countLeaderboardWorks(@Param("categoryId") Long categoryId);

    /** 榜单: 作品总数（本周） */
    int countLeaderboardWorksWeekly(@Param("categoryId") Long categoryId, @Param("weekStart") String weekStart);
}
