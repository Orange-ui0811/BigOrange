package com.orangejd.server.customer;

import org.apache.ibatis.annotations.*;
import java.util.*;

/** Every archive query repeats the owner/deletion predicate; caller IDs are never trusted. */
@Mapper
public interface CustomerContextMapper {
    @Select("SELECT s.id FROM customer_session s WHERE s.id=#{scope.sessionId} AND s.account_id=#{scope.owner} AND s.deleted_at IS NULL")
    Long owned(@Param("scope") CustomerHistoryRecovery.Scope scope);
    @Select("SELECT s.id FROM customer_session s WHERE s.id=#{scope.sessionId} AND s.account_id=#{scope.owner} AND s.deleted_at IS NULL FOR UPDATE")
    Long lockOwned(@Param("scope") CustomerHistoryRecovery.Scope scope);
    @Select("SELECT r.status,c.task_id,c.requirement_version FROM customer_run r JOIN customer_session s ON s.id=r.session_id LEFT JOIN customer_context_run c ON c.run_id=r.id WHERE r.id=#{scope.runId} AND r.session_id=#{scope.sessionId} AND r.account_id=#{scope.owner} AND s.account_id=#{scope.owner} AND s.deleted_at IS NULL")
    Map<String,Object> run(@Param("scope") CustomerHistoryRecovery.Scope scope);
    @Insert("INSERT IGNORE INTO customer_context_run(run_id,session_id,task_id,requirement_version) VALUES(#{scope.runId},#{scope.sessionId},#{scope.taskId},#{scope.requirementVersion})")
    int bind(@Param("scope") CustomerHistoryRecovery.Scope scope);

    String SOURCE_COLUMNS="m.id,m.run_id,m.role,m.content,m.status,c.task_id,c.requirement_version";
    String SOURCE_FROM=" FROM customer_message m JOIN customer_session s ON s.id=m.session_id JOIN customer_run r ON r.id=m.run_id LEFT JOIN customer_context_run c ON c.run_id=m.run_id WHERE m.session_id=#{scope.sessionId} AND s.account_id=#{scope.owner} AND s.deleted_at IS NULL AND m.id &lt;= #{scope.watermark}";
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.id=#{message}</script>")
    Map<String,Object> source(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("message") long message);
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.run_id != #{scope.runId} AND m.role='user' <if test='!otherTasks'>AND (c.task_id=#{scope.taskId} OR c.task_id IS NULL)</if> AND (<foreach collection='terms' item='term' separator=' OR '>INSTR(m.content,#{term}) &gt; 0</foreach>) ORDER BY m.id DESC LIMIT 20</script>")
    List<Map<String,Object>> search(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("terms") List<String> terms,@Param("otherTasks") boolean otherTasks);
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.run_id != #{scope.runId} AND m.role='user' <if test='!otherTasks'>AND (c.task_id=#{scope.taskId} OR c.task_id IS NULL)</if> AND m.id &gt; #{after} ORDER BY m.id DESC LIMIT 8</script>")
    List<Map<String,Object>> subsequentUsers(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("after") long after,@Param("otherTasks") boolean otherTasks);
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.run_id != #{scope.runId} AND r.status='SUCCEEDED' AND c.task_id=#{scope.taskId} AND m.id &gt; #{after} ORDER BY m.id LIMIT 160</script>")
    List<Map<String,Object>> completed(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("after") long after);
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.run_id != #{scope.runId} AND (c.task_id=#{scope.taskId} OR c.task_id IS NULL) ORDER BY m.id DESC LIMIT #{limit}</script>")
    List<Map<String,Object>> recent(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("limit") int limit);
    @Select("<script>SELECT "+SOURCE_COLUMNS+SOURCE_FROM+" AND m.id IN (<foreach collection='ids' item='mid' separator=','>#{mid}</foreach>) ORDER BY m.id</script>")
    List<Map<String,Object>> sources(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("ids") List<Long> ids);
    @Select("SELECT x.* FROM customer_context_summary x JOIN customer_session s ON s.id=x.session_id WHERE x.session_id=#{scope.sessionId} AND s.account_id=#{scope.owner} AND s.deleted_at IS NULL AND x.task_id=#{scope.taskId} AND x.through_message_id <= #{scope.watermark} ORDER BY x.through_message_id DESC LIMIT 12")
    List<Map<String,Object>> summaries(@Param("scope") CustomerHistoryRecovery.Scope scope);
    @Select("SELECT COUNT(*) FROM customer_context_summary WHERE session_id=#{scope.sessionId} AND task_id=#{scope.taskId} AND from_message_id <= #{through} AND through_message_id >= #{from}")
    int overlapping(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("from") long from,@Param("through") long through);
    @Insert("INSERT IGNORE INTO customer_context_summary_lease(session_id,task_id) VALUES(#{scope.sessionId},#{scope.taskId})")
    int ensureLease(@Param("scope") CustomerHistoryRecovery.Scope scope);
    @Update("UPDATE customer_context_summary_lease SET lease_token=#{token},lease_until=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND) WHERE session_id=#{scope.sessionId} AND task_id=#{scope.taskId} AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(3))")
    int claim(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("token") String token);
    @Update("UPDATE customer_context_summary_lease SET lease_token='',lease_until=NULL WHERE session_id=#{scope.sessionId} AND task_id=#{scope.taskId} AND lease_token=#{token}")
    int release(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("token") String token);
    @Select("SELECT COUNT(*) FROM customer_context_summary_lease WHERE session_id=#{scope.sessionId} AND task_id=#{scope.taskId} AND lease_token=#{token} AND lease_until > CURRENT_TIMESTAMP(3)")
    int leased(@Param("scope") CustomerHistoryRecovery.Scope scope,@Param("token") String token);
    @Insert("INSERT INTO customer_context_summary(id,session_id,task_id,requirement_version,from_message_id,through_message_id,source_ids_json,source_hash,summary_text,model,prompt_version) VALUES(#{id},#{sessionId},#{taskId},#{requirementVersion},#{fromMessageId},#{throughMessageId},#{sourceIdsJson},#{sourceHash},#{summary},#{model},'customer-source-summary-v1')")
    int insertSummary(Map<String,Object> row);
}
