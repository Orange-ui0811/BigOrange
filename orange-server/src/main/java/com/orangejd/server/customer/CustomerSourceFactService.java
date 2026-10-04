package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Source review is explicit and auditable. Text extraction can only propose a draft. */
@Service
public class CustomerSourceFactService {
    private final CustomerSourceFactMapper db;
    private final CustomerJson json;
    private final CustomerProjects projects;
    public CustomerSourceFactService(CustomerSourceFactMapper db,CustomerJson json,CustomerProjects projects) { this.db=db;this.json=json;this.projects=projects; }
    public List<Map<String,Object>> list() { return db.all(); }
    public List<Map<String,Object>> events(String id) { return db.events(id); }
    @Transactional public String propose(Map<String,Object> fact) {
        CustomerPurposeCatalog.validate(fact);
        String id=fact.get("id").toString();
        if(db.insert(id,json.write(fact),"DRAFT")==1) db.event(id,1,"DRAFT",null,"原文提取草稿，尚未核实语义关系");
        return id;
    }
    @Transactional public void review(String id,long revision,String status,long actor,String reason) {
        if(actor<=0 || !Set.of("REVIEWED","REVOKED").contains(status) || reason==null || reason.isBlank() || reason.length()>500) throw new IllegalArgumentException("INVALID_FACT_REVIEW");
        var row=db.lock(id);if(row==null || CustomerStore.number(row,"revision")!=revision)throw new IllegalArgumentException("FACT_REVISION_CONFLICT");
        var fact=json.read(row.get("fact_json"));
        if("REVIEWED".equals(status) && projects.pool().stream().noneMatch(p->current(fact,p)))throw new IllegalArgumentException("FACT_SOURCE_STALE");
        if(db.transition(id,revision,status,actor)!=1)throw new IllegalArgumentException("FACT_REVISION_CONFLICT");
        db.event(id,revision+1,status,actor,reason);
    }
    static boolean current(Map<String,Object> fact,Map<String,Object> p) {
        if(!CustomerProjects.identity(p).equals(Objects.toString(fact.get("repository"),"").toLowerCase(Locale.ROOT)))return false;
        if(fact.get("reviewedSource") instanceof Map<?,?> source && source.containsKey("versionId")
            && !Objects.toString(source.get("versionId"),"").equals(Objects.toString(p.get("versionId"),"")))return false;
        String raw=Objects.toString(p.get(fact.get("field")),"");
        return CustomerStore.hash(raw).equals(fact.get("sourceHash")) && CustomerPurpose.purpose(p).contains(Objects.toString(fact.get("quote"),""));
    }
    @Transactional public int markStale(List<Map<String,Object>> pool) {
        int count=0;
        for(var row:db.all()) {
            if(!"REVIEWED".equals(row.get("status")))continue;
            var fact=json.read(row.get("fact_json"));if(pool.stream().anyMatch(p->current(fact,p)))continue;
            long revision=CustomerStore.number(row,"revision");String id=row.get("id").toString();
            if(db.transition(id,revision,"STALE",null)==1) { db.event(id,revision+1,"STALE",null,"来源已变更、下架或删除，需要重新核对");count++; }
        }
        return count;
    }
}
