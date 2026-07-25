package com.orangejd.server.foundation.task;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SystemTaskMapper {
    @Insert("""
            INSERT INTO system_task
                (task_type, business_key, status, payload_json, max_attempts, next_run_at)
            VALUES
                (#{taskType}, #{businessKey}, 'PENDING', #{payloadJson}, #{maxAttempts}, NOW())
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIdempotent(SystemTask task);

    @Select("""
            SELECT id, task_type, business_key, status, payload_json, attempts, max_attempts, next_run_at
            FROM system_task
            WHERE status IN ('PENDING', 'RETRY_WAIT')
              AND next_run_at <= NOW()
            ORDER BY next_run_at, id
            LIMIT #{limit}
            """)
    List<SystemTask> selectReady(@Param("limit") int limit);

    @Update("""
            UPDATE system_task
            SET status = 'RUNNING',
                locked_by = #{workerId},
                locked_at = NOW(),
                attempts = attempts + 1
            WHERE id = #{id}
              AND status IN ('PENDING', 'RETRY_WAIT')
              AND next_run_at <= NOW()
            """)
    int claim(@Param("id") Long id, @Param("workerId") String workerId);

    @Select("""
            SELECT id, task_type, business_key, status, payload_json, attempts, max_attempts, next_run_at
            FROM system_task
            WHERE id = #{id}
            """)
    SystemTask selectById(@Param("id") Long id);

    @Update("""
            UPDATE system_task
            SET status = 'SUCCEEDED',
                progress = 100,
                result_json = #{resultJson},
                last_error = NULL,
                locked_by = NULL,
                locked_at = NULL,
                complete_time = NOW()
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int complete(@Param("id") Long id, @Param("resultJson") String resultJson);

    @Update("""
            UPDATE system_task
            SET status = #{status},
                last_error = #{error},
                next_run_at = #{nextRunAt},
                locked_by = NULL,
                locked_at = NULL,
                complete_time = CASE WHEN #{status} = 'FAILED' THEN NOW() ELSE NULL END
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int fail(@Param("id") Long id,
             @Param("status") String status,
             @Param("error") String error,
             @Param("nextRunAt") LocalDateTime nextRunAt);

    @Update("""
            UPDATE system_task
            SET status = 'RETRY_WAIT',
                locked_by = NULL,
                locked_at = NULL,
                next_run_at = NOW(),
                last_error = LEFT(CONCAT(COALESCE(last_error, ''), ' [recovered stale lock]'), 2000)
            WHERE status = 'RUNNING'
              AND locked_at < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            """)
    int recoverStale(@Param("minutes") int minutes);

    @Select("""
            SELECT status, COUNT(*) AS count
            FROM system_task
            GROUP BY status
            """)
    List<Map<String, Object>> countByStatus();
}
