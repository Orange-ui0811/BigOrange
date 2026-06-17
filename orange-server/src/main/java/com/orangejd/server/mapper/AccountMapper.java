package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account selectByUsername(@Param("username") String username);

    Account selectById(@Param("id") Long id);

    int insert(Account account);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    java.util.List<com.orangejd.pojo.entity.Account> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    int countAll();

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    java.util.List<java.util.Map<String, Object>> selectTopCreators(@Param("limit") int limit);

    /** 榜单: 全时段创作者收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardCreators(@Param("limit") int limit);

    /** 榜单: 本周创作者收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardCreatorsWeekly(@Param("weekStart") String weekStart, @Param("limit") int limit);

    /** 榜单: 创作者总数 */
    int countLeaderboardCreators();

    /** 榜单: 创作者总数（本周） */
    int countLeaderboardCreatorsWeekly(@Param("weekStart") String weekStart);
}
