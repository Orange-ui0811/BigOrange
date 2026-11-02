package com.orangejd.server.customer;

import com.orangejd.server.foundation.task.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;

/** Reuses the platform's durable task queue; never invokes a model or approves extracted facts. */
@Component
public class CustomerFactRefresh implements SystemTaskHandler {
    private final CustomerProjects projects;
    private final CustomerSourceFactService facts;
    private final SystemTaskService tasks;
    private final CustomerProperties config;
    public CustomerFactRefresh(CustomerProjects projects,CustomerSourceFactService facts,SystemTaskService tasks,CustomerProperties config) { this.projects=projects;this.facts=facts;this.tasks=tasks;this.config=config; }
    @Override public String taskType() { return "CUSTOMER_SOURCE_FACT_REFRESH"; }
    @Scheduled(initialDelay=60000,fixedDelay=300000) public void schedule() { if(config.isEnabled())enqueue(); }
    public Long enqueue() { return tasks.enqueue(taskType(),"customer-facts:"+(System.currentTimeMillis()/300000),Map.of(),3); }
    @Override public Object execute(SystemTask task) {
        if(!config.isEnabled())return Map.of("status","DISABLED");
        var pool=projects.pool();int stale=facts.markStale(pool),drafts=0;
        for(var p:pool) {
            var proposal=proposal(p);if(proposal.isEmpty())continue;
            facts.propose(proposal);drafts++;
        }
        return Map.of("status","COMPLETED","scanned",pool.size(),"draftsConsidered",drafts,"stale",stale,"modelCalls",0);
    }
    static Map<String,Object> proposal(Map<String,Object> p) {
        for(String field:List.of("whatIsIt","description","catalogText")) {
            String text=Objects.toString(p.get(field),"");
            for(String sentence:text.split("[\\n。!?！？]")) {
                String quote=sentence.strip();if(quote.length()<12 || quote.length()>180)continue;
                String lower=quote.toLowerCase(Locale.ROOT);
                String domain=lower.matches("(?s).*\\brag\\b.*") || quote.contains("检索增强")?"rag":lower.matches("(?s).*\\bagents?\\b.*") || quote.contains("智能体")?"agent":quote.contains("AI应用") || lower.contains("ai application")?"ai-application":null;
                if(domain==null)continue;
                String hash=CustomerStore.hash(text),repo=CustomerProjects.identity(p);
                if(!repo.matches("https://(?:github\\.com|gitee\\.com)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))continue;
                Map<String,Object> fact=new LinkedHashMap<>(Map.of("id","draft-"+CustomerStore.hash(repo+field+hash+Objects.toString(p.get("versionId"),"")),"repository",repo,"field",field,"sourceHash",hash,"quote",quote,"relations",Map.of(domain,"DIRECT"),"reviewMethod","UNREVIEWED_EXTRACT"));
                if(p.containsKey("versionId"))fact.put("reviewedSource",Map.of("versionId",p.get("versionId")));
                return fact;
            }
        }
        return Map.of();
    }
}
