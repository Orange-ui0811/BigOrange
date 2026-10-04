package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import com.orangejd.server.mapper.CareerProjectMapper;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import java.util.*;
import java.util.regex.*;

@Service
public class CustomerProjects {
    private final BeginnerProjectMapper beginner;
    private final CareerProjectMapper career;
    private final RagBConstraintMatcher matcher;
    private final CustomerCatalogMapper catalog;
    private final CustomerPurposeCatalog purposeCatalog;
    public CustomerProjects(BeginnerProjectMapper beginner,CareerProjectMapper career,RagBConstraintMatcher matcher) { this(beginner,career,matcher,null); }
    public CustomerProjects(BeginnerProjectMapper beginner,CareerProjectMapper career,RagBConstraintMatcher matcher,CustomerCatalogMapper catalog) { this(beginner,career,matcher,catalog,CustomerPurposeCatalog.bundled()); }
    @org.springframework.beans.factory.annotation.Autowired
    public CustomerProjects(BeginnerProjectMapper beginner,CareerProjectMapper career,RagBConstraintMatcher matcher,CustomerCatalogMapper catalog,CustomerPurposeCatalog purposeCatalog) { this.beginner=beginner; this.career=career; this.matcher=matcher;this.catalog=catalog;this.purposeCatalog=purposeCatalog; }
    public List<Map<String,Object>> search(Map<String,Object> state) {
        return search(state,List.of());
    }
    public List<Map<String,Object>> search(Map<String,Object> state,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> related) {
        return select(pool(),state,related);
    }
    List<Map<String,Object>> select(List<Map<String,Object>> pool,Map<String,Object> state,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> related) {
        var sorted=pool.stream().filter(p->explorable(p,state)).peek(p->{
            List<String> gaps=gaps(p,state); p.put("conditionGaps",gaps);
            p.put("matchStatus",gaps.isEmpty()?"CONFIRMED":"NEEDS_VERIFICATION");
            p.put("ranking",ranking(p,state,related));
        }).sorted(Comparator.<Map<String,Object>>comparingInt(p->"CONFIRMED".equals(p.get("matchStatus"))?0:1)
            .thenComparingInt(p->CustomerPurpose.rank(p,state))
            .thenComparingInt(p->Boolean.TRUE.equals(p.get("evidenceAvailable"))?0:1)
            .thenComparing(Comparator.comparingDouble((Map<String,Object> p)->((Number)((Map<?,?>)p.get("ranking")).get("total")).doubleValue()).reversed())
            .thenComparing(p->p.get("sourceRef").toString())).toList();
        Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
        sorted.forEach(p->unique.putIfAbsent(identity(p),p));
        return unique.values().stream().limit(3).peek(p->p.remove("catalogText")).toList();
    }
    List<Map<String,Object>> pool() {
        List<Map<String,Object>> pool=new ArrayList<>();
        for(CareerProjectProfile p:catalog==null?career.selectAllPublished():catalog.careers()) pool.add(card(p));
        for(BeginnerProject p:catalog==null?beginner.selectAllPublished():catalog.beginners()) pool.add(card(p));
        if(catalog!=null) {
            Set<String> known=new HashSet<>();pool.forEach(p->known.add(identity(p)));
            for(var document:catalog.externalDocuments()) {var external=externalCard(document);if(external!=null&&known.add(identity(external)))pool.add(external);}
        }
        if(catalog!=null) {
            Set<String> sources=new HashSet<>();catalog.evidenceSources().forEach(s->sources.add(sourceIdentity(s).toLowerCase(Locale.ROOT)));
            pool.forEach(p->p.put("evidenceAvailable",sources.contains(Objects.toString(p.get("name"),"").toLowerCase(Locale.ROOT)) || sources.contains(Objects.toString(p.get("sourceRef"),"").toLowerCase(Locale.ROOT)) || !"EXTERNAL_PROJECT".equals(p.get("type")) && (sources.contains("beginner:"+p.get("id")) || sources.contains("career:"+p.get("id")))));
        }
        return pool;
    }
    static String identity(Map<String,Object> p) { return Objects.toString(p.get("url"),Objects.toString(p.get("sourceRef"),"unknown")).replaceAll("/+$","").toLowerCase(Locale.ROOT); }
    public Map<String,Object> diagnose(Map<String,Object> state) { return CustomerDiagnostics.evaluate(this,pool(),state); }
    public Map<String,Object> detail(String type,long id,Map<String,Object> state) {
        Map<String,Object> card=load(type,id);
        if(!accepts(card,state)) throw new IllegalArgumentException("PROJECT_INACTIVE_OR_CONSTRAINT_MISMATCH");
        card.remove("catalogText");
        return card;
    }
    public Map<String,Object> exploreDetail(String type,long id,Map<String,Object> state) {
        Map<String,Object> card=load(type,id);
        if(!explorable(card,state)) throw new IllegalArgumentException("PROJECT_INACTIVE_OR_CONSTRAINT_MISMATCH");
        List<String> gaps=gaps(card,state); card.put("conditionGaps",gaps);
        card.remove("catalogText");
        card.put("matchStatus",gaps.isEmpty()?"CONFIRMED":"NEEDS_VERIFICATION"); return card;
    }
    private Map<String,Object> load(String type,long id) {
        Map<String,Object> card;
        if("BEGINNER_PROJECT".equals(type)) { BeginnerProject p=beginner.selectById(id); if(p==null) throw new IllegalArgumentException("PROJECT_NOT_FOUND"); card=card(p); }
        else if("CAREER_PROJECT".equals(type)) { CareerProjectProfile p=career.selectById(id); if(p==null || catalog!=null && catalog.eligibleCareer(id)==0) throw new IllegalArgumentException("PROJECT_NOT_FOUND"); card=card(p); }
        else if("EXTERNAL_PROJECT".equals(type) && catalog!=null) { var document=catalog.externalDocument(id); card=document==null?null:externalCard(document);if(card==null)throw new IllegalArgumentException("PROJECT_NOT_FOUND"); }
        else throw new IllegalArgumentException("INVALID_PROJECT_TYPE");
        return card;
    }
    /** Unknown facts can be explored, but known conflicts and unpublished identities never can. */
    public boolean explorable(Map<String,Object> card,Map<String,Object> state) {
        if("STRICT".equals(state.get("verificationMode"))) return accepts(card,state);
        // Unknown language/duration may be explored; a missing topic match is never positive relevance.
        if(state.containsKey("topic") && !topicMatch(card,state))return false;
        Map<String,Object> known=new LinkedHashMap<>(state);
        for(String field:unknownFields(card,state)) known.remove(field);
        return accepts(card,known);
    }
    private Set<String> unknownFields(Map<String,Object> card,Map<String,Object> state) {
        Set<String> unknown=new LinkedHashSet<>();
        if(state.containsKey("language") && Objects.toString(card.get("language"),"").isBlank()) unknown.add("language");
        if(state.containsKey("difficulty") && Objects.toString(card.get("difficulty"),"").isBlank()) unknown.add("difficulty");
        if(state.containsKey("maxDurationDays") && durationDays((String)card.get("duration"))==null) unknown.add("maxDurationDays");
        if(state.containsKey("minDurationDays") && CustomerDuration.estimate((String)card.get("duration"),false)==null) unknown.add("minDurationDays");
        for(String suffix:List.of("Docker","Database","Secrets","Hardware")) if(Boolean.FALSE.equals(state.get("allow"+suffix)) && !(card.get("requires"+suffix) instanceof Boolean)) unknown.add("allow"+suffix);
        return unknown;
    }
    public List<String> gaps(Map<String,Object> card,Map<String,Object> state) {
        return unknownFields(card,state).stream().map(field->CustomerState.label(field)+"尚未核实，不能确认满足你的要求").toList();
    }
    public boolean accepts(Map<String,Object> p,Map<String,Object> state) {
        if(CustomerProjectFeedback.excluded(p,state))return false;
        if(resourceOnly(p)) return false;
        if(state.get("sourceType") instanceof String type && !type.equals(p.get("type"))) return false;
        if(!Set.of("PUBLISHED","CANDIDATE","DOCUMENTED").contains(Objects.toString(p.get("status"),"")) || Boolean.TRUE.equals(p.get("archived"))) return false;
        Object url=p.get("url"); if(!(url instanceof String u) || !u.matches("https://(?:github\\.com|gitee\\.com)/[^\\s]+")) return false;
        if(state.get("language") instanceof String language) {
            if(!matcher.languageTokens(String.valueOf(p.getOrDefault("language",""))).contains(matcher.canonical(language))) return false;
        }
        for(String key:List.of("Docker","Database","Secrets","Hardware")) {
            if(Boolean.FALSE.equals(state.get("allow"+key)) && !Boolean.FALSE.equals(p.get("requires"+key))) return false;
        }
        if(state.get("maxDurationDays") instanceof Number max) {
            Integer days=durationDays((String)p.get("duration")); if(days==null || days>max.intValue()) return false;
        }
        if(state.get("minDurationDays") instanceof Number min) {
            Integer days=CustomerDuration.estimate((String)p.get("duration"),false); if(days==null || days<min.intValue()) return false;
        }
        if(state.get("difficulty") instanceof String d && !difficulty(d).equals(difficulty(Objects.toString(p.get("difficulty"),"")))) return false;
        if(state.containsKey("topic") && !topicMatch(p,state)) return false;
        return true;
    }
    /** Apply known hard conflicts before the bounded semantic review window. Unknown fields remain marked. */
    List<Map<String,Object>> purposeCandidates(List<Map<String,Object>> pool,Map<String,Object> state,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits,String query) {
        Map<String,Object> constraints=new LinkedHashMap<>(state);constraints.remove("topic");constraints.remove(CustomerPurpose.STATE_KEY);
        var eligible=pool.stream().filter(p->explorable(p,constraints)).toList();
        var nominated=purposeCatalog.nominate(eligible,CustomerPurpose.nominate(eligible,state,hits,query),state);
        if(!nominated.isEmpty())return nominated;
        // Counterfactual-only fallback: final selection still applies every original hard condition.
        // This keeps useful relaxation suggestions possible when no eligible nomination exists.
        return CustomerPurpose.nominate(pool.stream().filter(p->accepts(p,Map.of())).toList(),state,hits,query);
    }
    private boolean topicMatch(Map<String,Object> p,Map<String,Object> state) {
        if(state.containsKey(CustomerPurpose.STATE_KEY)) return CustomerPurpose.matches(p,state);
        Object t=state.get("topic");
        String facts=p.get("name")+" "+p.get("description")+" "+p.get("techStack")+" "+p.getOrDefault("topics","")+" "+p.getOrDefault("catalogTitle","")+" "+p.getOrDefault("catalogText","");
        return CustomerTopics.matches(t,facts);
    }
    private boolean resourceOnly(Map<String,Object> p) {
        String text=Objects.toString(p.get("whatIsIt"),"")+" "+Objects.toString(p.get("description"),"")+" "+Objects.toString(p.get("catalogTitle"),"");
        String intro=Objects.toString(p.get("catalogText"),""); text+=" "+intro.substring(0,Math.min(1800,intro.length()));
        return text.matches("(?si).*(精选.{0,8}(列表|资源|目录)|资源合集|教程合集|资源清单|curated.{0,16}(list|collection)|collection of.{0,16}(resources|tutorials|guides)|compilation of.{0,80}(guides|tutorials|resources)|all algorithms implemented|list of.{0,12}tutorials).*" )
            || Objects.toString(p.get("name"),"").toLowerCase(Locale.ROOT).matches(".*/awesome[-_].*");
    }
    public static String sourceIdentity(String ref) { return ref==null?"":ref.split("\\|",2)[0]; }
    public static boolean ownsEvidence(Map<String,Object> card,String ref) {
        String identity=sourceIdentity(ref);
        if(identity.equalsIgnoreCase(Objects.toString(card.get("sourceRef"),"")) || identity.equalsIgnoreCase(Objects.toString(card.get("name"),"")))return true;
        return Set.of("BEGINNER_PROJECT","CAREER_PROJECT").contains(Objects.toString(card.get("type"),"")) && identity.matches("(beginner|career):"+java.util.regex.Pattern.quote(Objects.toString(card.get("id"),"")));
    }
    Map<String,Object> ranking(Map<String,Object> p,Map<String,Object> s,List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits) {
        int position=0;
        Set<String> seen=new HashSet<>();
        for(var hit:hits) {
            if(!seen.add(sourceIdentity(hit.sourceRef()))) continue;
            position++;
            if(ownsEvidence(p,hit.sourceRef())) break;
        }
        boolean matched=hits.stream().anyMatch(h->ownsEvidence(p,h.sourceRef()));
        double relevance=matched?45/Math.sqrt(Math.max(1,position)):0;
        double quality=p.get("quality") instanceof Number n?Math.max(0,Math.min(100,n.doubleValue()))*.15:0;
        double preference=0;
        boolean novice=Objects.toString(s.get("experience"),"").matches(".*(新手|入门|刚学|零基础).*" );
        String level=difficulty(Objects.toString(p.get("difficulty"),""));
        if(novice) preference=level.equals("入门")?30:level.equals("进阶")?-40:level.equals("中等")?-8:-10;
        if(novice && p.get("beginnerScore") instanceof Number score) preference+=Math.max(0,Math.min(100,score.doubleValue()))*.1;
        if(novice && "LARGE".equalsIgnoreCase(Objects.toString(p.get("codebaseSize"),""))) preference-=15;
        double goalPreference=(CustomerGoals.learning(s.get("goal")) && "BEGINNER_PROJECT".equals(p.get("type"))?15:0)
            +(CustomerGoals.career(s.get("goal")) && "CAREER_PROJECT".equals(p.get("type"))?15:0);
        double uncertainty=unknownFields(p,s).stream().mapToDouble(field->field.equals("language")?20:field.equals("topic")?12:8).sum();
        return Map.of("version","customer-rank-v3","semantic",relevance,"quality",quality,"preference",preference,"goalPreference",goalPreference,"uncertaintyPenalty",uncertainty,"total",relevance+quality+preference+goalPreference-uncertainty,"semanticAvailable",!hits.isEmpty(),"semanticMatched",matched);
    }
    public static String difficulty(String value) {
        return switch(value.toUpperCase(Locale.ROOT)) {
            case "BEGINNER","EASY","SIMPLE","初级","简单","新手","入门" -> "入门";
            case "MEDIUM","INTERMEDIATE","中级","中等" -> "中等";
            case "HARD","ADVANCED","高级","困难","进阶" -> "进阶";
            default -> value;
        };
    }
    public String reason(Map<String,Object> card,Map<String,Object> state) {
        List<String> reasons=new ArrayList<>();
        if(CustomerGoals.learning(state.get("goal")) && "BEGINNER_PROJECT".equals(card.get("type"))) reasons.add("来自学习项目资料，优先结合练手与改造分析");
        if(CustomerGoals.career(state.get("goal")) && "CAREER_PROJECT".equals(card.get("type"))) reasons.add("来自职业项目资料，可结合能力证明与面试讨论点考察");
        if(Objects.toString(state.get("experience"),"").matches(".*(新手|入门|刚学|零基础).*")) {
            String level=difficulty(Objects.toString(card.get("difficulty"),""));
            reasons.add(level.equals("入门")?"资料标记为入门，与当前基础更接近":level.equals("进阶")?"资料标记为进阶，对刚入门用户有明显门槛，建议先阅读或缩小实践范围":"当前基础与项目规模仍需结合具体任务判断");
            if("LARGE".equalsIgnoreCase(Objects.toString(card.get("codebaseSize"),""))) reasons.add("仓库规模较大，不建议从重写整个系统开始；应先限定为运行示例或一个小改造任务");
        }
        if(state.containsKey("goal")) reasons.add("本轮目标："+CustomerGoals.display(state.get("goal")));
        if(state.containsKey("topic")) reasons.add("资料涉及"+CustomerTopics.display(state.get("topic"))+"，具体实践范围见项目说明");
        if(card.containsKey("language")) reasons.add("资料记录主语言为"+card.get("language"));
        if(card.containsKey("duration") && state.containsKey("maxDurationDays")) reasons.add("资料中的实践周期为"+card.get("duration")+"；你的上限为"+state.get("maxDurationDays")+"天");
        reasons.add("NEEDS_VERIFICATION".equals(card.get("matchStatus"))?"明确冲突已排除，仍有条件待核实":"已通过当前已知字段的条件检查，周期仅为资料估算");
        return String.join("；",reasons)+"。";
    }

