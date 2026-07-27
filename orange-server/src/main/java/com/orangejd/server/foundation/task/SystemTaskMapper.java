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
                (run_id, task_type, business_key, status, payload_json, config_version,
                 max_attempts, next_run_at)
            VALUES
                (#{runId}, #{taskType}, #{businessKey}, 'PENDING', #{payloadJson}, #{configVersion},
                 #{maxAttempts}, NOW())
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIdempotent(SystemTask task);

    @Select("""
            SELECT id, run_id, task_type, business_key, status, payload_json, config_version,
                   attempts, max_attempts, next_run_at, cancel_requested
            FROM system_task
            WHERE status IN ('PENDING', 'RETRY_WAIT')
              AND cancel_requested = 0
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
                lease_until = DATE_ADD(NOW(), INTERVAL 120 SECOND),
                attempts = attempts + 1
            WHERE id = #{id}
              AND status IN ('PENDING', 'RETRY_WAIT')
              AND next_run_at <= NOW()
            """)
    int claim(@Param("id") Long id, @Param("workerId") String workerId);

    @Select("""
            SELECT id, run_id, task_type, business_key, status, payload_json, config_version,
                   attempts, max_attempts, next_run_at, cancel_requested
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
                lease_until = NULL,
                complete_time = NOW()
            WHERE id = #{id} AND status = 'RUNNING' AND cancel_requested = 0
            """)
    int complete(@Param("id") Long id, @Param("resultJson") String resultJson);

    @Update("""
            UPDATE system_task
            SET status = CASE WHEN cancel_requested = 1 THEN 'CANCELLED' ELSE #{status} END,
                last_error = #{error},
                next_run_at = #{nextRunAt},
                locked_by = NULL,
                locked_at = NULL,
                lease_until = NULL,
                complete_time = CASE WHEN cancel_requested = 1 OR #{status} = 'FAILED' THEN NOW() ELSE NULL END
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
                lease_until = NULL,
                next_run_at = NOW(),
                last_error = LEFT(CONCAT(COALESCE(last_error, ''), ' [recovered stale lock]'), 2000)
            WHERE status = 'RUNNING'
              AND lease_until < NOW()
              AND cancel_requested = 0
            """)
    int recoverStale();

    @Update("""
            UPDATE system_task
            SET locked_at = NOW(),
                lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND)
            WHERE status = 'RUNNING' AND locked_by = #{workerId} AND cancel_requested = 0
            """)
    int heartbeatWorker(@Param("workerId") String workerId, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE system_task
            SET cancel_requested = 1,
                status = CASE WHEN status IN ('PENDING', 'RETRY_WAIT') THEN 'CANCELLED' ELSE status END,
                complete_time = CASE WHEN status IN ('PENDING', 'RETRY_WAIT') THEN NOW() ELSE complete_time END
            WHERE run_id = #{runId} AND status IN ('PENDING', 'RETRY_WAIT', 'RUNNING')
            """)
    int requestCancelByRun(@Param("runId") Long runId);

    @Select("SELECT COUNT(*) FROM system_task WHERE run_id = #{runId} AND status = 'RUNNING'")
    int countRunningByRun(@Param("runId") Long runId);

    @Update("""
            UPDATE system_task
            SET status = 'CANCELLED', complete_time = NOW(), locked_by = NULL,
                locked_at = NULL, lease_until = NULL
            WHERE id = #{id} AND status = 'RUNNING' AND cancel_requested = 1
            """)
    int markCancelled(@Param("id") Long id);

    @Select("""
            SELECT status, COUNT(*) AS count
            FROM system_task
            GROUP BY status
            """)
    List<Map<String, Object>> countByStatus();
}
