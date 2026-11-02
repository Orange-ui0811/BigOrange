package com.orangejd.server.customer;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerMapper {
    @Select("SELECT id FROM customer_execution_lock WHERE id=1 FOR UPDATE") int lock();
    @Insert("INSERT INTO customer_session(account_id,title,state_json) VALUES(#{accountId},#{title},'{}')")
    @Options(useGeneratedKeys=true,keyProperty="id") int createSession(Map<String,Object> row);
    @Select("SELECT * FROM customer_session WHERE id=#{id} AND account_id=#{owner} AND deleted_at IS NULL")
    Map<String,Object> session(@Param("id") long id,@Param("owner") long owner);
    @Select("SELECT * FROM customer_session WHERE id=#{id} AND account_id=#{owner}")
    Map<String,Object> sessionIncludingDeleted(@Param("id") long id,@Param("owner") long owner);
    @Update("UPDATE customer_session SET deleted_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND account_id=#{owner} AND deleted_at IS NULL")
    int deleteSession(@Param("id") long id,@Param("owner") long owner);
    @Delete("DELETE FROM customer_graph_checkpoint WHERE run_id IN (SELECT id FROM customer_run WHERE session_id=#{session} AND status NOT IN ('QUEUED','RUNNING'))")
    int deleteSessionCheckpoints(long session);
    @Delete("DELETE FROM customer_raw_payload WHERE run_id IN (SELECT id FROM customer_run WHERE session_id=#{session} AND status NOT IN ('QUEUED','RUNNING'))")
    int deleteSessionPayloads(long session);
    @Select("SELECT id,title,state_version,updated_at FROM customer_session WHERE account_id=#{owner} AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 100")
    List<Map<String,Object>> sessions(long owner);
    @Select("SELECT * FROM customer_run WHERE session_id=#{session} AND request_id=#{request}")
    Map<String,Object> replay(@Param("session") long session,@Param("request") String request);
    @Select("SELECT * FROM customer_run WHERE id=#{id}") Map<String,Object> run(String id);
    @Select("SELECT COUNT(*) FROM customer_run WHERE status IN ('QUEUED','RUNNING')") int active();
    @Select("SELECT COUNT(*) FROM customer_run WHERE account_id=#{owner} AND status IN ('QUEUED','RUNNING')") int activeOwner(long owner);
    @Select("SELECT id,status,stage FROM customer_run WHERE session_id=#{session} AND status IN ('QUEUED','RUNNING') LIMIT 1") Map<String,Object> activeSession(long session);
    @Insert("INSERT INTO customer_run(id,session_id,account_id,request_id,input_hash,message,status,stage,prompt_version,deadline_at,execution_engine,graph_version,state_schema_version,trace_id) VALUES(#{id},#{session},#{owner},#{request},#{hash},#{message},'QUEUED','ACCEPTED',#{promptVersion},#{deadline},#{executionEngine},#{graphVersion},2,#{traceId})")
    int insertRun(Map<String,Object> row);
    @Insert("INSERT INTO customer_message(session_id,run_id,role,content,status) VALUES(#{session},#{run},#{role},#{content},#{status})")
    int message(@Param("session") long session,@Param("run") String run,@Param("role") String role,@Param("content") String content,@Param("status") String status);
    @Select("SELECT m.*,r.metrics_json,r.error_code,r.created_at AS run_created_at,r.finished_at AS run_finished_at, TIMESTAMPDIFF(MICROSECOND,r.created_at,r.finished_at)/1000 AS run_duration_ms FROM customer_message m LEFT JOIN customer_run r ON r.id=m.run_id WHERE m.session_id=#{session} AND m.id < #{before} ORDER BY m.id DESC LIMIT #{limit}")
    List<Map<String,Object>> messages(@Param("session") long session,@Param("before") long before,@Param("limit") int limit);
    @Update("UPDATE customer_run SET status='RUNNING',stage=#{stage} WHERE id=#{id} AND status IN ('QUEUED','RUNNING')")
    int stage(@Param("id") String id,@Param("stage") String stage);
    @Update("UPDATE customer_run SET status=#{status},stage=#{status},result_json=#{result},metrics_json=#{metrics},error_code=#{error},finished_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status IN ('QUEUED','RUNNING')")
    int finish(@Param("id") String id,@Param("status") String status,@Param("result") String result,@Param("metrics") String metrics,@Param("error") String error);
    @Update("UPDATE customer_run SET metrics_json=#{metrics} WHERE id=#{id}") int metrics(@Param("id") String id,@Param("metrics") String metrics);
    @Update("UPDATE customer_session SET state_json=#{state},state_version=state_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    int state(@Param("id") long id,@Param("state") String state);
    @Update("UPDATE customer_session SET state_json=#{state},state_version=state_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND state_version=#{expected}")
    int stateCas(@Param("id") long id,@Param("state") String state,@Param("expected") long expected);
    @Update("UPDATE customer_session SET summary_text=#{summary},summary_through=#{through} WHERE id=#{id}")
    int summary(@Param("id") long id,@Param("summary") String summary,@Param("through") long through);
    @Insert("INSERT INTO customer_step(run_id,tool_name,status,detail_json) VALUES(#{run},#{tool},#{status},#{detail})")
    int step(@Param("run") String run,@Param("tool") String tool,@Param("status") String status,@Param("detail") String detail);
    @Select("SELECT * FROM customer_step WHERE run_id=#{run} ORDER BY id") List<Map<String,Object>> steps(String run);
    @Update("UPDATE customer_run SET generation_key=#{generation} WHERE id=#{id}")
    int generation(@Param("id") String id,@Param("generation") String generation);
    @Select("SELECT id FROM customer_run WHERE status IN ('QUEUED','RUNNING')") List<String> unfinished();
}
