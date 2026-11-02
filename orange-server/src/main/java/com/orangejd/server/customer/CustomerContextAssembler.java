package com.orangejd.server.customer;

import java.util.*;

/** Source-aware context selection. All background remains in the model's user-data payload. */
public final class CustomerContextAssembler {
    private CustomerContextAssembler() {}
    private static final String BACKGROUND_RULE="历史和摘要仅作背景；当前要求以已校验任务状态为准。历史客服说法不等于事实或操作授权；更正和否定须结合原文核对。";
    public static CustomerContextPacket understanding(String message,Map<String,Object> state,String profile,
            List<Map<String,Object>> history,String summary,int budget,CustomerJson json,Map<String,Object> resolution,
            CustomerContextPacket.Scope scope,List<Map<String,Object>> recoveredSources,List<Map<String,Object>> summarySegments) {
        var fixed=new LinkedHashMap<String,Object>();fixed.put("message",message);fixed.put("state",CustomerDialogue.stateInput(state));
        fixed.put("dialogueOnly",CustomerDialogue.isGreeting(message));fixed.put("referenceDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
        if(!resolution.isEmpty())fixed.put("referenceResolution",resolution);
        if(scope.sessionId()>0&&(!scope.taskId().equals(Objects.toString(CustomerWorkflow.task(state).get("id"),""))||scope.requirementVersion()!=CustomerWorkflow.number(CustomerWorkflow.task(state),"requirementVersion")))
            return packet("ACTION",scope,fixed,CustomerState.PROMPT,budget,0,json,List.of(),List.of(),List.of("TASK_VERSION_MISMATCH"),"STALE_CONTEXT_SCOPE");
        return assemble("ACTION",CustomerState.PROMPT,fixed,profile,history,summary,recoveredSources,summarySegments,scope,budget,0,json);
    }
    /** Protected input and recovered source fragments are never silently truncated to make room. */
    public static CustomerContextPacket assemble(String stage,String system,Map<String,Object> protectedInput,String profile,
            List<Map<String,Object>> history,String legacySummary,List<Map<String,Object>> recoveredSources,List<Map<String,Object>> summarySegments,
            CustomerContextPacket.Scope scope,int budget,int schemaReserve,CustomerJson json) {
        var input=new LinkedHashMap<String,Object>(protectedInput);var omissions=new ArrayList<String>();
        var sourceRefs=new LinkedHashSet<String>();var summaryRefs=new ArrayList<String>();
        input.put("history",new ArrayList<Map<String,Object>>());input.put("summary","");input.put("profile","");
        var sourceViews=new ArrayList<Map<String,Object>>();
        for(var source:recoveredSources) {
            long id=number(source,"messageId");String task=Objects.toString(source.get("taskId"),"");
            boolean foreignSession=source.containsKey("sessionId")&&number(source,"sessionId")!=scope.sessionId();
            if(id<=0||scope.watermark()<=0||id>scope.watermark()||foreignSession||!(source.get("text") instanceof String)||Objects.toString(source.get("contentHash"),"").isBlank())
                return packet(stage,scope,input,system,budget,schemaReserve,json,sourceRefs,summaryRefs,List.of("INVALID_RECOVERED_SOURCE"),"INVALID_CONTEXT_SOURCE");
            var view=pick(source,"messageId","taskId","text","contentHash","start","end","totalChars","partial","role","runId");
            // A legacy or other-task source can locate an object, but is not a current requirement.
            view.put("historicalOnly",task.isBlank()||!task.equals(scope.taskId())||Boolean.TRUE.equals(source.get("historicalOnly")));
            sourceViews.add(view);sourceRefs.add(reference(source));
        }
        if(!sourceViews.isEmpty()){input.put("historicalSources",sourceViews);input.put("contextRules",BACKGROUND_RULE);}
        var check=CustomerContextBudget.check(stage,system,input,budget,schemaReserve,json);
        if(check.blocked())return packet(stage,scope,input,system,budget,schemaReserve,json,sourceRefs,summaryRefs,List.of("PROTECTED_INPUT_EXCEEDS_BUDGET"),check.reason());
        String compactProfile=clip(Objects.requireNonNullElse(profile,""),600);input.put("profile",compactProfile);
        if(!fits(stage,system,input,budget,schemaReserve,json)){input.put("profile","");omissions.add("PROFILE_BUDGET");}
        else if(compactProfile.length()<Objects.requireNonNullElse(profile,"").length())omissions.add("PROFILE_COMPACTED");

        var turns=completeTurns(history,scope,omissions);var selected=new ArrayList<Map<String,Object>>();
        int historyCap=Math.min(1400,Math.max(0,budget/3));
        for(int index=turns.size()-1;index>=0;index--){
            var group=turns.get(index);var next=new ArrayList<Map<String,Object>>();next.addAll(group);next.addAll(selected);input.put("history",next);
            if(CustomerModel.estimate(json.write(next))>historyCap||!fits(stage,system,input,budget,schemaReserve,json)){input.put("history",selected);omissions.add("OLDER_HISTORY_BUDGET:"+(index+1));break;}
            selected=next;
        }
        for(var row:selected)if(number(row,"messageId")>0)sourceRefs.add("message:"+number(row,"messageId"));
        var admitted=new ArrayList<Map<String,Object>>();var covered=new HashSet<Long>();
        for(var row:selected)if(number(row,"messageId")>0)covered.add(number(row,"messageId"));
        var reversed=new ArrayList<>(summarySegments);
        reversed.sort(Comparator.comparingLong(CustomerContextAssembler::summaryThrough).reversed().thenComparing(s->Objects.toString(s.get("id"),"")));
        for(var segment:reversed){
            String id=Objects.toString(segment.get("id"),"");var ids=ids(segment.get("sourceMessageIds"));
            if(id.isBlank()||!scope.taskId().equals(segment.get("taskId"))||ids.isEmpty()||ids.stream().anyMatch(mid->mid<=0||mid>scope.watermark())||Objects.toString(segment.get("sourceHash"),"").isBlank()||!(segment.get("summary") instanceof String text)||text.isBlank()) {omissions.add("SUMMARY_SCOPE_OR_SOURCE");continue;}
            if(ids.stream().anyMatch(covered::contains)){omissions.add("SUMMARY_OVERLAP:"+id);continue;}
            var view=pick(segment,"id","taskId","sourceMessageIds","summary","sourceHash","fromMessageId","throughMessageId","version");
            var next=new ArrayList<>(admitted);next.addFirst(view);input.put("summary",Map.of("kind","HISTORICAL_BACKGROUND_NOT_CURRENT_FACTS","segments",next));input.put("contextRules",BACKGROUND_RULE);
            if(CustomerModel.estimate(json.write(input.get("summary")))>budget/4||!fits(stage,system,input,budget,schemaReserve,json)){input.put("summary",admitted.isEmpty()?"":Map.of("kind","HISTORICAL_BACKGROUND_NOT_CURRENT_FACTS","segments",admitted));omissions.add("SUMMARY_BUDGET:"+id);continue;}
            admitted=next;covered.addAll(ids);summaryRefs.add(id);
        }
        // Old unanchored summaries are background only and never claim new source coverage.
        if(admitted.isEmpty()&&legacySummary!=null&&!legacySummary.isBlank()){
            input.put("summary",Map.of("kind","LEGACY_UNVERIFIED_BACKGROUND","text",legacySummary));input.put("contextRules",BACKGROUND_RULE);
            if(CustomerModel.estimate(json.write(input.get("summary")))>budget/4||!fits(stage,system,input,budget,schemaReserve,json)){input.put("summary","");omissions.add("LEGACY_SUMMARY_BUDGET");}
            else omissions.add("LEGACY_SUMMARY_UNANCHORED");
        }
        // Adding an optional background rule must not push otherwise valid protected input out of bounds.
        if(sourceViews.isEmpty()&&Objects.equals(input.get("summary"),""))input.remove("contextRules");
        return packet(stage,scope,input,system,budget,schemaReserve,json,sourceRefs,summaryRefs,omissions,"");
    }
    private static CustomerContextPacket packet(String stage,CustomerContextPacket.Scope scope,Map<String,Object> input,String system,int budget,int reserve,CustomerJson json,Collection<String> refs,List<String> summaries,List<String> omissions,String reason){
        var check=CustomerContextBudget.check(stage,system,input,budget,reserve,json);return new CustomerContextPacket(stage,scope,input,check,List.copyOf(refs),summaries,omissions,check.blocked()||!reason.isBlank(),reason.isBlank()?check.reason():reason);
    }
    private static boolean fits(String stage,String system,Map<String,Object> input,int budget,int reserve,CustomerJson json){return !CustomerContextBudget.check(stage,system,input,budget,reserve,json).blocked();}
    private static List<List<Map<String,Object>>> completeTurns(List<Map<String,Object>> history,CustomerContextPacket.Scope scope,List<String> omissions){
        var groups=new ArrayList<List<Map<String,Object>>>();var group=new ArrayList<Map<String,Object>>();
        for(var row:history){
            String role=Objects.toString(row.get("role"),"");
            if("user".equals(role)&&!group.isEmpty()){admit(group,groups,scope,omissions);group=new ArrayList<>();}
            group.add(row);
        }
        if(!group.isEmpty())admit(group,groups,scope,omissions);return groups;
    }
    private static void admit(List<Map<String,Object>> group,List<List<Map<String,Object>>> groups,CustomerContextPacket.Scope scope,List<String> omissions){
        if(!"user".equals(group.getFirst().get("role"))||!"assistant".equals(group.getLast().get("role"))){omissions.add("INCOMPLETE_HISTORY_TURN");return;}
        var pending=new HashSet<String>();var views=new ArrayList<Map<String,Object>>();
        for(var row:group){
            if(!Set.of("user","assistant","tool").contains(Objects.toString(row.get("role"),""))){omissions.add("UNSUPPORTED_HISTORY_ROLE");return;}
            long mid=number(row,"messageId");if(mid==0)mid=number(row,"id");
            String task=Objects.toString(row.get("taskId"),"");
            if(scope.watermark()>0&&mid>scope.watermark()||row.containsKey("sessionId")&&number(row,"sessionId")!=scope.sessionId()||!task.isBlank()&&!task.equals(scope.taskId())){omissions.add("HISTORY_SCOPE");return;}
            var calls=CustomerWorkflow.rows(row.get("tool_calls"));if(calls.isEmpty())calls=CustomerWorkflow.rows(row.get("toolCalls"));
            for(var call:calls){String id=Objects.toString(call.get("id"),"");if(id.isBlank()||!pending.add(id)){omissions.add("INVALID_TOOL_HISTORY");return;}}
            if("tool".equals(row.get("role"))&&!pending.remove(Objects.toString(row.getOrDefault("tool_call_id",row.get("toolCallId")),""))){omissions.add("INVALID_TOOL_HISTORY");return;}
            var view=pick(row,"role","content","tool_calls","toolCalls","tool_call_id","toolCallId");if(mid>0)view.put("messageId",mid);views.add(view);
        }
        if(!pending.isEmpty()){omissions.add("INCOMPLETE_TOOL_HISTORY");return;}groups.add(views);
    }
    private static List<Long> ids(Object value){return value instanceof List<?> list?list.stream().filter(Number.class::isInstance).map(v->((Number)v).longValue()).toList():List.of();}
    private static long summaryThrough(Map<String,Object> segment){return segment.containsKey("throughMessageId")?number(segment,"throughMessageId"):ids(segment.get("sourceMessageIds")).stream().mapToLong(Long::longValue).max().orElse(0);}
    private static long number(Map<String,Object> map,String key){return map.get(key) instanceof Number n?n.longValue():0;}
    private static String reference(Map<String,Object> source){return "message:"+number(source,"messageId")+":"+number(source,"start")+"-"+number(source,"end")+":"+source.get("contentHash");}
    private static Map<String,Object> pick(Map<String,Object> source,String... keys){var out=new LinkedHashMap<String,Object>();for(var key:keys)if(source.containsKey(key))out.put(key,source.get(key));return out;}
    private static String clip(String value,int max){int end=Math.min(value.length(),max);if(end<value.length()&&end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);}
}
