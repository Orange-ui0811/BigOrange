package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;

/** Shared business operations; graph routing stays in the request-local turn. */
final class CustomerRecommendationSteps {
    static final String KNOWN_EVIDENCE_STATUS="KNOWN_EVIDENCE_READY";
    private final CustomerConversation c;
    CustomerRecommendationSteps(CustomerConversation c){this.c=c;}
    void plan(CustomerConversation.Turn t) {
        c.progress(t.state,t.context,"PLANNING");

        try { t.policy=Objects.toString(c.model.policyIdentity(),c.config.getModel()); }
        catch(RuntimeException ex) { CustomerRecovery.requireRecoverable(ex);t.context.check();c.warning(t.context.run,"purposeCache",ex);t.policy="unavailable:"+t.context.run; }
        String factsVersion=c.purposeCatalog.version();t.state.put("sourceFactsVersion",factsVersion);
        t.policy=t.policy+"|catalog:"+factsVersion;
        t.related=List.of();
        t.purposeQuery=CustomerTopics.retrievalQuery(t.state.get("topic"));
        if(t.state.containsKey("topic")) {
            try {
                String cached=CustomerPurposeMemory.plannedQuery(t.state,t.policy);
                if(cached!=null)t.purposeQuery=cached;
                else {
                    var plan=c.model.call(CustomerPurpose.PLAN_PROMPT,Map.of("topic",CustomerPurpose.topic(t.state),"originalRequest",CustomerPurpose.originalRequest(t.state)),t.deadline.minusSeconds(15),t.usage);
                    t.purposeQuery=CustomerPurpose.query(plan,t.state);CustomerPurposeMemory.rememberPlan(t.state,t.policy,t.purposeQuery);
                }
                c.db.step(t.context.run,"planProjectQuery",cached==null?"SUCCEEDED":"CACHE_HIT",c.json.write(Map.of("topic",t.state.get("topic"),"query",t.purposeQuery)));
            } catch(RuntimeException ex) {
                if(!"DEADLINE_EXCEEDED".equals(CustomerRecovery.code(ex)) || !Instant.now().isBefore(t.deadline)) CustomerRecovery.requireRecoverable(ex);
                t.context.check();c.warning(t.context.run,"planProjectQuery",ex);
            }
        }
        }
    void recall(CustomerConversation.Turn t) {
        c.progress(t.state,t.context,"RETRIEVING");
        if(!CustomerWorkflow.active(t.state).isEmpty() || List.of("language","topic","goal","experience").stream().anyMatch(t.state::containsKey)) {
            String query=t.state.containsKey("topic")?t.purposeQuery:"推荐项目 "+Objects.toString(t.state.get("language"),"")+" "+CustomerGoals.display(t.state.get("goal"));
            query=query+" "+CustomerRequirementService.query(t.state);
            try { c.db.stage(t.context.run,"SEARCHING"); t.related=t.context.projectRecall(query).hits(); }
            catch(RuntimeException ex) { CustomerRecovery.requireRecoverable(ex); t.context.check(); c.warning(t.context.run,"recommendationRecall",ex); t.result.put("rankingNotice","语义排序暂不可用，本次按已确认条件、基础偏好和项目质量筛选。"); }
        }
        }
    void review(CustomerConversation.Turn t) {
        c.db.stage(t.context.run,"FILTERING_PROJECTS");
        if(t.state.containsKey("topic")) {
            c.progress(t.state,t.context,"REVIEWING");
            Map<String,Object> reviewInput=null;t.candidates=List.of();
            try {
                t.candidates=t.context.purposeCandidates(t.state,t.related,t.purposeQuery);
                var sourceFacts=c.purposeCatalog.seed(t.candidates,t.state,t.policy,c.json);
                if(!sourceFacts.isEmpty())c.db.step(t.context.run,"sourcePurposeEvidence","SUCCEEDED",c.json.write(Map.of("catalogVersion",c.purposeCatalog.version(),"evidenceIds",sourceFacts)));
                var pending=CustomerPurposeMemory.pending(t.candidates,t.state,t.policy);
                boolean restored=CustomerPurposeMemory.restore(t.candidates,t.state,t.policy);
                // Current constraints still decide admission. Never call select here: it strips source text.
                long usable=restored?t.candidates.stream().filter(p->c.projects.explorable(p,t.state)).map(CustomerProjects::identity).distinct().count():0;
                boolean serveKnown=usable>=3 || usable>0 && CustomerPurpose.reviewPaused(t.state);
                int batches=0;String stopReason=serveKnown?"KNOWN_EVIDENCE":"COMPLETE";
                // All batches share the existing review-stage deadline and leave time for evidence checks.
                Instant reviewDeadline=Instant.now().plusSeconds(c.config.getPurposeSeconds());
                if(t.deadline.minusSeconds(20).isBefore(reviewDeadline))reviewDeadline=t.deadline.minusSeconds(20);
                while(!pending.isEmpty() && !serveKnown) {
                    if(batches>=c.config.getPurposeMaxBatches()) {stopReason="BATCH_LIMIT";break;}
                    if(!Instant.now().plusSeconds(8).isBefore(reviewDeadline)) {stopReason="TIME_BUDGET";break;}
                    var input=CustomerPurpose.input(pending,t.state,c.json,c.config.getInputTokens());reviewInput=input;t.reviewInput=input;
                    if(CustomerPurpose.reviewPaused(t.state) || CustomerPurpose.retryBlocked(t.state,input)) {
                        c.db.step(t.context.run,"reviewProjectPurpose","RETRY_SUPPRESSED",c.json.write(Map.of("reason","RECENT_CONTENT_REJECTION")));
                        if(!CustomerPurposeMemory.restore(t.candidates,t.state,t.policy)) {
                            t.state.put("lastCandidates",List.of());t.result.put("status","RELEVANCE_UNAVAILABLE");t.result.put("answer",CustomerPurpose.failureAnswer(t.state));return;
                        }
                        stopReason="RECENT_CONTENT_REJECTION";break;
                    } else {
                        batches++;
                        c.db.step(t.context.run,"reviewProjectPurpose","STARTED",c.json.write(Map.of("batch",batches,"candidateKeys",CustomerWorkflow.rows(input.get("candidates")).stream().map(p->p.get("key")).toList(),"inputHash",CustomerStore.hash(c.json.write(input)))));
                        var reviewed=c.model.call(CustomerPurpose.REVIEW_PROMPT,input,reviewDeadline,t.usage);
                        CustomerPurposeMemory.remember(reviewed,input,pending,t.state,t.policy);
                        c.db.step(t.context.run,"reviewProjectPurpose","SUCCEEDED",c.json.write(Map.of("input",input,"verdict",reviewed,"cachedCount",t.candidates.size()-pending.size())));
                        c.store.checkpoint(t.context.run,t.state);
                        var remaining=CustomerPurposeMemory.pending(t.candidates,t.state,t.policy);
                        // Invalid/partial output must not start a new sequence of retries under another batch.
                        if(remaining.size()>=pending.size() || reviewed.containsKey("unreviewedKeys")) {pending=remaining;stopReason="PARTIAL_OUTPUT";break;}
                        pending=remaining;
                    }
                }
                if(batches==0)c.db.step(t.context.run,"reviewProjectPurpose",serveKnown?KNOWN_EVIDENCE_STATUS:"CACHE_HIT",c.json.write(Map.of("candidateCount",t.candidates.size(),"pendingCount",pending.size())));
                var coverage=Map.of("candidateCount",t.candidates.size(),"reviewedCount",t.candidates.size()-pending.size(),"pendingCount",pending.size(),"batches",batches,"stopReason",stopReason);
                t.result.put("purposeCoverage",coverage);
                c.db.step(t.context.run,"purposeCoverage",pending.isEmpty()?"COMPLETE":"PARTIAL",c.json.write(coverage));
                if(!CustomerPurposeMemory.restore(t.candidates,t.state,t.policy)) {
                    // A valid empty batch is coverage information, not a provider failure.
                    t.state.put(CustomerPurpose.STATE_KEY,Map.of("status","PARTIAL_EMPTY","scope",CustomerPurpose.scope(t.state),"topic",CustomerPurpose.topic(t.state),"proofs",Map.of(),"reviewedCount",t.candidates.size()-pending.size(),"candidateCount",t.candidates.size()));
                    t.state.put("lastCandidates",List.of());t.result.put("status","PURPOSE_REVIEW_PARTIAL");
                    t.result.put("answer","本轮候选共 "+t.candidates.size()+" 个，已核对 "+(t.candidates.size()-pending.size())+" 个，暂未取得符合方向的用途依据；另有 "+pending.size()+" 个尚未核对。本轮核对尚未完成，不能据此判断没有合适项目。你的条件已保留。");
                    c.store.checkpoint(t.context.run,t.state);return;
                }
                c.store.checkpoint(t.context.run,t.state);

            } catch(RuntimeException ex) {
                if(!"DEADLINE_EXCEEDED".equals(CustomerRecovery.code(ex)) || !Instant.now().isBefore(t.deadline)) CustomerRecovery.requireRecoverable(ex);
                t.context.check();c.warning(t.context.run,"reviewProjectPurpose",ex);
                CustomerPurpose.failed(t.state,ex,reviewInput);
                // A failure on new candidates cannot invalidate already verified, unchanged candidates.
                if(!t.candidates.isEmpty() && CustomerPurposeMemory.restore(t.candidates,t.state,t.policy)) {
                    t.reviewRecovered=true;
                    t.result.put("notice","本次仅展示已有有效用途依据且通过当前条件检查的项目，其他候选暂未完成核对。");
                } else {
                    t.state.put("lastCandidates",List.of());t.result.put("status","RELEVANCE_UNAVAILABLE");
                    t.result.put("answer",CustomerPurpose.failureAnswer(t.state));return;
                }
            }
            if("PARTIAL_MATCHES".equals(CustomerPurpose.status(t.state))) {
                t.result.put("purposeReviewIncomplete",true);
            }
        }
        }
    void select(CustomerConversation.Turn t) {
        if(Set.of("RELEVANCE_UNAVAILABLE","PURPOSE_REVIEW_PARTIAL").contains(Objects.toString(t.result.get("status"),"")))return;
        if("SUCCEEDED_EMPTY".equals(CustomerPurpose.status(t.state))) {
            t.state.put("lastCandidates",List.of());t.result.put("status","NO_MATCH");
            t.result.put("answer","本次已检查的候选中没有取得符合当前方向的用途依据。这不代表整个知识库或 GitHub 没有合适项目；你的条件已保留。");return;
        }
        if(t.state.containsKey("topic")) {
            // The frozen implementation included selection in the purpose-review recovery boundary.
            try {t.cards=c.projects.select(t.candidates,t.state,t.related);}
            catch(RuntimeException ex) {
                if(t.reviewRecovered)throw ex;
                if(!"DEADLINE_EXCEEDED".equals(CustomerRecovery.code(ex)) || !Instant.now().isBefore(t.deadline))CustomerRecovery.requireRecoverable(ex);
                t.context.check();c.warning(t.context.run,"reviewProjectPurpose",ex);CustomerPurpose.failed(t.state,ex,t.reviewInput);
                if(t.candidates.isEmpty() || !CustomerPurposeMemory.restore(t.candidates,t.state,t.policy)) {
                    t.state.put("lastCandidates",List.of());t.result.put("status","RELEVANCE_UNAVAILABLE");t.result.put("answer",CustomerPurpose.failureAnswer(t.state));return;
                }
                t.cards=c.projects.select(t.candidates,t.state,t.related);
                t.result.put("notice","本次仅展示已有有效用途依据且通过当前条件检查的项目，其他候选暂未完成核对。");
            }
        } else t.cards=t.related.isEmpty()?t.context.projects(t.state):t.context.rankedProjects(t.state,t.related);
        if(t.cards.isEmpty() && "PARTIAL_MATCHES".equals(CustomerPurpose.status(t.state))) {
            t.state.put("lastCandidates",List.of());t.result.put("status","PURPOSE_REVIEW_PARTIAL");
            t.result.put("answer","已核对的候选暂未同时通过当前条件检查，其他候选尚未完成用途核对。不能据此认定没有合适项目；你的条件已保留。");return;
        }
        if(t.cards.isEmpty()) { t.state.put("lastCandidates",List.of()); c.diagnose(t.state,t.context,t.result); if(!"RELEVANCE_UNAVAILABLE".equals(t.result.get("status")))t.result.put("status","NO_MATCH"); return; }
        t.state.put("lastCandidates",t.cards.stream().map(CustomerProjectFeedback::identity).toList());
        c.progress(t.state,t.context,"ENRICHING");
        }
    void details(CustomerConversation.Turn t) {
        List<?> candidates=t.state.get("lastCandidates") instanceof List<?> l?l:List.of();
        if(candidates.isEmpty()) { t.result.put("status","FOLLOW_UP"); t.result.put("answer","当前会话还没有项目列表，请先说明想找什么项目。"); return; }
        List<Integer> positions;
        if(t.resolution!=null&&!t.resolution.references().isEmpty()) {
            candidates=t.resolution.references().stream().map(r->r.get("project")).toList();
            t.action.put("positions",java.util.stream.IntStream.rangeClosed(1,candidates.size()).boxed().toList());
        }
        try { positions=CustomerState.positions(t.action.get("positions"),candidates.size()); }
        catch(IllegalArgumentException ex) { t.result.put("status","FOLLOW_UP"); t.result.put("answer","这个位置不在刚才的项目列表里，请选择列表中的项目编号。"); return; }
        if(positions.isEmpty()) { t.result.put("status","FOLLOW_UP"); t.result.put("answer","你希望查看或比较刚才列表中的哪几个项目？"); return; }
        t.cards=new ArrayList<>();
        for(int pos:positions) {
            Map<?,?> key=(Map<?,?>)candidates.get(pos-1);
            try { t.cards.add(t.context.detail(key.get("type").toString(),CustomerStore.positiveId(key.get("id")),t.state)); }
            catch(IllegalArgumentException ex) {
                if(!Set.of("PROJECT_NOT_FOUND","PROJECT_INACTIVE_OR_CONSTRAINT_MISMATCH").contains(ex.getMessage())) throw ex;
                c.warning(t.context.run,"projectChanged",ex); t.result.put("notice","部分项目已下架或不满足修改后的条件，已排除。");
            }
        }
        if(t.cards.isEmpty()) { t.state.put("lastCandidates",List.of()); t.result.put("status","PROJECTS_CHANGED"); t.result.put("answer","你选择的项目已不再有效，或不符合修改后的要求。条件都已保留，我可以继续为你找其他项目。"); return; }
        CustomerTurnPlan.focus(t.state,t.cards,t.context.run);
        }
    void finishRecommendation(CustomerConversation.Turn t) {
        if(t.cards.isEmpty())return;
        String next=null; // Workflow questions are task-specific; fixed profile completion is retired.
        boolean uncertain="PROJECTS_WITH_LIMITATIONS".equals(t.result.get("status"))||t.cards.stream().anyMatch(p->"NEEDS_VERIFICATION".equals(p.get("matchStatus")));
        t.result.put("answer",uncertain?"下面是可继续考察的真实项目：已排除明确冲突，但具体用途、条件或学习适配仍有未核实项，已逐项标注。通用搭建工具或改造路线不代表已经具备所需业务功能。":next==null?"以下项目的本轮具体用途及必要条件已有依据；学习门槛另列说明。周期仍是资料估算，实际完成时间取决于实现范围和投入。":"先给你几项真实项目作为探索候选，已执行你明确提出的条件；偏好可以继续聊着补充。");
        if(Boolean.TRUE.equals(t.result.get("purposeReviewIncomplete"))) {
            t.result.put("status","PROJECTS_WITH_LIMITATIONS");
            t.result.put("answer",t.result.get("answer")+"\n\n以上仅包含已有有效用途依据的项目，其他候选尚未完成核对，因此不是完整的匹配结果。");
        }
        if(next!=null) t.result.put("pendingQuestion",next);
        // Workflow v2 owns the single candidate-driven question; legacy diagnostics would ask a second one.
        if(!CustomerWorkflow.enabled(t.state) && t.cards.stream().allMatch(p->"NEEDS_VERIFICATION".equals(p.get("matchStatus")))) {
            try {
                var diagnostic=t.context.diagnose(t.state);t.result.put("diagnostics",diagnostic);
                t.result.put("answer",t.result.get("answer")+"\n\n"+CustomerDiagnostics.describe(diagnostic));
                t.result.remove("pendingQuestion");
            } catch(RuntimeException ex) { CustomerRecovery.requireRecoverable(ex);t.context.check();c.warning(t.context.run,"filterDiagnostics",ex); }
        }
        }
}
