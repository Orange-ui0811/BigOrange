package com.orangejd.server.customer;

import java.util.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Classifies server-owned gaps. User answers never promote unknown project facts. */
public final class CustomerEvidenceDecision {
    public static final String VERSION="evidence-gap-v2";
    private CustomerEvidenceDecision() {}
    public static void apply(Map<String,Object> state,Map<String,Object> result,String intent) {
        List<Map<String,Object>> user=new ArrayList<>(),choices=new ArrayList<>(),facts=new ArrayList<>();
        String status=Objects.toString(result.get("status"),"");
        boolean outage=Set.of("KNOWLEDGE_UNAVAILABLE","RELEVANCE_UNAVAILABLE","DIAGNOSTICS_UNAVAILABLE").contains(status)
                ||"UNAVAILABLE".equals(result.get("verificationStatus"))||"UNAVAILABLE".equals(result.get("requirementVerificationStatus"));
        var q=map(result.get("question"));
        if(!intent.equals("QUESTION")&&!outage&&!q.isEmpty()&&Objects.equals(q.get("taskId"),task(state).get("id"))
                &&"OPEN".equals(q.get("status"))&&!Set.of("PAUSED","CANCELLED","COMPLETED").contains(task(state).get("status"))) {
            var entry=Map.<String,Object>of("questionId",q.get("id"),"taskId",q.get("taskId"),"text",q.get("text"),"kind",q.get("kind"));
            if(Set.of("TRADEOFF","MIGRATION").contains(q.get("kind")))choices.add(entry);else user.add(entry);
        }
        facts.addAll(rows(map(result.get("questionCoverage")).get("missingFacts")));
        facts.addAll(rows(result.get("verificationGaps")));
        for(var card:rows(result.get("projects"))) {
            for(var match:rows(card.get("requirementMatches"))) {
                if((Boolean.TRUE.equals(match.get("requiredForVerified"))||"HARD".equals(match.get("strength")))
                        &&Set.of("UNKNOWN","PARTIAL").contains(match.get("verdict")))facts.add(requirementGap(card,match));
            }
            var learning=map(card.get("learningFit"));
            if(Set.of("UNKNOWN","SCOPE_UNVERIFIED").contains(Objects.toString(learning.get("status"),"")))
                facts.add(Map.of("owner","PROJECT_FACT","projectKey",key(card),"projectName",Objects.toString(card.get("name"),"项目"),
                        "text",Objects.toString(learning.get("message"),"尚未核对项目的学习前置条件及适合入门的范围"),"reason","LEARNING_SCOPE_UNVERIFIED"));
            if("NEEDS_VERIFICATION".equals(card.get("matchStatus"))&&facts.stream().noneMatch(f->Objects.equals(f.get("projectKey"),key(card))))
                facts.add(Map.of("owner","PROJECT_FACT","projectKey",key(card),"projectName",Objects.toString(card.get("name"),"项目"),
                        "text","项目的必要条件尚未完成核实，已有引用不能证明所有条件均满足","reason","REQUIRED_CONDITIONS_UNVERIFIED"));
            if(rows(card.get("references")).isEmpty()&&facts.stream().noneMatch(f->Objects.equals(f.get("projectKey"),key(card))))
                facts.add(Map.of("owner","PROJECT_FACT","projectKey",key(card),"projectName",Objects.toString(card.get("name"),"项目"),
                        "text","本轮项目说明缺少已核对的原文证据","reason","NO_VERIFIED_PROJECT_EVIDENCE"));
        }
        if(status.equals("PURPOSE_REVIEW_PARTIAL")&&facts.isEmpty())facts.add(Map.of("owner","PROJECT_FACT","text","候选用途尚未完成核对","reason","REVIEW_INCOMPLETE"));
        Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
        for(var fact:facts)unique.putIfAbsent(Objects.toString(fact.get("projectKey"),"")+":"+Objects.toString(fact.get("requirementId"),"")+":"+fact.get("text"),fact);
        var projectFacts=new ArrayList<>(unique.values());
        String coverage=Objects.toString(map(result.get("questionCoverage")).get("status"),"NOT_APPLICABLE");
        String decision,next;
        if(outage){decision="VERIFICATION_UNAVAILABLE";next="RETRY_VERIFICATION";result.remove("pendingQuestion");result.remove("question");}
        else if(!user.isEmpty()){decision=projectFacts.isEmpty()?"USER_INPUT_REQUIRED":"MIXED_GAPS";next="ASK_USER";}
        else if(!choices.isEmpty()){decision="USER_DECISION_REQUIRED";next="ASK_PREFERENCE";}
        else if(!projectFacts.isEmpty()){decision=Set.of("PARTIAL","UNASSESSED").contains(coverage)?"PARTIALLY_SUPPORTED":"PROJECT_FACTS_UNVERIFIED";next="VERIFY_PROJECT_FACTS";}
        else if(Set.of("NO_MATCH","NO_VERIFIED_MATCH").contains(status)){decision="NO_VERIFIED_CANDIDATE";next="SEARCH_OR_REVIEW_CANDIDATES";}
        else if(!rows(result.get("references")).isEmpty()){decision="SUPPORTED";next="ANSWER_VERIFIED_FACTS";}
        else {decision="NOT_REQUIRED";next="NONE";}
        result.put("evidenceDecision",Map.of("version",VERSION,"status",decision,"nextAction",next,"coverage",coverage,
                "userInputs",List.copyOf(user),"userChoices",List.copyOf(choices),"projectFacts",List.copyOf(projectFacts)));
    }
    static Map<String,Object> requirementGap(Map<String,Object> card,Map<String,Object> match) {
        Map<String,Object> gap=new LinkedHashMap<>();gap.put("owner","PROJECT_FACT");gap.put("projectKey",key(card));gap.put("projectName",Objects.toString(card.get("name"),"项目"));
        gap.put("requirementId",Objects.toString(match.get("requirementId"),""));gap.put("requirementRevision",match.getOrDefault("requirementRevision",0L));
        gap.put("text",Objects.toString(match.get("requirement"),"必要条件"));gap.put("verdict",match.getOrDefault("verdict","UNKNOWN"));gap.put("reason",Objects.toString(match.get("reason"),"当前项目资料尚未核实"));
        return gap;
    }
    private static String key(Map<String,Object> card){return card.get("type")+":"+card.get("id");}
}
