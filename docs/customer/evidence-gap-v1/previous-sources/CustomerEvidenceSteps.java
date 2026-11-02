package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;

/** Shared business operations; graph routing stays in the request-local turn. */
final class CustomerEvidenceSteps {
    private final CustomerConversation c;
    CustomerEvidenceSteps(CustomerConversation c){this.c=c;}
    void retrieveKnowledge(CustomerConversation.Turn t) {
        boolean platform=t.message.matches("(?s).*(这个平台|当前平台|平台的|平台支持|客服会|知识库怎么保证).*" );
        c.db.stage(t.context.run,"SEARCHING");
        try { t.found=t.context.knowledge(t.message,platform?Set.of("MANUAL"):Set.of()); }
        catch(RuntimeException ex) { CustomerRecovery.requireRecoverable(ex); t.context.check(); c.warning(t.context.run,"knowledge",ex); t.result.put("status","KNOWLEDGE_UNAVAILABLE"); t.result.put("answer","资料检索暂时不可用，当前不能确认这个问题中的项目或平台事实。我们仍可以继续聊需求，也可以先查看业务库中的真实项目。"); t.result.put("pendingQuestion","你想先看项目候选，还是继续补充使用场景？"); return; }
        t.result.put("generation",t.found.generation());
        t.hits=platform?t.found.hits().stream().filter(h->h.sourceRef()!=null && h.sourceRef().startsWith("platform-faq:")).toList():t.found.hits();
        if(platform && t.hits.isEmpty()) { t.result.put("status","REFUSE_NO_PLATFORM_FAQ_EVIDENCE"); t.result.put("answer","当前没有可核对的平台规则资料，暂时不能确认这项政策。我们可以继续讨论项目需求。"); t.result.put("pendingQuestion","你最想解决的具体使用问题是什么？"); return; }
        if(!"ANSWER_WITH_CITATIONS".equals(t.found.answerPolicy())) { t.result.put("status",t.found.answerPolicy()); t.result.put("answer","现有资料还不足以确认这个问题的结论，但不影响我们继续找项目。你可以补充问题，也可以让我按已确认条件先推荐真实候选，并标出待核实项。"); t.result.put("pendingQuestion","你想先看项目候选，还是继续核对这个问题？"); return; }
        }
    void retrieveProjectEvidence(CustomerConversation.Turn t) {
        // Identity is already enforced by document scope. Names in the query boost logos and badges.
        String query=CustomerTopics.display(t.state.get("topic"))+" "+CustomerRequirementService.query(t.state)+" 项目功能 用途 实践示例 安装部署 环境依赖 限制 "+t.message;
        t.found=t.context.projectEvidence(query.substring(0,Math.min(2000,query.length())),t.cards);
        t.hits=t.found.hits().stream().filter(h->t.cards.stream().anyMatch(p->CustomerProjects.ownsEvidence(p,h.sourceRef()))).toList();
        }
    void prepareEvidence(CustomerConversation.Turn t) {
        t.evidenceDeadline=t.projectMode?t.deadline.minusSeconds(5):t.deadline;
        if(t.projectMode) {
            if(t.hits.isEmpty()) { t.references=List.of();return; }
            Map<String,String> aliases=new HashMap<>();
            t.cards.forEach(c->{ aliases.put(c.get("sourceRef").toString(),c.get("sourceRef").toString()); aliases.put(c.get("name").toString(),c.get("sourceRef").toString()); });
            t.evidenceQuestion=t.message+"\n当前需求（只用于选择解释重点，不作为项目事实）："+c.json.write(CustomerRecommendationAdvice.conditions(t.state));
            t.evidence=c.evidenceBudget(t.hits,t.evidenceQuestion,aliases);
        } else { t.evidenceQuestion=t.message;t.evidence=c.evidenceBudget(t.hits,t.message); c.db.stage(t.context.run,"GENERATING"); }
        c.db.step(t.context.run,"buildPrompt","SUCCEEDED",c.json.write(Map.of("promptVersion",CustomerConversation.PROMPT_VERSION,"question",t.evidenceQuestion,"evidence",CustomerEvidence.promptEvidence(t.evidence))));
        }
    void validateCitations(CustomerConversation.Turn t) {
        var checked=CustomerEvidence.discardUnsupported(t.generated,t.evidence);
        t.claims=new ArrayList<>(checked.claims());
        while(!t.claims.isEmpty() && CustomerModel.estimate(CustomerEvidence.VERIFY_PROMPT+c.json.write(Map.of("claims",CustomerEvidence.verificationInput(t.claims))))>c.config.getInputTokens()) t.claims.removeLast();
        c.db.step(t.id,"validateEvidence",checked.rejected().isEmpty()?"SUCCEEDED":"CLAIMS_REMOVED",c.json.write(Map.of("accepted",t.claims.size(),"rejected",checked.rejected())));
        }
    void verifyClaims(CustomerConversation.Turn t) {
        if(t.claims.isEmpty()) {t.references=List.of();return;}
        var input=CustomerEvidence.verificationInput(t.claims);
        var verdict=c.model.call(CustomerEvidence.VERIFY_PROMPT,Map.of("claims",input),t.evidenceDeadline,t.usage);
        t.references=CustomerEvidence.supported(t.claims,verdict);
        c.db.step(t.id,"verifyFacts","SUCCEEDED",c.json.write(Map.of("claims",input,"verdict",verdict,"supported",t.references.size())));
        }
    void assemble(CustomerConversation.Turn t) {
        for(var card:t.cards) {
            card.remove("catalogText");
            card.put("recommendReason",c.projects.reason(card,t.state));
            card.put("references",t.references.stream().filter(r->CustomerProjects.ownsEvidence(card,Objects.toString(r.get("sourceRef"),""))).toList());
            for(String field:List.of("description","prerequisites","mainRisks","vibeCodingReason")) {
                String supported=String.join("\n",t.references.stream().filter(r->field.equals(r.get("field")) && CustomerProjects.ownsEvidence(card,Objects.toString(r.get("sourceRef"),""))).map(r->r.get("text")+" ["+r.get("evidenceId")+"]").toList());
                if(!supported.isBlank()) {
                    card.put(field,supported);
                    if(field.equals("description"))card.put("whatIsIt",supported);
                }
            }
            card.put("supportedExplanation",String.join("\n",t.references.stream().filter(r->CustomerProjects.ownsEvidence(card,Objects.toString(r.get("sourceRef"),""))).map(r->Objects.toString(r.get("text"),"")+" ["+r.get("evidenceId")+"]").toList()));
            card.put("importable",!"NEEDS_VERIFICATION".equals(card.get("matchStatus")) && !((List<?>)card.get("references")).isEmpty());
        }
        t.result.put("projects",t.cards); t.result.put("references",t.references); t.result.put("generation",t.found.generation());
        boolean allSupported=t.cards.stream().allMatch(c->!"NEEDS_VERIFICATION".equals(c.get("matchStatus")) && !((List<?>)c.get("references")).isEmpty());
        t.result.put("status",allSupported?"VERIFIED_PROJECTS":"PROJECTS_WITH_LIMITATIONS");
        if(!allSupported)t.result.put("notice","部分项目仍有待核实条件或缺少本轮支持证据，相关项目暂不能导入正式方案；有引用仅表示所引用事实得到支持。");
        }
    void finishQa(CustomerConversation.Turn t) {
        t.result.put("references",t.references); t.result.put("status",t.references.isEmpty()?"REFUSE_INSUFFICIENT_EVIDENCE":"SUPPORTED_EXTRACTS");
        t.result.put("answer",t.references.isEmpty()?"这次资料核对未形成可靠结论。我们可以继续讨论需求，或先查看真实项目并标出待核实信息。":String.join("\n\n",t.references.stream().map(r->r.get("text")+" ["+r.get("evidenceId")+"]").toList()));
        String pending=CustomerState.followUp(c.cast(t.result.get("state"))); if(pending!=null) t.result.put("pendingQuestion",pending);
        }
    void fallbackEvidence(CustomerConversation.Turn t) {
            for(var card:t.cards) { card.put("references",List.of()); card.put("importable",false); card.put("evidenceStatus","UNAVAILABLE"); card.put("recommendReason",c.projects.reason(card,t.state)+"补充资料核对暂未完成，部署、功能等细节仍需确认。"); }
            t.result.put("projects",t.cards); t.result.put("references",List.of()); t.result.put("status","PROJECTS_WITH_LIMITATIONS");
            t.result.put("notice","资料核对暂未完成，当前仅展示业务库已记录的事实，不能带入正式方案。");
        }
}
