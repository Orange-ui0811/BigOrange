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
                (#{requestId}, 1, #{accountId}, #{businessType}, #{businessId}, #{purpose},
                 #{provider}, #{slotNo}, #{keyFingerprint}, #{model}, 'RUNNING', NOW(3))
            """)
    int insert(Map<String, Object> record);

    @Update("""
            UPDATE ai_tool_call_log
            SET status = #{status}, http_status = #{httpStatus}, error_code = #{errorCode},
                error_summary = #{errorSummary}, finished_at = NOW(3),
                duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW(3)) DIV 1000
            WHERE request_id = #{requestId} AND attempt_no = 1
            """)
    int finish(@Param("requestId") String requestId,
               @Param("status") String status,
               @Param("httpStatus") Integer httpStatus,
               @Param("errorCode") String errorCode,
               @Param("errorSummary") String errorSummary);

    @Select("""
            SELECT id, request_id AS requestId, account_id AS accountId,
                   business_type AS businessType, business_id AS businessId, purpose,
                   provider, slot_no AS slotNo, key_fingerprint AS keyFingerprint,
                   model, status, http_status AS httpStatus, error_code AS errorCode,
                   error_summary AS errorSummary, started_at AS startedAt,
                   finished_at AS finishedAt, duration_ms AS durationMs
            FROM ai_tool_call_log
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> recent(int limit);
}
