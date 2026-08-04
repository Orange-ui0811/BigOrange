package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlanHistoryMapper {

    @Insert("""
            INSERT INTO plan_history
                (account_id, content_hash, summary, phase1, phase2, phase3, phase4, phase5, phase6)
            VALUES
                (#{accountId}, #{contentHash}, #{summary}, #{phase1}, #{phase2}, #{phase3},
                 #{phase4}, #{phase5}, #{phase6})
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> record);

    @Update("""
            UPDATE plan_history
            SET content_hash = #{contentHash}, summary = #{summary},
                phase1 = #{phase1}, phase2 = #{phase2}, phase3 = #{phase3},
                phase4 = #{phase4}, phase5 = #{phase5}, phase6 = #{phase6}
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    int updateOwned(Map<String, Object> record);

    @Update("""
            UPDATE plan_history
            SET phase6 = #{phase6}, content_hash = NULL
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    int updatePhase6Owned(@Param("id") Long id, @Param("accountId") Long accountId,
                          @Param("phase6") String phase6);

    @Select("""
            SELECT id, summary, phase1, phase2, phase3, phase4, phase5, phase6,
                   create_time AS createTime
            FROM plan_history
            WHERE account_id = #{accountId}
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> selectByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);

    @Delete("DELETE FROM plan_history WHERE id = #{id} AND account_id = #{accountId}")
    int deleteById(@Param("id") Long id, @Param("accountId") Long accountId);

    @Delete("DELETE FROM plan_history WHERE account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
