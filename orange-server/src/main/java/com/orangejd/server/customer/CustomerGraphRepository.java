package com.orangejd.server.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Each operation borrows and returns a connection through Spring. No model call holds a transaction. */
@Repository
public class CustomerGraphRepository {
    private final JdbcTemplate jdbc;
    public CustomerGraphRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void begin(String run) {
        jdbc.update("INSERT INTO customer_graph_execution(run_id,thread_id,graph_version,state_schema_version,status) VALUES(?,?,'customer-graph-v2',2,'RUNNING')",run,"customer:"+run);
    }
    public void checkpoint(String run,String id,String node,String next,String state) {
        jdbc.update("INSERT INTO customer_graph_checkpoint(run_id,checkpoint_id,node_id,next_node_id,state_json) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE state_json=VALUES(state_json),node_id=VALUES(node_id),next_node_id=VALUES(next_node_id)",run,id,node,next,state);
    }
    public List<Map<String,Object>> checkpoints(String run) {
        return jdbc.queryForList("SELECT * FROM customer_graph_checkpoint WHERE run_id=? ORDER BY sequence_id DESC",run);
    }
    public void archive(String run,String status,String error) {
        jdbc.update("UPDATE customer_graph_execution SET status=?,error_code=?,finished_at=CURRENT_TIMESTAMP(3) WHERE run_id=?",status,error,run);
    }
    public void nodeStart(String id,String run,String engine,String node) {
        jdbc.update("INSERT INTO customer_node_execution(id,run_id,execution_engine,node_id,status) VALUES(?,?,?,?,'RUNNING')",id,run,engine,node);
    }
    public void nodeEnd(String id,String status,String next,long elapsed,String error,String summary) {
        jdbc.update("UPDATE customer_node_execution SET status=?,next_node_id=?,duration_ms=?,error_code=?,summary_json=?,finished_at=CURRENT_TIMESTAMP(3) WHERE id=?",status,next,elapsed,error,summary,id);
    }
    public List<Map<String,Object>> nodes(String run) {return jdbc.queryForList("SELECT * FROM customer_node_execution WHERE run_id=? ORDER BY created_at,id",run);}
    public void payload(String id,String run,String node,String kind,String content) {
        jdbc.update("INSERT INTO customer_raw_payload(id,run_id,node_execution_id,kind,content_json) VALUES(?,?,?,?,?)",id,run,node,kind,content);
    }
    public void technicalStep(String run,String kind,String content) {
        jdbc.update("INSERT INTO customer_step(run_id,tool_name,status,detail_json) VALUES(?,?,'OBSERVED',?)",run,kind,content);
    }
    public List<Map<String,Object>> payloads(String run) {
        return jdbc.queryForList("SELECT id,node_execution_id,kind,created_at FROM customer_raw_payload WHERE run_id=? AND created_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY ORDER BY created_at,id",run);
    }
    @Transactional
    public Map<String,Object> raw(long actor,String run,String payload,String reason) {
        var rows=jdbc.queryForList("SELECT id,kind,content_json,created_at FROM customer_raw_payload WHERE id=? AND run_id=? AND created_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY",payload,run);
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"原文不存在或已过期");
        jdbc.update("INSERT INTO customer_raw_access_audit(actor_id,run_id,payload_id,reason) VALUES(?,?,?,?)",actor,run,payload,reason);
        return rows.getFirst();
    }
    public Map<String,Object> runs(String run,String engine,String status,String outcome,String from,String until,int offset,int limit) {
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();
        for(var entry:List.of(new String[]{"id",run},new String[]{"execution_engine",engine},new String[]{"status",status})) {
            if(entry[1]!=null && !entry[1].isBlank()){where.append(" AND ").append(entry[0]).append("=?");args.add(entry[1]);}
        }
        if(outcome!=null && !outcome.isBlank()){where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.businessOutcome'))=?");args.add(outcome);}
        if(from!=null && !from.isBlank()){where.append(" AND created_at>=?");args.add(from);}
        if(until!=null && !until.isBlank()){where.append(" AND created_at<?");args.add(until);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM customer_run"+where,Long.class,args.toArray());
        args.add(limit);args.add(offset);
        var rows=jdbc.queryForList("SELECT id,execution_engine,status,stage,error_code,graph_version,prompt_version,generation_key,created_at,finished_at,JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.businessOutcome')) AS business_outcome FROM customer_run"+where+" ORDER BY created_at DESC LIMIT ? OFFSET ?",args.toArray());
        return Map.of("items",rows,"total",Objects.requireNonNullElse(total,0L));
    }
    /** Whitelisted tables, bounded batches, and only terminal/deleted sessions. */
    public int cleanup() {
        int count=0;
        for(String table:List.of("customer_graph_checkpoint","customer_raw_payload","customer_node_execution","customer_graph_execution")) {
            int days=table.equals("customer_node_execution") || table.equals("customer_graph_execution")?90:30;
            count+=jdbc.update("DELETE FROM "+table+" WHERE (created_at < CURRENT_TIMESTAMP - INTERVAL "+days+" DAY OR run_id IN (SELECT r.id FROM customer_run r JOIN customer_session s ON s.id=r.session_id WHERE s.deleted_at IS NOT NULL)) AND run_id IN (SELECT id FROM customer_run WHERE status NOT IN ('QUEUED','RUNNING')) LIMIT 500");
        }
        count+=jdbc.update("DELETE FROM customer_raw_access_audit WHERE created_at < CURRENT_TIMESTAMP - INTERVAL 90 DAY LIMIT 500");
        return count;
    }
}
