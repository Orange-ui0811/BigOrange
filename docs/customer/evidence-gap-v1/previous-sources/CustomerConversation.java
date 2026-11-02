package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class CustomerConversation {
    public static final String PROMPT_VERSION="customer-v5.0-workflow-v2";
    public static final Set<String> SKILLS=Set.of("KNOWLEDGE_QA_V1","REQUIREMENTS_V1","PROJECT_RECOMMENDATION_V1");
    final CustomerStore store;
    final CustomerMapper db;
    final CustomerJson json;
    final CustomerModel model;
    final CustomerTools tools;
    final CustomerProjects projects;
    final CustomerProperties config;
    final CustomerPurposeCatalog purposeCatalog;
    CustomerContextSupport contextSupport;
    @org.springframework.beans.factory.annotation.Autowired
    public void contextEngineering(CustomerContextSupport support) {this.contextSupport=support;}
    public CustomerConversation(CustomerStore s,CustomerMapper d,CustomerJson j,CustomerModel m,CustomerTools t,CustomerProjects p,CustomerProperties c) { this(s,d,j,m,t,p,c,CustomerPurposeCatalog.bundled()); }
    @org.springframework.beans.factory.annotation.Autowired
    public CustomerConversation(CustomerStore s,CustomerMapper d,CustomerJson j,CustomerModel m,CustomerTools t,CustomerProjects p,CustomerProperties c,CustomerPurposeCatalog pc) { store=s; db=d; json=j; model=m; tools=t; projects=p; config=c; purposeCatalog=pc; }
    /** Compatibility entry for existing integration fixtures. Production commits only in CustomerRuns. */
    public void execute(String id,List<Map<String,Object>> usage,Instant deadline) {
        var out=prepare(new CustomerRunContext(id,deadline,usage));
        store.complete(id,out.result(),out.state(),metrics(usage));
    }
    public CustomerExecutionResult prepare(CustomerRunContext input) {
        Turn turn=newTurn(input);
        for(int count=0;!"END".equals(turn.next);count++) {
            if(count>=32)throw new IllegalStateException("GRAPH_NODE_LIMIT");
            turn.step(turn.next);
        }
        return turn.output();
    }
    public Turn newTurn(CustomerRunContext input) { return new Turn(input); }
    public final class Turn {
        final CustomerRecommendationSteps recommendationSteps=new CustomerRecommendationSteps(CustomerConversation.this);
        final CustomerEvidenceSteps evidenceSteps=new CustomerEvidenceSteps(CustomerConversation.this);
        final String id; final Instant deadline;final List<Map<String,Object>> usage;
        long owner,sessionId;
        Map<String,Object> session,prior,action,state,result,generated,reviewInput;
        Map<String,Object> binding=Map.of();Object messageId;
        CustomerReferenceResolver.Resolution resolution;
        CustomerTools.Context context;CustomerHistory.View history;
        CustomerContextSupport.Memory memory;
        String message,profile,preamble,changes,intent,policy,purposeQuery,evidenceQuestion;
        String next="load_context";boolean projectMode,recommendation,evidenceFailed,reviewRecovered;
        List<Map<String,Object>> rejected,candidates=List.of(),cards=List.of(),evidence=List.of(),claims=List.of(),references=List.of();
        List<com.orangejd.server.ragb.retrieval.RagBSearchHit> related=List.of(),hits=List.of();
        com.orangejd.server.ragb.service.RagBSearchService.SearchResult found;
        Instant evidenceDeadline;
        Turn(CustomerRunContext input) {id=input.runId();deadline=input.deadline();usage=input.usage();}
        public String next() {return next;}
        public CustomerExecutionResult output() { if(!"END".equals(next))throw new IllegalStateException("GRAPH_INCOMPLETE");return new CustomerExecutionResult(result,state);}
        public void check() {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("RUN_STOPPED");
            if(!Instant.now().isBefore(deadline))throw new IllegalStateException("DEADLINE_EXCEEDED");
            if(context!=null)context.check();
        }
        public Map<String,Object> snapshot() {
            Map<String,Object> out=new LinkedHashMap<>();out.put("runId",id);out.put("sessionId",sessionId);out.put("next",next);
            out.put("graphVersion","customer-graph-v2");out.put("stateSchemaVersion",2);
            if(state!=null)out.put("workflowTask",CustomerWorkflow.visible(state));
            if(intent!=null)out.put("intent",intent);
            if(state!=null)out.put("conditions",CustomerRecommendationAdvice.conditions(state));
            if(prior!=null)out.put("priorConditions",CustomerRecommendationAdvice.conditions(prior));
            out.put("candidatePoolCount",candidates.size());out.put("selectedCount",cards.size());
            out.put("recalledChunkCount",related.size());out.put("preparedEvidenceCount",evidence.size());
            if(state!=null)out.put("purposeStatus",Objects.toString(CustomerPurpose.status(state),"NOT_REQUIRED"));
            if(context!=null){out.put("toolCalls",context.calls);out.put("knowledgeSearches",context.searches);}
            out.put("candidateIdentities",cards.stream().map(CustomerProjectFeedback::identity).toList());
            out.put("evidenceIds",references.stream().map(r->Objects.toString(r.get("evidenceId"),"")).toList());
            if(found!=null)out.put("generation",found.generation());
            if(result!=null)out.put("businessStatus",result.getOrDefault("status","PENDING"));
            return json.read(json.write(out));
        }
        public void step(String node) {
            check();
            try {
                switch(node) {
                    case "load_context" -> {load();next="resolve_references";}
                    case "resolve_references" -> {resolution=CustomerReferenceResolver.resolve(message,prior,binding);if(contextSupport!=null)contextSupport.recover(this);next="understand_turn";}
                    case "understand_turn" -> {understand();next="reconcile_conditions";}
                    case "reconcile_conditions" -> {reconcile();next=branch();}
                    case "direct_response" -> {direct();next="finalize_response";}
                    case "diagnose" -> {diagnose(state,context,result);next="finalize_response";}
                    case "retrieve_knowledge" -> {evidenceSteps.retrieveKnowledge(this);next=found!=null && !result.containsKey("answer")?"prepare_evidence":"finalize_response";}
                    case "plan_recommendation" -> {recommendationSteps.plan(this);next="recall_candidates";}
                    case "recall_candidates" -> {recommendationSteps.recall(this);next="review_purpose";}
                    case "review_purpose" -> {recommendationSteps.review(this);next=Set.of("RELEVANCE_UNAVAILABLE","PURPOSE_REVIEW_PARTIAL").contains(Objects.toString(result.get("status"),""))?"finalize_response":"select_projects";}
                    case "select_projects" -> {recommendationSteps.select(this);next=cards.isEmpty()?"finalize_response":"retrieve_project_evidence";}
                    case "load_project_details" -> {recommendationSteps.details(this);next=cards.isEmpty()?"finalize_response":"retrieve_project_evidence";}
                    case "retrieve_project_evidence" -> {evidenceSteps.retrieveProjectEvidence(this);next="prepare_evidence";}
                    case "prepare_evidence" -> {evidenceSteps.prepareEvidence(this);next=projectMode && hits.isEmpty()?"assemble_project_cards":"generate_claims";}
                    case "generate_claims" -> {generated=model.call(CustomerEvidence.PROMPT,Map.of("question",evidenceQuestion,"evidence",CustomerEvidence.promptEvidence(evidence)),evidenceDeadline,usage);next="validate_citations";}
                    case "validate_citations" -> {evidenceSteps.validateCitations(this);next="verify_claims";}
                    case "verify_claims" -> {evidenceSteps.verifyClaims(this);next="assemble_project_cards";}
                    case "assemble_project_cards" -> {if(projectMode)evidenceSteps.assemble(this);else evidenceSteps.finishQa(this);next=projectMode?"match_requirements":"finalize_response";}
                    case "match_requirements" -> {CustomerRequirementMatcher.match(CustomerConversation.this,this);next=cards.isEmpty()?"finalize_response":"generate_advice";}
                    case "generate_advice" -> {if(recommendation){recommendationAdvice(cards,state,context,result,deadline,usage);recommendationSteps.finishRecommendation(this);}else result.put("answer","下面按相同字段展示项目资料。未核实的信息保留为未知；相关原文证据附在项目后面。");next="finalize_response";}
                    case "finalize_response" -> {finalizeResponse();next="validate_response";}
                    case "validate_response" -> {validateResponse();next="END";}
                    default -> throw new IllegalStateException("UNKNOWN_GRAPH_NODE");
                }
            } catch(RuntimeException ex) {
                boolean evidenceNode=Set.of("retrieve_project_evidence","prepare_evidence","generate_claims","validate_citations","verify_claims","assemble_project_cards").contains(node);
                if(!evidenceNode || !projectMode && !Set.of("generate_claims","validate_citations","verify_claims").contains(node))throw ex;
                if(!projectMode || !"DEADLINE_EXCEEDED".equals(CustomerRecovery.code(ex)) || !Instant.now().isBefore(deadline))CustomerRecovery.requireRecoverable(ex);
                context.check();warning(id,projectMode?"projectEvidence":"evidence",ex);evidenceFailed=true;
                if(projectMode) {evidenceSteps.fallbackEvidence(this);next="match_requirements";}else {references=List.of();evidenceSteps.finishQa(this);next="finalize_response";}
            }
            check();
        }
        private String branch() {
            if(action.containsKey("workflowReply"))return "direct_response";
            if(Set.of("RECOMMEND","COLLECT","DETAIL","COMPARE").contains(intent) && state.get("pendingConditions") instanceof List<?> p && !p.isEmpty())return "direct_response";
            return switch(intent) {
                case "STATE","CHAT","EXPLAIN" -> "direct_response";
                case "QUESTION" -> "retrieve_knowledge";
                case "DIAGNOSE" -> "diagnose";
                case "DETAIL","COMPARE" -> {projectMode=true;yield "load_project_details";}
                default -> {
                    if(!"READY_TO_SEARCH".equals(CustomerWorkflow.task(state).get("status")))yield "direct_response";
                    CustomerWorkflow.task(state).put("status","EVALUATING");
                    projectMode=true;recommendation=true;yield "plan_recommendation";
                }
            };
        }
        private void load() {
        Map<String,Object> run=db.run(id); owner=CustomerStore.number(run,"account_id");sessionId=CustomerStore.number(run,"session_id");
        message=run.get("message").toString(); session=store.session(sessionId,owner);
        prior=json.read(session.get("state_json"));
        var rawHistory=db.messages(sessionId,Long.MAX_VALUE,40);
        messageId=rawHistory.stream().filter(m->id.equals(m.get("run_id"))&&"user".equals(m.get("role"))).map(m->m.get("id")).findFirst().orElse("run:"+id);
        CustomerWorkflow.initialize(prior,rawHistory,id);
        if("EVALUATING".equals(CustomerWorkflow.task(prior).get("status"))) {
            CustomerWorkflow.task(prior).put("status","READY_TO_SEARCH");CustomerWorkflow.task(prior).put("lastOutcome","PREVIOUS_RUN_INTERRUPTED");
        }
        prior.put("_expectedStateVersion",session.getOrDefault("state_version",0));
        for(var step:db.steps(id))if("inputContext".equals(step.get("tool_name")))binding=json.read(step.get("detail_json"));
        CustomerTaskProgress.recover(prior,db,sessionId,id);
        context=tools.context(id,owner,deadline); context.observe(usage); context.check(); db.stage(id,"UNDERSTANDING");
        if(usage instanceof CustomerUsageLedger ledger)ledger.guard=context::check;
        try { profile=context.profile(); }
        catch(RuntimeException ex) { CustomerRecovery.requireRecoverable(ex); context.check(); profile=""; warning(id,"profile",ex); }
        history=history(sessionId,id);
        if(contextSupport!=null)contextSupport.prepare(this);
        }
        private void understand() {
        String systemIntent=CustomerConditionFacts.route(message);
        var input=contextSupport!=null?contextSupport.input(this):CustomerDialogue.input(message,prior,profile,history.messages(),history.summary(),config.getInputTokens(),json,
            Map.of("positions",resolution.positions(),"references",resolution.references(),"ambiguous",resolution.ambiguous()));
        boolean migration=Boolean.TRUE.equals(CustomerWorkflow.workflow(prior).get("migrationPending"));
        action=migration?new LinkedHashMap<>(Map.of("intent","CHAT","reply","请确认旧会话需求。","changes",Map.of())):systemIntent==null?new LinkedHashMap<>(model.call(CustomerState.PROMPT,input,deadline,usage)):new LinkedHashMap<>(Map.of("intent",systemIntent,"changes",Map.of()));
        if(!migration&&systemIntent==null&&contextSupport!=null&&contextSupport.recoverRequested(this,action))
            action=new LinkedHashMap<>(model.call(CustomerState.PROMPT,contextSupport.input(this),deadline,usage));
        if(Boolean.TRUE.equals(action.get("needsHistory")))action=new LinkedHashMap<>(Map.of("intent","CHAT","reply","本轮可回查的历史还不能唯一确认你指的是哪个项目或哪项要求，请补充项目名称、当时的目标或需要恢复的任务。已有条件保留着。","changes",Map.of()));
        action.remove("workflowReply");action.remove("validatedTaskAction");
        action.put("positions",resolution.positions());
        if(!resolution.references().isEmpty() && "QUESTION".equals(action.get("intent")))action.put("intent","DETAIL");
        // Route a pure greeting to dialogue; its actual reply is still generated by the model.
        if(CustomerDialogue.isGreeting(message)) action.put("intent","CHAT");
        if("CHAT".equals(action.get("intent")) && CustomerDialogue.explicitlyRequestsEvidence(message)) {
            action.put("intent","QUESTION");
            db.step(id,"intentBoundary","QUESTION_REQUIRED",json.write(Map.of("reason","EXPLICIT_EVIDENCE_REQUEST")));
        }
        var coveredTopics=CustomerTopicCoverage.complete(action,message);
        String coveredGoal=CustomerGoalCoverage.complete(action,message);
        if(!coveredGoal.isBlank())db.step(id,"conditionCoverage","LITERAL_GOAL_RECOVERED",json.write(Map.of("source","MESSAGE","field","goal")));
        if(!coveredTopics.isEmpty())db.step(id,"conditionCoverage","LITERAL_TOPIC_RECOVERED",json.write(Map.of("topics",coveredTopics,"source","MESSAGE")));
        preamble=CustomerTurnPlan.preamble(action,prior,message);
        rejected=new ArrayList<>();
        // An explanation must not mutate conditions, even if the model also proposes changes.
        if("EXPLAIN".equals(action.get("intent"))) { action.put("changes",Map.of()); action.put("profileDefaults",Map.of()); action.remove("pendingConditions"); action.remove("preferences"); }
        }
        private void reconcile() {
        prior=json.read(json.write(prior));
        CustomerTaskService.prepare(prior,action,message,id);
        context.check(); state="EXPLAIN".equals(action.get("intent"))?new LinkedHashMap<>(prior):CustomerState.reconcile(prior,action,message,profile,rejected);
        CustomerRequirementService.apply(state,prior,action,message,messageId,id);
        CustomerTaskService.apply(state,action,message,messageId,id,resolution);
        changes=CustomerConditionFacts.changes(prior,state,action);
        if(!changes.isBlank())state.put("lastChangeSummary",changes);
        if(!rejected.isEmpty()) db.step(id,"conditionValidation","REJECTED_PROPOSALS",json.write(Map.of("rejected",rejected)));
        // References were validated against server-owned list identities before interpretation.
        if(!"EXPLAIN".equals(action.get("intent")))CustomerProjectFeedback.apply(state,action.get("projectFeedback"),message,id);
        db.step(id,"turnPlan","SUCCEEDED",json.write(Map.of("intent",action.get("intent"),"hasPreamble",!preamble.isBlank(),"positions",action.getOrDefault("positions",List.of()),"rejectedFields",rejected.size())));
        store.checkpoint(id,state);
        if(contextSupport!=null)contextSupport.bind(this);
        intent=action.get("intent").toString(); result=new LinkedHashMap<>();
        result.put("skill",intent.equals("QUESTION")?"KNOWLEDGE_QA_V1":intent.equals("COLLECT")?"REQUIREMENTS_V1":"PROJECT_RECOMMENDATION_V1");
        result.put("intent",intent); result.put("state",state); result.put("projects",List.of()); result.put("references",List.of());
        result.put("workflowIntent",CustomerRoutePolicy.intent(action));result.put("workflowRoute",CustomerRoutePolicy.route(action));result.put("intentSubtype",CustomerRoutePolicy.subtype(action));
        result.put("profileLoaded",!profile.isBlank());
        }
        private void direct() {
        if(action.containsKey("workflowReply")) {result.put("status",state.get("pendingConditions") instanceof List<?> p&&!p.isEmpty()?"NEEDS_CLARIFICATION":CustomerWorkflow.question(state).isEmpty()?"TASK_UPDATED":"FOLLOW_UP");result.put("answer",action.get("workflowReply"));}
        else if(intent.equals("STATE")) {
            result.put("status","CONFIRMED_CONDITIONS");result.put("answer",CustomerConditionFacts.describe(state)+"\n"+CustomerRequirementService.query(state));
        }
        else if(Set.of("RECOMMEND","COLLECT","DETAIL","COMPARE").contains(intent) && state.get("pendingConditions") instanceof List<?> pending && !pending.isEmpty()) {
            result.put("status","NEEDS_CLARIFICATION");
            result.put("answer","能理解的条件已经记下，还有一项需要确认，避免给你不符合要求的项目。");
            result.put("pendingQuestion",((Map<?,?>)pending.getFirst()).get("question"));
            result.put("pendingConditions",pending);
        }
        else if(intent.equals("CHAT")) {
            result.put("status","CONVERSATION"); result.put("answer",CustomerDialogue.reply(action));
            if(state.get("pendingConditions") instanceof List<?> pending && !pending.isEmpty() && !CustomerDialogue.isGreeting(message)) {
                result.put("pendingConditions",pending);
                if(!rejected.isEmpty()) { result.put("answer","能确认的条件已保存，有一项暂时不能可靠确认。其他条件保留，我们可以继续聊。"); result.put("pendingQuestion",((Map<?,?>)pending.getFirst()).get("question")); }
            }
        }
        else if(intent.equals("EXPLAIN")) { result.put("status","PROCESS_EXPLANATION");result.put("answer",CustomerTaskProgress.describe(state)+CustomerPurpose.explanation(state)); }
        else {
            String followUp=CustomerState.followUp(state);
            result.put("status","FOLLOW_UP");result.put("answer",action.get("reply") instanceof String reply && !reply.isBlank()?CustomerDialogue.reply(action):followUp);
        }
        }
        private void finalizeResponse() {
        context.check(); db.stage(id,"VALIDATING");
        result.put("conditionChangeSummary",changes);
        if(!changes.isBlank()&&!intent.equals("STATE")) result.put("answer",changes+"\n\n"+result.get("answer"));
        if(!preamble.isBlank())result.put("answer",preamble+"\n\n"+result.get("answer"));
        }
        private void validateResponse() {
        if(result.get("projects") instanceof List<?> cards) {
            List<Object> valid=new ArrayList<>();
            for(Object raw:cards) {
                Map<?,?> card=(Map<?,?>)raw;
                try {
                    if("NEEDS_VERIFICATION".equals(card.get("matchStatus"))) projects.exploreDetail(card.get("type").toString(),CustomerStore.positiveId(card.get("id")),state);
                    else projects.detail(card.get("type").toString(),CustomerStore.positiveId(card.get("id")),state);
                    valid.add(raw);
                } catch(IllegalArgumentException ex) {
                    if(!Set.of("PROJECT_NOT_FOUND","PROJECT_INACTIVE_OR_CONSTRAINT_MISMATCH").contains(ex.getMessage())) throw ex;
                    warning(id,"projectChanged",ex);
                }
            }
            if(valid.size()!=cards.size()) {
                result.remove("recommendationReport");
                result.put("projects",valid); result.put("references",List.of());
                for(Object raw:valid) { Map<String,Object> card=cast(raw); card.put("references",List.of()); card.put("importable",false); }
                if(!Set.of("DETAIL","COMPARE").contains(intent))state.put("lastCandidates",valid.stream().map(raw->{ Map<?,?> c=(Map<?,?>)raw; return CustomerProjectFeedback.identity(c); }).toList());
                result.put("notice","部分项目在本轮处理期间已下架或发生条件变化，已从结果中移除。");
                if(valid.isEmpty()) { result.put("status","PROJECTS_CHANGED"); result.put("answer","刚才的候选已不再有效，不能继续作为推荐依据。你的条件已保留，我们可以继续查找其他项目。"); }
            }
            if(!cards.isEmpty() && !Set.of("DETAIL","COMPARE").contains(intent)) {
                state.put("lastCandidates",valid.stream().map(raw->{ Map<?,?> c=(Map<?,?>)raw; return CustomerProjectFeedback.identity(c); }).toList());
                state.put("candidateListRunId",id);state.remove("focusedProject");
            }
        }
        Object nextQuestion=result.get("pendingQuestion");
        if(nextQuestion==null && "FOLLOW_UP".equals(result.get("status"))) nextQuestion=result.get("answer");
        if(nextQuestion instanceof String question && !question.isBlank()) state.put("lastQuestion",Map.of("text",question.substring(0,Math.min(300,question.length())),"runId",id));
        if(result.get("answer") instanceof String text) result.put("answer",CustomerText.paragraphs(text));
        if(result.get("projects") instanceof List<?> cards && !cards.isEmpty() && state.get("sourceFactsVersion") instanceof String version && !version.equals(purposeCatalog.version())) {
            result.put("projects",List.of());result.put("references",List.of());result.remove("recommendationReport");
            result.put("status","PROJECTS_CHANGED");result.put("answer","本轮使用的来源证据刚刚发生变更，推荐暂未提交。你的条件已保留，可以继续按最新资料查找。");
            state.remove("focusedProject");state.put("lastCandidates",List.of());
        }
        CustomerPurpose.appendPartialNotice(result);
        CustomerTaskProgress.finish(state,id,result.get("status"));
        CustomerTaskService.finish(state,result,intent,id);
        result.put("businessOutcome",CustomerOutcome.classify(result));
        if(contextSupport!=null)contextSupport.archive(this);
        }
    }
    void progress(Map<String,Object> state,CustomerTools.Context context,String phase) {
        context.check();CustomerTaskProgress.advance(state,context.run,phase);store.checkpoint(context.run,state);
    }
    void diagnose(Map<String,Object> state,CustomerTools.Context context,Map<String,Object> result) {
        if(CustomerPurpose.unavailable(state)) {
            result.put("status","RELEVANCE_UNAVAILABLE");result.put("answer",CustomerPurpose.explanation(state));return;
        }
        try {
            var diagnostic=context.diagnose(state);result.put("diagnostics",diagnostic);result.put("status",diagnostic.getOrDefault("status","FILTER_DIAGNOSTICS"));
            result.put("answer",CustomerDiagnostics.describe(diagnostic));
        } catch(RuntimeException ex) {
            CustomerRecovery.requireRecoverable(ex);context.check();warning(context.run,"filterDiagnostics",ex);
            result.put("status","DIAGNOSTICS_UNAVAILABLE");result.put("answer","这次暂时无法核对调整方案。你的要求已经保留，我们可以先聊聊最想实现的功能，再尝试调整方向或周期；我不会自动更改你的条件。");
        }
    }
    void recommendationAdvice(List<Map<String,Object>> cards,Map<String,Object> state,CustomerTools.Context context,Map<String,Object> result,Instant deadline,List<Map<String,Object>> usage) {
        if(cards.stream().anyMatch(c->!CustomerRecommendationAdvice.references(c).isEmpty()) && Instant.now().plusSeconds(8).isBefore(deadline)) {
            try {
                context.check(); db.stage(context.run,"GENERATING");
                Instant adviceDeadline=Instant.now().plusSeconds(25);
                if(adviceDeadline.isAfter(deadline.minusSeconds(5))) adviceDeadline=deadline.minusSeconds(5);
                var output=model.call(CustomerRecommendationAdvice.PROMPT,CustomerRecommendationAdvice.input(cards,state),adviceDeadline,usage);
                CustomerRecommendationAdvice.apply(output,cards,state);
                db.step(context.run,"practiceAdvice","SUCCEEDED",json.write(Map.of("kind","SUGGESTION","promptVersion",PROMPT_VERSION)));
            } catch(RuntimeException ex) {
                if(!"DEADLINE_EXCEEDED".equals(CustomerRecovery.code(ex)) || !Instant.now().isBefore(deadline)) CustomerRecovery.requireRecoverable(ex);
                context.check(); warning(context.run,"practiceAdvice",ex);
            }
        }
        result.put("recommendationReport",CustomerRecommendationAdvice.report(cards,state));
    }
    private CustomerHistory.View history(long session,String run) {
        var view=CustomerHistory.build(db.messages(session,Long.MAX_VALUE,40),run,1400,json);
        return view;
    }
    List<Map<String,Object>> evidenceBudget(List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits,String question) {
        return evidenceBudget(hits,question,Map.of());
    }
    List<Map<String,Object>> evidenceBudget(List<com.orangejd.server.ragb.retrieval.RagBSearchHit> hits,String question,Map<String,String> aliases) {
        for(int budget=4200;budget>=1000;budget-=400) {
            var input=CustomerEvidence.input(hits,budget,aliases);
            if(!input.isEmpty() && CustomerModel.estimate(CustomerEvidence.PROMPT+json.write(Map.of("question",question,"evidence",CustomerEvidence.promptEvidence(input))))<=config.getInputTokens()) return input;
        }
        throw new IllegalArgumentException(hits.isEmpty()?"NO_USABLE_EVIDENCE":"EVIDENCE_BUDGET_EXCEEDED");
    }
    private String confirmedConditions(Map<String,Object> state) {
        Map<String,String> labels=new LinkedHashMap<>();
        labels.put("goal","目标"); labels.put("experience","基础"); labels.put("language","语言"); labels.put("topic","主题");
        labels.put("difficulty","难度"); labels.put("maxDurationDays","周期上限（天）"); labels.put("minDurationDays","周期下限（天）");
        labels.put("allowDocker","Docker"); labels.put("allowDatabase","数据库"); labels.put("allowSecrets","API密钥");
        List<String> facts=new ArrayList<>();
        labels.forEach((key,label)->{ if(state.containsKey(key)) facts.add(label+"："+(state.get(key) instanceof Boolean allowed?(allowed?"允许使用":"不使用"):state.get(key))); });
        return facts.isEmpty()?"当前还没有确认的项目条件。":"当前已确认条件："+String.join("；",facts)+"。";
    }
    void warning(String run,String component,RuntimeException ex) {
        db.step(run,component,"DEGRADED",json.write(CustomerRecovery.details(ex)));
    }
    @SuppressWarnings("unchecked") Map<String,Object> cast(Object value) { return (Map<String,Object>) value; }
    public Map<String,Object> metrics(List<Map<String,Object>> usage) {
        Set<String> missing=usage instanceof CustomerUsageLedger ledger?Set.copyOf(ledger.missing):Set.of();
        var orchestration=usage instanceof CustomerUsageLedger ledger?List.copyOf(ledger.orchestration):List.of();
        usage=new ArrayList<>(usage);
        Map<String,Object> metrics=new LinkedHashMap<>(Map.of("modelCalls",new ArrayList<>(usage),"costUsd","UNKNOWN","embeddingUsage","NOT_REPORTED","protocol",config.isNativeActions()?"NATIVE_ACTION_V1":"SCHEMA_ACTION_V1","promptVersion",PROMPT_VERSION));
        metrics.put("orchestrationTimings",orchestration);metrics.put("observationMissing",missing);metrics.put("tokenUsage",CustomerUsage.summarize(usage)); metrics.put("phaseUsage",CustomerUsage.byPhase(usage)); return metrics;
    }
}
