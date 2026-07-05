package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlanHistoryMapper {

    @Insert("INSERT INTO plan_history (account_id, summary, phase1, phase2, phase3, phase4, phase5, phase6) VALUES (#{accountId}, #{summary}, #{phase1}, #{phase2}, #{phase3}, #{phase4}, #{phase5}, #{phase6})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> record);

    @Select("SELECT id, summary, phase1, phase2, phase3, phase4, phase5, phase6, create_time FROM plan_history WHERE account_id = #{accountId} ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> selectByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);

    @Delete("DELETE FROM plan_history WHERE id = #{id} AND account_id = #{accountId}")
    int deleteById(@Param("id") Long id, @Param("accountId") Long accountId);

    @Delete("DELETE FROM plan_history WHERE account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
