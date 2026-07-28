package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;
import java.util.List;

@Mapper
public interface AgentActionMapper {

    @Insert("""
            INSERT INTO agent_action_task
                (id, account_id, credential_id, action_key, parameters_json, action_summary,
                 status, quota_cost, balance_snapshot, result_json, spoken_summary, expires_at)
            VALUES
                (#{id}, #{accountId}, #{credentialId}, #{actionKey}, #{parametersJson},
                 #{actionSummary}, #{status}, #{quotaCost}, #{balance}, #{resultJson},
                 #{spokenSummary}, #{expiresAt})
            """)
    int insert(Map<String, Object> task);

    @Select("""
            SELECT id, account_id AS accountId, credential_id AS credentialId,
                   action_key AS actionKey, parameters_json AS parametersJson,
                   action_summary AS actionSummary, status, quota_cost AS quotaCost,
                   balance_snapshot AS balance, result_json AS resultJson,
                   spoken_summary AS spokenSummary, error_code AS errorCode,
                   expires_at AS expiresAt, started_at AS startedAt, finished_at AS finishedAt,
                   create_time AS createTime, update_time AS updateTime
            FROM agent_action_task
            WHERE id = #{id} AND account_id = #{accountId} AND credential_id = #{credentialId}
            """)
    Map<String, Object> selectOwned(@Param("id") String id, @Param("accountId") Long accountId,
                                    @Param("credentialId") Long credentialId);

    @Select("""
            SELECT id, account_id AS accountId, credential_id AS credentialId,
                   action_key AS actionKey, parameters_json AS parametersJson,
                   action_summary AS actionSummary, status, quota_cost AS quotaCost,
                   balance_snapshot AS balance, result_json AS resultJson,
                   spoken_summary AS spokenSummary, error_code AS errorCode,
                   expires_at AS expiresAt, started_at AS startedAt, finished_at AS finishedAt,
                   create_time AS createTime, update_time AS updateTime
            FROM agent_action_task
            WHERE account_id = #{accountId} AND credential_id = #{credentialId}
            ORDER BY create_time DESC LIMIT 1
            """)
    Map<String, Object> selectLatest(@Param("accountId") Long accountId,
                                     @Param("credentialId") Long credentialId);

    @Select("""
            SELECT id, account_id AS accountId, credential_id AS credentialId,
                   action_key AS actionKey, parameters_json AS parametersJson,
                   action_summary AS actionSummary, status, quota_cost AS quotaCost,
                   balance_snapshot AS balance, result_json AS resultJson,
                   spoken_summary AS spokenSummary, error_code AS errorCode,
                   create_time AS createTime, update_time AS updateTime
            FROM agent_action_task
            WHERE account_id = #{accountId} AND credential_id = #{credentialId}
              AND action_key = #{actionKey} AND status = 'SUCCEEDED'
            ORDER BY create_time DESC LIMIT 1
            """)
    Map<String, Object> selectLatestSucceeded(@Param("accountId") Long accountId,
                                               @Param("credentialId") Long credentialId,
                                               @Param("actionKey") String actionKey);

    @Select("""
            SELECT id, credential_id AS credentialId, action_key AS actionKey,
                   action_summary AS actionSummary, status, quota_cost AS quotaCost,
                   balance_snapshot AS balance, spoken_summary AS spokenSummary,
                   result_json AS resultJson, error_code AS errorCode, expires_at AS expiresAt,
                   started_at AS startedAt, finished_at AS finishedAt,
                   create_time AS createTime, update_time AS updateTime
            FROM agent_action_task
            WHERE account_id = #{accountId}
            ORDER BY create_time DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> selectByAccount(@Param("accountId") Long accountId,
                                               @Param("limit") int limit);

    @Update("""
            UPDATE agent_action_task SET status = 'QUEUED', spoken_summary = #{spokenSummary}
            WHERE id = #{id} AND status = 'PREPARED' AND expires_at > NOW()
            """)
    int markQueued(@Param("id") String id, @Param("spokenSummary") String spokenSummary);

    @Update("""
            UPDATE agent_action_task SET status = 'RUNNING', started_at = NOW(),
                spoken_summary = #{spokenSummary}
            WHERE id = #{id} AND status = 'QUEUED'
            """)
    int markRunning(@Param("id") String id, @Param("spokenSummary") String spokenSummary);

    @Update("""
            UPDATE agent_action_task SET status = 'SUCCEEDED', result_json = #{resultJson},
                balance_snapshot = #{balance}, spoken_summary = #{spokenSummary},
                error_code = NULL, finished_at = NOW()
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int markSucceeded(@Param("id") String id, @Param("resultJson") String resultJson,
                      @Param("balance") long balance, @Param("spokenSummary") String spokenSummary);

    @Update("""
            UPDATE agent_action_task SET status = 'FAILED', spoken_summary = #{spokenSummary},
                error_code = #{errorCode}, finished_at = NOW()
            WHERE id = #{id} AND status IN ('QUEUED', 'RUNNING')
            """)
    int markFailed(@Param("id") String id, @Param("errorCode") String errorCode,
                   @Param("spokenSummary") String spokenSummary);

    @Update("""
            UPDATE agent_action_task SET status = 'CANCELLED',
                spoken_summary = '任务已取消。', finished_at = NOW()
            WHERE id = #{id} AND account_id = #{accountId} AND credential_id = #{credentialId}
              AND status IN ('PREPARED', 'QUEUED')
            """)
    int cancel(@Param("id") String id, @Param("accountId") Long accountId,
               @Param("credentialId") Long credentialId);

    @Update("""
            UPDATE agent_action_task SET status = 'EXPIRED',
                spoken_summary = '确认已过期，请重新发起操作。', finished_at = NOW()
            WHERE id = #{id} AND status = 'PREPARED' AND expires_at <= NOW()
            """)
    int expire(String id);

    @Update("""
            UPDATE agent_action_task SET status = 'FAILED', error_code = 'SERVER_RESTARTED',
                spoken_summary = '任务因 OrangeJD 服务重启而中断，请在网页核对结果和配额记录。',
                finished_at = NOW()
            WHERE status IN ('QUEUED', 'RUNNING')
            """)
    int markInterruptedAfterRestart();

    @Delete("""
            DELETE FROM agent_action_task
            WHERE id = #{id} AND account_id = #{accountId}
              AND status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')
            """)
    int deleteTerminalByAccount(@Param("id") String id, @Param("accountId") Long accountId);
}
