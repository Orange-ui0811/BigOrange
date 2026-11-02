package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CustomerStore {
    private final CustomerMapper db;
    private final CustomerJson json;
    private final CustomerProperties config;
    public CustomerStore(CustomerMapper db, CustomerJson json, CustomerProperties config) { this.db=db; this.json=json; this.config=config; }
    public void access(Long owner) {
        if(owner == null || owner <= 0) fail(HttpStatus.UNAUTHORIZED,"请先登录");
        if(!config.canAccess(owner)) fail(HttpStatus.FORBIDDEN,"客服暂不可用");
    }
    public Map<String,Object> session(long id,long owner) {
        access(owner); Map<String,Object> row=db.session(id,owner);
        if(row==null || !(row.get("account_id") instanceof Number account) || account.longValue()!=owner) fail(HttpStatus.NOT_FOUND,"会话不存在");
        return row;
    }
    public Map<String,Object> run(String id,long owner) {
        access(owner); Map<String,Object> row=db.run(id);
        if(row==null || !(row.get("account_id") instanceof Number account) || account.longValue()!=owner) fail(HttpStatus.NOT_FOUND,"运行不存在");
        session(number(row,"session_id"),owner);
        return row;
    }
    @Transactional
    public void deleteSession(long id,long owner) {
        access(owner); db.lock();
        Map<String,Object> row=db.sessionIncludingDeleted(id,owner);
        if(row==null || !(row.get("account_id") instanceof Number account) || account.longValue()!=owner) fail(HttpStatus.NOT_FOUND,"会话不存在");
        if(row.get("deleted_at")!=null) return;
        if(active(db.activeSession(id))) fail(HttpStatus.CONFLICT,"请先停止当前回答，再删除会话");
        db.deleteSession(id,owner);
        db.deleteSessionCheckpoints(id);db.deleteSessionPayloads(id);
    }
    public List<Map<String,Object>> sessions(long owner) { access(owner); return db.sessions(owner); }
    public Map<String,Object> create(long owner,String title) {
        access(owner); Map<String,Object> row=new LinkedHashMap<>(); row.put("accountId",owner);
        row.put("title", title==null || title.isBlank()?"项目推荐咨询":title.strip().substring(0,Math.min(120,title.strip().length())));
        db.createSession(row); return session(number(row,"id"),owner);
    }
    public Map<String,Object> history(long id,long owner,long before,int limit) {
        Map<String,Object> session=session(id,owner);
        List<Map<String,Object>> rows=new ArrayList<>(db.messages(id,before,Math.max(1,Math.min(100,limit))));
        Collections.reverse(rows);
        Map<String,Object> value=new LinkedHashMap<>(); value.put("session",session); value.put("messages",rows);
        value.put("state",json.read(session.get("state_json"))); value.put("activeRun",db.activeSession(id)); return value;
    }
    @Transactional
    public Map<String,Object> submit(long session,long owner,String request,String message) {
        return submit(session,owner,request,message,Map.of());
    }
    @Transactional
    public Map<String,Object> submit(long session,long owner,String request,String message,Map<String,Object> binding) {
        access(owner);
        if(request==null || !request.matches("[a-zA-Z0-9_-]{8,80}")) fail(HttpStatus.BAD_REQUEST,"请求标识无效");
        if(message==null || message.isBlank() || message.length()>2000) fail(HttpStatus.BAD_REQUEST,"消息长度须为1至2000字符");
        Objects.requireNonNull(binding,"binding");
        if(!Set.of("taskId","questionId","listId").containsAll(binding.keySet()) || binding.values().stream().anyMatch(v->!(v instanceof String s)||s.length()>100))fail(HttpStatus.BAD_REQUEST,"消息关联无效");
        String hash=hash(binding.isEmpty()?message:message+"\n"+json.write(new TreeMap<>(binding))); db.lock();var current=session(session,owner);
        Map<String,Object> old=db.replay(session,request);
        if(old!=null) {
            if(!hash.equals(old.get("input_hash"))) fail(HttpStatus.CONFLICT,"相同请求标识的内容不一致");
            Map<String,Object> replay=new LinkedHashMap<>(old); replay.put("replayed",true); return replay;
        }
        var state=json.read(current.get("state_json"));var task=CustomerWorkflow.task(state);
        if(binding.containsKey("taskId")&&!Objects.equals(task.get("id"),binding.get("taskId")))fail(HttpStatus.CONFLICT,"任务已切换，请刷新会话");
        if(binding.containsKey("questionId")&&!Objects.equals(CustomerWorkflow.question(state).get("id"),binding.get("questionId")))fail(HttpStatus.CONFLICT,"该问题已结束，请回答当前问题");
        if(binding.containsKey("listId")&&CustomerWorkflow.rows(task.get("lists")).stream().noneMatch(l->Objects.equals(l.get("id"),binding.get("listId"))))fail(HttpStatus.CONFLICT,"候选列表不属于当前任务");
        if(db.activeOwner(owner)>0) fail(HttpStatus.CONFLICT,"请等待当前账号的回答结束");
        if(db.active()>=config.getMaxConcurrent()+config.getMaxQueued()) fail(HttpStatus.CONFLICT,"客服等待队列已满，请稍后再试");
        Map<String,Object> row=new LinkedHashMap<>(); row.put("id",UUID.randomUUID().toString()); row.put("session",session);
        row.put("owner",owner); row.put("request",request); row.put("hash",hash); row.put("message",message);
        row.put("promptVersion",CustomerConversation.PROMPT_VERSION);
        row.put("executionEngine",config.getExecutionEngine().name());
        row.put("graphVersion",config.getExecutionEngine()==CustomerExecutionEngine.Kind.LANGGRAPH4J?"customer-graph-v2":null);
        row.put("traceId",UUID.randomUUID().toString());
        row.put("deadline",LocalDateTime.now().plusSeconds(config.getRunSeconds()));
        db.insertRun(row); db.message(session,row.get("id").toString(),"user",message,"COMPLETED");
        if(!binding.isEmpty())db.step(row.get("id").toString(),"inputContext","ACCEPTED",json.write(binding));
        return new LinkedHashMap<>(db.run(row.get("id").toString()));
    }
    @Transactional
    public void checkpoint(String id,Map<String,Object> state) {
        db.lock(); Map<String,Object> run=db.run(id);
        if(active(run)) saveState(number(run,"session_id"),state);
    }
    @Transactional
    public void complete(String id,Map<String,Object> result,Map<String,Object> state,Object metrics) {
        db.lock(); Map<String,Object> run=db.run(id);
        if(!active(run)) return;
        long session=number(run,"session_id");saveState(session,state);
        if(db.finish(id,"SUCCEEDED",json.write(result),json.write(metrics),null)==1) {
            db.message(session,id,"assistant",json.write(result),"COMPLETED");
        }
    }
    private void saveState(long session,Map<String,Object> state) {
        if(state.get("_expectedStateVersion") instanceof Number expected) {
            var saved=new LinkedHashMap<>(state);saved.remove("_expectedStateVersion");
            if(db.stateCas(session,json.write(saved),expected.longValue())!=1)throw new IllegalStateException("STALE_SESSION_REVISION");
            state.put("_expectedStateVersion",expected.longValue()+1);
        } else db.state(session,json.write(state));
    }
    @Transactional
    public void terminal(String id,String status,String error,Object metrics) {
        db.lock(); Map<String,Object> row=db.run(id); if(!active(row)) return;
        String response=json.write(Map.of("status",status,"errorCode",error,"answer",CustomerRecovery.message(status,error),"recoverable",true,"projects",List.of(),"references",List.of()));
        db.finish(id,status,response,json.write(metrics),error);
        db.message(number(row,"session_id"),id,"assistant",response,status);
    }
    public static boolean active(Map<String,Object> row) { return row!=null && ("RUNNING".equals(row.get("status")) || "QUEUED".equals(row.get("status"))); }
    public static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static long positiveId(Object value) {
        long id;
        if(value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) id=((Number)value).longValue();
        else if(value instanceof String text && text.matches("[1-9][0-9]{0,18}")) {
            try { id=Long.parseLong(text); } catch(NumberFormatException ex) { throw new IllegalArgumentException("INVALID_ID"); }
        } else throw new IllegalArgumentException("INVALID_ID");
        if(id<=0) throw new IllegalArgumentException("INVALID_ID"); return id;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    private void fail(HttpStatus status,String text) { throw new ResponseStatusException(status,text); }
}
