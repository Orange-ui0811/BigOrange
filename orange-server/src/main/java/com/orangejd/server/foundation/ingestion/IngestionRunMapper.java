package com.orangejd.server.foundation.ingestion;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface IngestionRunMapper {
    @Insert("""
            INSERT IGNORE INTO ingestion_run
                (pipeline_type, trigger_source, business_key, config_type, config_version)
            VALUES
                (#{pipelineType}, #{triggerSource}, #{businessKey}, #{configType}, #{configVersion})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIdempotent(IngestionRun run);

    @Select("""
            SELECT id, pipeline_type, trigger_source, business_key, config_type, config_version,
                   status, total_count, success_count, failed_count, skipped_count, progress,
                   cancel_requested, error_summary, started_at, completed_at, create_time, update_time
            FROM ingestion_run
            WHERE pipeline_type = #{pipelineType} AND business_key = #{businessKey}
            """)
    IngestionRun selectByBusinessKey(@Param("pipelineType") String pipelineType,
                                     @Param("businessKey") String businessKey);

    @Select("""
            SELECT id, pipeline_type, trigger_source, business_key, config_type, config_version,
                   status, total_count, success_count, failed_count, skipped_count, progress,
                   cancel_requested, error_summary, started_at, completed_at, create_time, update_time
            FROM ingestion_run
            WHERE id = #{id}
            """)
    IngestionRun selectById(Long id);

    @Select("""
            <script>
            SELECT id, pipeline_type, trigger_source, business_key, config_type, config_version,
                   status, total_count, success_count, failed_count, skipped_count, progress,
                   cancel_requested, error_summary, started_at, completed_at, create_time, update_time
            FROM ingestion_run
            <where>
              <if test="pipelineType != null and pipelineType != ''">pipeline_type = #{pipelineType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IngestionRun> selectPage(@Param("pipelineType") String pipelineType,
                                  @Param("status") String status,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM ingestion_run
            <where>
              <if test="pipelineType != null and pipelineType != ''">pipeline_type = #{pipelineType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("pipelineType") String pipelineType, @Param("status") String status);

    @Update("""
            UPDATE ingestion_run SET status = 'RUNNING', started_at = COALESCE(started_at, NOW())
            WHERE id = #{id} AND status IN ('PENDING', 'RETRY_WAIT')
            """)
    int markRunning(Long id);

    @Update("""
            UPDATE ingestion_run
            SET status = #{status}, progress = #{progress}, total_count = #{total},
                success_count = #{success}, failed_count = #{failed}, skipped_count = #{skipped},
                error_summary = #{error}, completed_at = CASE
                    WHEN #{status} IN ('SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED') THEN NOW()
                    ELSE completed_at END
            WHERE id = #{id}
            """)
    int updateProgress(@Param("id") Long id, @Param("status") String status,
                       @Param("progress") int progress, @Param("total") int total,
                       @Param("success") int success, @Param("failed") int failed,
                       @Param("skipped") int skipped, @Param("error") String error);

    @Update("UPDATE ingestion_run SET cancel_requested = 1 WHERE id = #{id}")
    int requestCancel(Long id);

    @Update("""
            UPDATE ingestion_run
            SET status = 'CANCELLED', cancel_requested = 1, progress = 100,
                error_summary = #{reason}, completed_at = NOW()
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markSuperseded(@Param("id") Long id, @Param("reason") String reason);

    @Insert("""
            INSERT INTO ingestion_run_item (run_id, stage, item_key, status, attempts, result_json, last_error, complete_time)
            VALUES (#{runId}, #{stage}, #{itemKey}, #{status}, 1, CAST(#{resultJson} AS JSON), #{error},
                    CASE WHEN #{status} IN ('SUCCEEDED', 'FAILED', 'SKIPPED') THEN NOW() ELSE NULL END)
            ON DUPLICATE KEY UPDATE status = VALUES(status), attempts = attempts + 1,
                result_json = VALUES(result_json), last_error = VALUES(last_error),
                complete_time = VALUES(complete_time)
            """)
    int upsertItem(@Param("runId") Long runId, @Param("stage") String stage,
                   @Param("itemKey") String itemKey, @Param("status") String status,
                   @Param("resultJson") String resultJson, @Param("error") String error);

    @Select("""
            SELECT id, run_id AS runId, stage, item_key AS itemKey, status, attempts,
                   result_json AS resultJson, last_error AS lastError,
                   create_time AS createTime, update_time AS updateTime, complete_time AS completeTime
            FROM ingestion_run_item WHERE run_id = #{runId} ORDER BY id
            """)
    List<Map<String, Object>> selectItems(Long runId);

    @Select("SELECT run_id FROM ingestion_pipeline_lock WHERE pipeline_key = #{pipelineKey} AND lease_until > NOW()")
    Long selectActiveLock(String pipelineKey);

    @Insert("""
            INSERT INTO ingestion_pipeline_lock (pipeline_key, run_id, owner_id, lease_until)
            VALUES (#{pipelineKey}, #{runId}, #{ownerId}, DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND))
            ON DUPLICATE KEY UPDATE
              run_id = IF(lease_until <= NOW(), VALUES(run_id), run_id),
              owner_id = IF(lease_until <= NOW(), VALUES(owner_id), owner_id),
              lease_until = IF(lease_until <= NOW(), VALUES(lease_until), lease_until)
            """)
    int acquireLock(@Param("pipelineKey") String pipelineKey, @Param("runId") Long runId,
                    @Param("ownerId") String ownerId, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE ingestion_pipeline_lock
            SET lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND)
            WHERE run_id = #{runId}
            """)
    int heartbeatLock(@Param("runId") Long runId, @Param("leaseSeconds") int leaseSeconds);

    @Delete("DELETE FROM ingestion_pipeline_lock WHERE run_id = #{runId}")
    int releaseLock(Long runId);
}
