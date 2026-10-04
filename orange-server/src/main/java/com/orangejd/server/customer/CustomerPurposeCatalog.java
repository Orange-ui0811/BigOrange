package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reviewed public source facts, never an approval of user constraints or permission to import. */
@Component
public final class CustomerPurposeCatalog {
    private final List<Map<String,Object>> facts;
    private final String version;
    private CustomerSourceFactMapper db;
    private CustomerJson json;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerPurposeCatalog(CustomerJson json,CustomerSourceFactMapper db) { this(json);this.db=db;this.json=json; }
    public CustomerPurposeCatalog(CustomerJson json) {
        try (var in=new ClassPathResource("customer/purpose-evidence.json").getInputStream()) {
            String source=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            var data=json.read(source);
            if(!(data.get("facts") instanceof List<?> rows))throw new IllegalArgumentException("INVALID_PURPOSE_CATALOG");
            List<Map<String,Object>> parsed=new ArrayList<>();
            for(Object row:rows) {
                if(!(row instanceof Map<?,?> value))throw new IllegalArgumentException("INVALID_PURPOSE_CATALOG");
                Map<String,Object> fact=new LinkedHashMap<>();value.forEach((k,v)->fact.put(k.toString(),v));
                validate(fact);parsed.add(Map.copyOf(fact));
            }
            facts=List.copyOf(parsed);version=CustomerStore.hash(source);
        } catch(java.io.IOException ex) { throw new IllegalStateException("PURPOSE_CATALOG_UNAVAILABLE",ex); }
    }
    CustomerPurposeCatalog(List<Map<String,Object>> facts,String version) {
        facts.forEach(CustomerPurposeCatalog::validate);this.facts=List.copyOf(facts);this.version=version;
    }
    private static final class Default { static final CustomerPurposeCatalog INSTANCE=new CustomerPurposeCatalog(new CustomerJson(new ObjectMapper())); }
    static CustomerPurposeCatalog bundled() { return Default.INSTANCE; }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void seedDatabase() {
        if(db==null)return;
        for(var fact:facts) if(db.insert(fact.get("id").toString(),json.write(fact),"REVIEWED")==1)
            db.event(fact.get("id").toString(),1,"REVIEWED",null,"导入 v4.18 已核对的来源证据；不覆盖撤销记录");
    }
    private List<Map<String,Object>> currentFacts() {
        return db==null?facts:db.all().stream().filter(r->"REVIEWED".equals(r.get("status"))).map(r->json.read(r.get("fact_json"))).toList();
    }
    String version() { return db==null?version:CustomerStore.hash(json.write(db.all().stream().filter(r->"REVIEWED".equals(r.get("status"))).map(r->Map.of("id",r.get("id"),"revision",r.get("revision"),"fact",r.get("fact_json"))).toList())); }

    static void validate(Map<String,Object> fact) {
        if(!(fact.get("id") instanceof String id) || !id.matches("[a-zA-Z0-9_-]{1,120}")
            || !(fact.get("repository") instanceof String repo) || !repo.matches("https://(?:github\\.com|gitee\\.com)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
            || !Set.of("catalogText","whatIsIt","description").contains(fact.get("field"))
            || !(fact.get("sourceHash") instanceof String hash) || !hash.matches("[a-f0-9]{64}")
            || !(fact.get("quote") instanceof String quote) || quote.length()<12 || quote.length()>180
            || !(fact.get("relations") instanceof Map<?,?> relations) || relations.isEmpty()
            || !relations.keySet().stream().allMatch(k->k instanceof String s && (Set.of("ai-application","agent","rag").contains(s) || s.matches("topic:[^\\s]{1,80}")))
            || !relations.values().stream().allMatch(Set.of("DIRECT","SUBTOPIC")::contains))
            throw new IllegalArgumentException("INVALID_PURPOSE_CATALOG");
    }
    /** Only a complete, plain domain request qualifies. Extra objects/scenarios require semantic review. */
    private String domain(Map<String,Object> state) {
        if(!state.containsKey("topic"))return null;
        var topics=CustomerTopics.normalize(state.get("topic"));if(topics.size()!=1)return null;
        String topic=compact(topics.getFirst()),original=compact(CustomerPurpose.originalRequest(state));
        original=original.replaceFirst("^(?:可以用于|用于|我想做|想做|方向是|主题是)","");
        if(!original.equals(topic))return null;
        if(CustomerTopics.isAiApplication(topic))return "ai-application";
        if(Set.of("agent","智能体","aiagent","ai智能体").contains(topic))return "agent";
        if(Set.of("rag","检索增强","检索增强生成").contains(topic))return "rag";
        return "topic:"+topic;
    }
    private static String compact(String value) { return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+",""); }
    Map<String,Object> proof(Map<String,Object> project,Map<String,Object> state) {
        return proof(project,state,currentFacts());
    }
    private Map<String,Object> proof(Map<String,Object> project,Map<String,Object> state,List<Map<String,Object>> available) {
        String domain=domain(state);if(domain==null)return Map.of();
        for(var fact:available) {
            if(!CustomerProjects.identity(project).equals(fact.get("repository").toString().toLowerCase(Locale.ROOT)))continue;
            if(!(((Map<?,?>)fact.get("relations")).get(domain) instanceof String relation))continue;
            if(fact.get("reviewedSource") instanceof Map<?,?> source && source.containsKey("versionId")
                && !Objects.toString(source.get("versionId"),"").equals(Objects.toString(project.get("versionId"),"")))continue;
            String raw=Objects.toString(project.get(fact.get("field")),"");
            if(!CustomerStore.hash(raw).equals(fact.get("sourceHash")))continue;
            String quote=fact.get("quote").toString();
            if(!CustomerPurpose.purpose(project).contains(quote))continue;
            return Map.of("key",CustomerPurpose.key(project),"relation",relation,"quote",quote,"catalogId",fact.get("id"));
        }
        return Map.of();
    }
    /** Nominate from the already hard-filtered pool, so ANN truncation cannot hide known source facts. */
    List<Map<String,Object>> nominate(List<Map<String,Object>> eligible,List<Map<String,Object>> nominated,Map<String,Object> state) {
        Map<String,Map<String,Object>> out=new LinkedHashMap<>();
        var available=currentFacts();
        eligible.stream().filter(p->!proof(p,state,available).isEmpty()).limit(3).forEach(p->out.putIfAbsent(CustomerProjects.identity(p),p));
        nominated.forEach(p->out.putIfAbsent(CustomerProjects.identity(p),p));
        return out.values().stream().limit(12).toList();
    }
    List<String> seed(List<Map<String,Object>> candidates,Map<String,Object> state,String policy,CustomerJson json) {
        List<Map<String,Object>> approved=new ArrayList<>(),matches=new ArrayList<>(),views=new ArrayList<>();List<String> ids=new ArrayList<>();
        var available=currentFacts();
        for(var p:candidates) {
            var proof=proof(p,state,available);if(proof.isEmpty())continue;
            approved.add(p);matches.add(proof);ids.add(proof.get("catalogId").toString());
            views.add(Map.of("key",CustomerPurpose.key(p),"purpose",CustomerPurpose.purpose(p)));
        }
        if(!approved.isEmpty())CustomerPurposeMemory.remember(Map.of("matches",matches),Map.of("candidates",views),approved,state,policy);
        return ids;
    }
}
