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

    int countActiveAdmins();

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    java.util.List<Long> selectActiveAdminIdsForUpdate();

    java.util.List<java.util.Map<String, Object>> selectTopCreators(@Param("limit") int limit);

    /** 榜单: 全时段创作者收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardCreators(@Param("limit") int limit);

    /** 榜单: 本周创作者收藏排行 */
    java.util.List<java.util.Map<String, Object>> selectLeaderboardCreatorsWeekly(@Param("weekStart") String weekStart, @Param("limit") int limit);

    /** 榜单: 创作者总数 */
    int countLeaderboardCreators();

    /** 榜单: 创作者总数（本周） */
    int countLeaderboardCreatorsWeekly(@Param("weekStart") String weekStart);

    /** 配额: FOR UPDATE 锁行查询 */
    Account selectByIdForUpdate(@Param("id") Long id);

    /** 配额: 原子扣减（返回受影响行数，0=余额不足） */
    int decreaseQuota(@Param("id") Long id, @Param("amount") long amount);

    /** 配额: 原子增加 */
    int increaseQuota(@Param("id") Long id, @Param("amount") long amount);

    /** 配额: 管理端直接设置（审计用） */
    int setQuota(@Param("id") Long id, @Param("quota") Long quota);

    /** 按邮箱查询 */
    Account selectByEmail(@Param("email") String email);
}