    public static Integer durationDays(String value) {
        return CustomerDuration.estimate(value);
    }
    private Map<String,Object> card(BeginnerProject p) {
        Map<String,Object> v=base("BEGINNER_PROJECT",p.getId(),p.getFullName(),p.getHtmlUrl(),p.getStatus(),p.getArchived(),p.getPrimaryLanguage(),p.getTechStack(),p.getDifficulty(),p.getEstimatedDuration(),p.getDescriptionZh());
        put(v,"requiresDocker",p.getRequiresDocker()); put(v,"requiresDatabase",p.getRequiresDatabase()); put(v,"requiresSecrets",p.getRequiresSecrets());
        put(v,"quality",p.getEditorialScore()); put(v,"topics",p.getTopicsJson());
        put(v,"beginnerScore",p.getBeginnerScore()); put(v,"codebaseSize",p.getCodebaseSize());
        put(v,"whatIsIt",p.getWhatIsIt()); put(v,"analysisReason",p.getBeginnerReason());
        put(v,"modificationPlan",p.getModificationIdeas()); put(v,"mainRisks",p.getMainRisks());
        put(v,"vibeCodingReason",p.getFinalRecommendation()); v.put("prerequisites",prerequisites(v)); return v;
    }
    private Map<String,Object> externalCard(Map<String,Object> document) {
        String repository=sourceIdentity(Objects.toString(document.get("source_ref"),""));
        if(!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || Arrays.stream(repository.split("/")).anyMatch(part->part.equals(".")||part.equals("..")) || Set.of("topics","collections","marketplace","features","settings","orgs","users").contains(repository.split("/")[0].toLowerCase(Locale.ROOT)))return null;
        Map<String,Object> v=base("EXTERNAL_PROJECT",CustomerStore.positiveId(document.get("id")),repository,"https://github.com/"+repository,"DOCUMENTED",false,null,null,null,null,"已抓取的仓库资料；主语言、周期、难度与资源要求尚未形成可核实的结构化字段。");
        v.put("sourceRef",repository);v.put("documentId",document.get("id"));v.put("versionId",document.get("active_version_id"));v.put("evidenceStatus","DOCUMENT_IDENTITY");
        v.put("catalogTitle",Objects.toString(document.get("title"),repository));
        v.put("catalogText",Objects.toString(document.get("topic_text"),""));
        var language=CustomerSourceFacts.explicitLanguage(v.get("catalogText").toString());
        if(!language.isEmpty()) {
            v.put("language",language.get("language"));v.put("languageEvidence",language);
            v.put("description","已抓取的仓库资料；主语言有简介原文依据，周期、难度与资源要求仍需核实。");
        }
        return v;
    }
    private Map<String,Object> card(CareerProjectProfile p) {
        Map<String,Object> v=base("CAREER_PROJECT",p.getProjectId(),p.getFullName(),p.getHtmlUrl(),p.getStatus(),p.getArchived(),p.getPrimaryLanguage(),p.getTechStack(),p.getDifficulty(),p.getEstimatedDuration(),p.getSummaryZh());
        put(v,"requiresDocker",p.getRequiresDocker()); put(v,"requiresDatabase",p.getRequiresDatabase()); put(v,"requiresSecrets",p.getRequiresSecrets());
        put(v,"quality",p.getOverallScore()); put(v,"analysisReason",p.getCareerValue());
        put(v,"proofPoints",p.getProofPoints()); put(v,"interviewTopics",p.getInterviewTopics());
        put(v,"modificationPlan",p.getModificationPlan()); put(v,"mainRisks",p.getMainRisks());
        v.put("prerequisites",prerequisites(v)); return v;
    }
    private Map<String,Object> base(String type,Long id,String name,String url,String status,Boolean archived,String language,String stack,String difficulty,String duration,String description) {
        Map<String,Object> v=new LinkedHashMap<>(); v.put("type",type); v.put("id",id); v.put("sourceRef",(type.equals("BEGINNER_PROJECT")?"beginner:":"career:")+id);
        put(v,"name",name); put(v,"url",url); put(v,"status",status); put(v,"archived",archived); put(v,"language",language); put(v,"techStack",stack);
        put(v,"difficulty",difficulty==null?null:difficulty(difficulty)); put(v,"duration",duration); put(v,"description",description); v.put("evidenceStatus","VERIFIED_DATABASE"); v.put("importable",false); return v;
    }
    private String prerequisites(Map<String,Object> card) {
        List<String> facts=new ArrayList<>();
        for(String key:List.of("Docker","Database","Secrets")) {
            Object required=card.get("requires"+key);
            String label=switch(key) {case "Database"->"数据库";case "Secrets"->"密钥";default->"Docker";};
            if(required instanceof Boolean value) facts.add(label+(value?"：需要":"：资料标记不需要"));
        }
        if(facts.isEmpty()) return "资源与环境要求尚未核实，需查看仓库部署说明。";
        return String.join("；",facts)+"。其他环境要求需核对仓库。";
    }
    private void put(Map<String,Object> v,String key,Object value) { if(value!=null) v.put(key,value); }
}
