package com.orangejd.server.service;

import com.orangejd.pojo.dto.TrendProfileDTO;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import com.orangejd.server.ragb.service.RagBSearchService;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;
import com.orangejd.server.foundation.external.AiRuntimeConfig;

/** Development experiment, without account profiles, quota, history writes or route switching. */
@Service
@Profile("dev")
public class TrendRagABPreviewService {
    private final TrendContextBuilder context;
    private final RagBSearchService search;
    private final TrendV2Analyzer analyzer;
    private final AiRuntimeConfigService runtime;
    private final TrendModelClient modelClient;
    private final Map<String,Frozen> frozen=new LinkedHashMap<>();
    private record Frozen(Map<String,Object> body,String knowledge,AiRuntimeConfig runtime,long createdAt) {}
    @org.springframework.beans.factory.annotation.Autowired
    public TrendRagABPreviewService(TrendContextBuilder context, RagBSearchService search,
                                    TrendV2Analyzer analyzer, AiRuntimeConfigService runtime,TrendModelClient modelClient) {
        this.context=context; this.search=search; this.analyzer=analyzer; this.runtime=runtime;this.modelClient=modelClient;
    }
    TrendRagABPreviewService(TrendContextBuilder c,RagBSearchService s,TrendV2Analyzer a,AiRuntimeConfigService r) { this(c,s,a,r,null); }
    public synchronized Map<String,Object> preview(String variant,TrendProfileDTO p,boolean generate,boolean observe) {
        if(!observe) return preview(variant,p,generate);
        try(var observation=TrendLabObservation.open()) {
            var result=preview(variant,p,generate);
            result.put("labStages",observation.stages);
            if(observation.request!=null) {
                String id=UUID.randomUUID().toString();
                var retrieval=(TrendContextBuilder.Retrieval)result.get("retrieval");
                frozen.put(id,new Frozen(observation.request,retrieval.text(),runtime.current(AiRuntimeConfigService.PROJECT),System.currentTimeMillis()));
                while(frozen.size()>32) frozen.remove(frozen.keySet().iterator().next());
                result.put("snapshotId",id); result.put("modelRequest",observation.request);
            }
            return result;
        }
    }
    public synchronized Map<String,Object> replay(String snapshotId,boolean removeKnowledge) {
        var snapshot=frozen.get(snapshotId);
        if(snapshot==null || System.currentTimeMillis()-snapshot.createdAt()>7_200_000) throw new IllegalArgumentException("实验快照不存在或已过期");
        var config=runtime.current(AiRuntimeConfigService.PROJECT);
        if(!Objects.equals(config.version(),snapshot.runtime().version()) || !Objects.equals(config.model(),snapshot.runtime().model()) || !Objects.equals(config.endpoint(),snapshot.runtime().endpoint())) throw new IllegalStateException("模型配置已变化，不能重放");
        Map<String,Object> body=new LinkedHashMap<>(snapshot.body());
        if(removeKnowledge) body.put("messages",withoutKnowledge(snapshot.body(),snapshot.knowledge()));
        var completion=modelClient.completeMeasured(config,body);
        return Map.of("snapshotId",snapshotId,"removeKnowledge",removeKnowledge,"modelRequest",body,"modelMetrics",completion.metrics(),"content",completion.content(),"scope","Frozen synthetic prompt replay; one model request; no retrieval/business/history/quota");
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> withoutKnowledge(Map<String,Object> body,String knowledge) {
        return ((List<Map<String,Object>>)body.get("messages")).stream().map(message->{
            Map<String,Object> copy=new LinkedHashMap<>(message);
            if("user".equals(copy.get("role")) && !knowledge.isEmpty()) copy.put("content",copy.get("content").toString().replace("\n\n## 知识库参考\n"+knowledge,""));
            return copy;
        }).toList();
    }
    public synchronized Map<String,Object> preview(String variant, TrendProfileDTO p, boolean generate) {
        if (!Set.of("A","B").contains(variant) || p == null) throw new IllegalArgumentException("需要 A/B 和测试条件");
        if (Boolean.TRUE.equals(p.getUsePersona())) throw new IllegalArgumentException("实验仅允许合成画像");
        TrendAnalysisInput input=new TrendAnalysisInput(p.getTopics(),p.getPurpose(),p.getExperience(),p.getLanguages(),
                p.getTimeCommitment(),p.getCustomTopics(),p.getCustomPurpose(),p.getCustomExp(),p.getCustomLang(),
                p.getCustomTime(),true,false,p.getPreferBeginnerProjects(),p.getPreferCareerProjects());
        if (TrendContextBuilder.retrievalQueries(input).stream().anyMatch(q->q.length()>2000))
            throw new IllegalArgumentException("测试条件过长");
        long started=System.nanoTime();
        var retrieval=TrendLabObservation.measure("retrieval.total", () -> "A".equals(variant)?context.retrieve(input):retrieveB(input));
        Map<String,Object> result=new LinkedHashMap<>();
        var config=runtime.current(AiRuntimeConfigService.PROJECT);
        result.put("runtime",Map.of("model",config.model(),"endpoint",config.endpoint(),"version",config.version(),"source",config.source()));
        result.put("variant",variant);
        result.put("retrieval",retrieval);
        if (generate) result.put("result",analyzer.preview(input,config,retrieval));
        result.put("wallMs",(System.nanoTime()-started)/1_000_000.0);
        result.put("scope","Production TrendV2Analyzer generation/parser/assembler/binder; synthetic profile; no controller quota/history/router retry; B adapted to SAME 3-project/1100-character evidence budget. No global route change.");
        return result;
    }
    TrendContextBuilder.Retrieval retrieveB(TrendAnalysisInput input) {
        long started=System.nanoTime();
        List<String> queries=TrendContextBuilder.retrievalQueries(input);
        if (queries.isEmpty()) return new TrendContextBuilder.Retrieval("",new LinkedHashMap<>(Map.of(
                "status","NO_TOPIC","evidence",List.of(),"durationMs",0)));
        // The same two query intents as A. B internally performs dense + lexical fusion.
        Map<String,Document> documents=new LinkedHashMap<>();
        Map<String,Double> scores=new HashMap<>();
        Map<String,List<RagBSearchHit>> passages=new LinkedHashMap<>();
        List<Object> routes=new ArrayList<>();
        var constraints=new RagBSearchConstraints(Set.of(),Set.of(),null,null,null,null,null,
                Boolean.TRUE.equals(input.preferBeginnerProjects()),Boolean.TRUE.equals(input.preferCareerProjects()));
        for (int route=0;route<queries.size();route++) {
            String query=queries.get(route);
            var found=TrendLabObservation.measure("B.native.total", () -> search.retrieve(query,8,.55,constraints));
            routes.add(Map.of("query",queries.get(route),"generation",found.generation(),"status",found.status(),
                    "hits",found.hits(),"excludedReasons",found.excludedReasons(),"answerPolicy",found.answerPolicy()));
            long filteringStarted=System.nanoTime();
            Set<String> ranked=new HashSet<>();
            for (int rank=0;rank<found.hits().size();rank++) {
                var hit=found.hits().get(rank);
                Document doc=document(hit);
                String url=TrendEvidenceBinder.repositoryUrl(doc);
                if (url==null || !TrendTopicGate.accepts(doc,input)) continue;
                if (ranked.add(url)) scores.merge(url,(route==0?.55:.25)/(21+rank),Double::sum);
                documents.putIfAbsent(url,doc);
                var group=passages.computeIfAbsent(url,ignored->new ArrayList<>());
                if (group.stream().noneMatch(old->old.chunkId()==hit.chunkId())) group.add(hit);
            }
            TrendLabObservation.elapsed("B.adapterTopicGateAndFusion",filteringStarted);
        }
        List<String> urls=scores.keySet().stream().sorted(Comparator.<String>comparingDouble(scores::get).reversed()
                .thenComparing(Comparator.naturalOrder())).limit(3).toList();
        List<Document> selected=urls.stream().map(url->new Document(documents.get(url).getId(),
                passages.get(url).stream().limit(3).map(RagBSearchHit::content).reduce((a,b)->a+"\n\n"+b).orElse(""),
                documents.get(url).getMetadata())).toList();
        var rendered=TrendLabObservation.measure("B.renderEvidence", () -> TrendEvidenceCardFormatter.render(selected));
        List<Map<String,Object>> evidence=new ArrayList<>();
        for (int i=0;i<rendered.cards().size();i++) {
            var doc=selected.get(i);
            Map<String,Object> item=new LinkedHashMap<>(doc.getMetadata());
            item.put("evidenceId","E"+(i+1)); item.put("repositoryUrl",urls.get(i));
            item.put("text",doc.getText());item.put("card",rendered.cards().get(i));
            item.put("chunks",passages.get(urls.get(i))); evidence.add(item);
        }
        Map<String,Object> trace=new LinkedHashMap<>();
        trace.put("strategyVersion","b-trend-adapter-v1-equal-budget");trace.put("status",evidence.isEmpty()?"EMPTY":"USED");
        trace.put("queries",queries);trace.put("routes",routes);trace.put("evidence",evidence);
        trace.put("selectedCount",evidence.size());trace.put("candidateCount",scores.size());
        trace.put("durationMs",(System.nanoTime()-started)/1_000_000.0);
        trace.put("evidencePromptChars",rendered.renderedChars());trace.put("evidenceBudgetChars",1100);
        return new TrendContextBuilder.Retrieval(rendered.text(),trace);
    }
    static Document document(RagBSearchHit hit) {
        Map<String,Object> meta=new LinkedHashMap<>();
        meta.put("source",hit.sourceType());meta.put("sourceRef",Objects.toString(hit.sourceRef(),""));
        meta.put("docTitle",Objects.toString(hit.title(),""));meta.put("docId",hit.documentId());
        meta.put("chunkIndex",hit.chunkIndex());
        // Resolve only source identity, never guess the repository from links in README content.
        String source=Objects.toString(hit.sourceRef(),"").split("\\|",2)[0];
        String canonical=TrendEvidenceBinder.canonical(source);
        if (canonical!=null) meta.put("repositoryUrl",canonical);
        return new Document("b:"+hit.chunkId(),Objects.toString(hit.content(),""),meta);
    }
}
