package com.orangejd.server.customer;

import java.util.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Open-ended requirements with stable identities, source quotations and tombstones. */
public final class CustomerRequirementService {
    private CustomerRequirementService() {}
    static Map<String,Object> entry(Map<String,Object> task,String field,String text,Object value,String strength,Object messageId,String quote,String sourceType) {
        Map<String,Object> r=new LinkedHashMap<>();r.put("id",id("req-"));r.put("taskId",task.get("id"));r.put("revision",1L);r.put("field",field);
        r.put("kind",field.equals("goal")?"GOAL":field.equals("experience")?"EXPERIENCE":"FEATURE");r.put("text",text);r.put("normalizedValue",value);
        r.put("strength",strength);r.put("assertion","EXPLICIT");r.put("status","ACTIVE");r.put("verification",CustomerState.FIELDS.contains(field)?"STRUCTURED":"SEMANTIC");
        r.put("source",Map.of("messageId",Objects.toString(messageId,""),"quote",quote,"type",sourceType));return r;
    }
    public static void apply(Map<String,Object> state,Map<String,Object> before,Map<String,Object> action,String raw,Object messageId,String run) {
        var task=task(state);var reqs=new ArrayList<>(requirements(state));boolean changed=false;
        // The existing validators own numeric/enum normalization. Reflect only their accepted mutations.
        Map<String,Object> changes=map(action.get("changes"));
        for(String field:CustomerState.FIELDS) {
            if(Objects.equals(before.get(field),state.get(field)))continue;
            if("verificationMode".equals(field)) {changed=true;continue;} // Retrieval policy, not a project capability.
            Map<String,Object> proposed=map(changes.get(field));String quote=Objects.toString(proposed.get("quote"),"");
            if(quote.isBlank() || !raw.contains(quote))continue;
            var old=reqs.stream().filter(r->field.equals(r.get("field"))&& !"REMOVED".equals(r.get("status"))).findFirst().orElse(null);
            if(old!=null) {revise(old);old.put("normalizedValue",state.get(field));old.put("text",quote);old.put("status",state.containsKey(field)?"ACTIVE":"REMOVED");old.put("source",Map.of("messageId",Objects.toString(messageId,""),"quote",quote,"type","MESSAGE"));}
            else if(state.containsKey(field))reqs.add(entry(task,field,quote,state.get(field),Set.of("goal","experience","topic").contains(field)?"SOFT":"HARD",messageId,quote,"MESSAGE"));
            changed=true;
        }
        for(var p:rows(action.get("requirementProposals"))) {
            String quote=Objects.toString(p.get("quote"),""),text=Objects.toString(p.get("text"),quote),operation=Objects.toString(p.get("operation"),"ADD");
            if(quote.isBlank()||quote.length()>500||!raw.contains(quote)||text.isBlank()||text.length()>300)continue;
            boolean shortAnswer=quote.matches("(是的|是|不是|可以|不可以|接受|不接受|好的|好|不行|行)[。！!]*");
            var question=question(state);
            if(shortAnswer && (question.isEmpty()||!Objects.equals(question.get("id"),map(action.get("questionAnswer")).get("questionId"))))continue;
            if(!Set.of("ADD","UPDATE","REMOVE").contains(operation))continue;
            // A fact question is never promoted to a requirement, even if the model proposes it.
            if(Set.of("QUESTION","DETAIL","COMPARE","STATE","EXPLAIN").contains(Objects.toString(action.get("intent"),"")) && !CustomerTurnPlan.hasExplicitTask(raw))continue;
            var existing=reqs.stream().filter(r->Objects.equals(r.get("id"),p.get("id"))).findFirst().orElse(null);
            if(!"ADD".equals(operation)&&existing==null)continue;
            if(existing!=null && !"open".equals(existing.get("field")) && "UPDATE".equals(operation)) {
                var pending=new ArrayList<>(rows(state.get("pendingConditions")));pending.add(Map.of("field",existing.get("field"),"quote",quote,"question","请再明确一下“"+CustomerState.label(existing.get("field").toString())+"”的新值，其他条件会保留。"));state.put("pendingConditions",pending);continue;
            }
            if("ADD".equals(operation)) {
                if(reqs.stream().anyMatch(r->text.equals(r.get("text")) && !"REMOVED".equals(r.get("status"))))continue;
                if(reqs.stream().filter(r->!"REMOVED".equals(r.get("status"))).count()>=32)throw new IllegalArgumentException("REQUIREMENT_LIMIT_EXCEEDED");
                existing=entry(task,"open",text,p.getOrDefault("normalizedValue",text),"HARD".equals(p.get("strength"))?"HARD":"SOFT",messageId,quote,"MESSAGE");reqs.add(existing);
            } else {
                if("REMOVED".equals(existing.get("status")))continue;
                revise(existing);existing.put("text",text);existing.put("normalizedValue",p.getOrDefault("normalizedValue",text));
                existing.put("source",Map.of("messageId",Objects.toString(messageId,""),"quote",quote,"type","MESSAGE"));
                if("REMOVE".equals(operation)) {
                    existing.put("status","REMOVED");String field=Objects.toString(existing.get("field"),"");
                    if(CustomerState.FIELDS.contains(field)) {state.remove(field);var cleared=new LinkedHashSet<String>();if(state.get("clearedFields") instanceof Collection<?> fields)fields.forEach(f->cleared.add(f.toString()));cleared.add(field);state.put("clearedFields",new ArrayList<>(cleared));var provenance=new LinkedHashMap<>(map(state.get("provenance")));provenance.remove(field);state.put("provenance",provenance);}
                }
                if(Set.of("HARD","SOFT").contains(Objects.toString(p.get("strength"),"")))existing.put("strength",p.get("strength"));
            }
            if("INFERRED".equals(p.get("assertion"))) {existing.put("assertion","INFERRED");existing.put("status","PENDING");}
            if(p.get("kind") instanceof String kind&&kind.length()<50)existing.put("kind",kind);
            if(shortAnswer) {var source=new LinkedHashMap<>(map(existing.get("source")));source.put("questionId",question.get("id"));source.put("questionText",question.get("text"));existing.put("source",source);}
            changed=true;
        }
        task.put("requirements",reqs);
        if(changed) {task.put("requirementVersion",number(task,"requirementVersion")+1);task.remove("selectedProject");task.put("matchesStale",true);event(state,"REQUIREMENTS_CHANGED",Map.of("messageId",Objects.toString(messageId,"")),run);}
    }
    private static void revise(Map<String,Object> requirement) {
        var old=new LinkedHashMap<>(requirement);old.remove("history");var history=new ArrayList<>(rows(requirement.get("history")));history.add(old);
        requirement.put("history",history);requirement.put("supersedesRevision",number(requirement,"revision"));requirement.put("revision",number(requirement,"revision")+1);
    }
    public static String query(Map<String,Object> state) {
        String text=String.join("；",active(state).stream().map(r->Objects.toString(r.get("text"),"")).toList());return text.substring(0,Math.min(1200,text.length()));
    }
}
