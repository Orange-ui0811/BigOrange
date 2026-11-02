package com.orangejd.server.foundation.aitool;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiToolCallLogMapper {
    @Insert("""
            INSERT INTO ai_tool_call_log
                (request_id, attempt_no, account_id, business_type, business_id, purpose,
                 provider, slot_no, key_fingerprint, model, status, started_at)
            VALUES
                (#{requestId}, #{attemptNo}, #{accountId}, #{businessType}, #{businessId}, #{purpose},
                 #{provider}, #{slotNo}, #{keyFingerprint}, #{model}, 'RUNNING', NOW(3))
            """)
    int insert(Map<String, Object> record);

    @Update("""
            UPDATE ai_tool_call_log
            SET status = #{status}, http_status = #{httpStatus}, error_code = #{errorCode},
                error_summary = #{errorSummary}, finished_at = NOW(3),
                duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW(3)) DIV 1000
            WHERE request_id = #{requestId} AND attempt_no = #{attemptNo}
            """)
    int finish(@Param("requestId") String requestId,
               @Param("attemptNo") int attemptNo,
               @Param("status") String status,
               @Param("httpStatus") Integer httpStatus,
               @Param("errorCode") String errorCode,
               @Param("errorSummary") String errorSummary);

    @Select("""
            SELECT log.id, log.request_id AS requestId, log.attempt_no AS attemptNo,
                   log.account_id AS accountId,
                   account.username AS username, profile.nickname AS nickname,
                   COALESCE(NULLIF(profile.nickname, ''), account.username,
                            CONCAT('用户 #', log.account_id), '系统任务') AS userDisplayName,
                   log.business_type AS businessType, log.business_id AS businessId, log.purpose,
                   log.provider, log.slot_no AS slotNo, log.key_fingerprint AS keyFingerprint,
                   log.model, log.status, log.http_status AS httpStatus,
                   log.error_code AS errorCode, log.error_summary AS errorSummary,
                   log.started_at AS startedAt, log.finished_at AS finishedAt,
                   log.duration_ms AS durationMs
            FROM ai_tool_call_log log
            LEFT JOIN account account ON account.id = log.account_id
            LEFT JOIN user_profile profile ON profile.account_id = account.id
            ORDER BY log.id DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> recent(int limit);
}
