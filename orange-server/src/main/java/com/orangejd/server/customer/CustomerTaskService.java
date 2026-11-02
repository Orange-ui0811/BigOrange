package com.orangejd.server.customer;

import java.util.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Task and question lifecycle, independent of a single graph execution. */
public final class CustomerTaskService {
    private CustomerTaskService() {}
    public static String operation(Map<String,Object> action,String raw) {
        if(raw.strip().matches("(?:先)?(?:跳过|不知道|先看看|先看项目)[。！!]*"))return "SKIP_QUESTION";
        String fallback=recordOnly(raw)?"RECORD_ONLY":"";
        var control=map(action.get("taskAction"));String op=Objects.toString(control.get("operation"),""),quote=Objects.toString(control.get("quote"),"");
        if(quote.isBlank()||!raw.contains(quote))return fallback;
        if(raw.matches("(?s).*(不要|别|不想|暂不)(取消|暂停|切换|更换|结束|选择|选定).*"))return fallback;
        if(raw.matches("(?s).*(如何|怎么|是否|能否|如果).*" )&&!raw.matches("(?s).*(请|帮我|现在就).*"))return fallback;
        // Destructive/control transitions require both a grounded quote and an explicit request.
        String expression=switch(op) {
            case "PAUSE_TASK"->".*(暂停|先停|晚点再找|等会再找).*";
            case "RESUME_TASK"->".*(继续|恢复|接着).*";
            case "SWITCH_TASK"->".*(换个目标|换个任务|换个方向|另一个需求|不找.*了|改找|现在.*找).*";
            case "CANCEL_TASK"->".*(取消.*任务|不找了|结束.*任务).*";
            case "SELECT_PROJECT"->".*(就选|选定|选择|选第[一二三123]个|决定用|就要).*";
            case "SKIP_QUESTION"->".*(跳过|不知道|先看).*";
            case "RECORD_ONLY"->".*(记录|先别找|不用推荐).*";
            default->"(?!)";
        };
        return raw.matches("(?s)"+expression)?op:fallback;
    }
    private static boolean recordOnly(String raw){return raw.matches("(?s).*(先记住|先记录|只记录|仅记录|不用推荐|先别找|不要搜索).*");}
    public static void prepare(Map<String,Object> state,Map<String,Object> action,String raw,String run) {
        String op=operation(action,raw);action.put("validatedTaskAction",op);
        if("SKIP_QUESTION".equals(op)&&question(state).isEmpty()&&raw.strip().matches("(不知道|跳过)[。！!]*")) {op="";action.put("validatedTaskAction",op);}
        if(Set.of("CANCELLED","COMPLETED").contains(Objects.toString(task(state).get("status"),"")) && !Set.of("SWITCH_TASK","RESUME_TASK").contains(op)) {
            action.put("changes",Map.of());action.put("profileDefaults",Map.of());action.put("requirementProposals",List.of());
        }
        if("SWITCH_TASK".equals(op)) {newTask(state,run);clearProjection(state);event(state,"TASK_SWITCHED",Map.of(),run);}
        if("RESUME_TASK".equals(op)) {
            String requested=Objects.toString(map(action.get("taskAction")).get("taskId"),"");
            var paused=rows(workflow(state).get("archivedTasks")).stream().filter(t->"PAUSED".equals(t.get("status"))&&(requested.isBlank()||requested.equals(t.get("id")))).toList();
            if("PAUSED".equals(task(state).get("status"))&&(requested.isBlank()||requested.equals(task(state).get("id")))) {task(state).put("status","DISCOVERING");workflow(state).put("activeTaskId",task(state).get("id"));}
            else if(paused.size()==1) {
                var archived=new ArrayList<>(rows(workflow(state).get("archivedTasks")));var target=paused.getFirst();archived.remove(target);
                var current=task(state);if(!Set.of("COMPLETED","CANCELLED").contains(current.get("status")))current.put("status","PAUSED");archived.add(current);
                workflow(state).put("archivedTasks",archived);workflow(state).put("task",target);workflow(state).put("activeTaskId",target.get("id"));clearProjection(state);
                target.put("status","DISCOVERING");target.put("matchesStale",true);restoreProjection(state);
                closeQuestion(state,"SUPERSEDED",run);target.remove("currentListId");
            } else action.put("workflowReply","请说明要恢复哪一个任务："+paused.stream().map(t->Objects.toString(t.get("id"),"")).toList());
        }
        if(Set.of("PAUSE_TASK","CANCEL_TASK","SELECT_PROJECT","SKIP_QUESTION").contains(op)) {action.put("changes",Map.of());action.put("profileDefaults",Map.of());action.put("requirementProposals",List.of());}
    }
    public static void restoreProjection(Map<String,Object> state) {
        for(var r:active(state))if(CustomerState.FIELDS.contains(Objects.toString(r.get("field"),"")) && r.get("normalizedValue")!=null)state.put(r.get("field").toString(),r.get("normalizedValue"));
    }
    public static void apply(Map<String,Object> state,Map<String,Object> action,String raw,Object messageId,String run,CustomerReferenceResolver.Resolution reference) {
        var wf=workflow(state);var task=task(state);String op=Objects.toString(action.get("validatedTaskAction"),"");
        wf.put("sessionRevision",number(wf,"sessionRevision")+1);
        if(Boolean.TRUE.equals(wf.get("migrationPending"))) {
            if(!run.equals(wf.get("migrationFirstRun")) && raw.strip().matches("(确认沿用|沿用这些需求)[。！!]*")) {
                for(var r:requirements(state)) {r.put("status","ACTIVE");var source=new LinkedHashMap<>(map(r.get("source")));source.put("confirmation",Map.of("messageId",Objects.toString(messageId,""),"quote",raw));r.put("source",source);}
                wf.remove("migrationPending");task.put("requirementVersion",number(task,"requirementVersion")+1);restoreProjection(state);closeQuestion(state,"ANSWERED",run);
                action.put("workflowReply","已沿用这些有原话依据的需求。可以补充或修改，也可以让我开始查找。");return;
            }
            if(!run.equals(wf.get("migrationFirstRun")) && raw.strip().matches("(重新开始|不沿用)[。！!]*")) {
                wf.remove("migrationPending");task.put("status","CANCELLED");newTask(state,run);clearProjection(state);action.put("workflowReply","我们从新需求开始。你希望项目解决什么问题？");return;
            }
            ask(state,"MIGRATION","旧会话中有以下带原话依据的需求，是否沿用？",List.of("确认沿用","重新开始"),"migration",run);
            action.put("workflowReply","旧会话记录仍保留。请先确认是否沿用：\n"+String.join("\n",requirements(state).stream().filter(r->"PENDING".equals(r.get("status"))).map(r->"- "+r.get("text")).toList()));return;
        }
        if("PAUSE_TASK".equals(op)||"CANCEL_TASK".equals(op)) {
            task.put("status","PAUSE_TASK".equals(op)?"PAUSED":"CANCELLED");wf.remove("activeTaskId");
            if("CANCEL_TASK".equals(op))closeQuestion(state,"CANCELLED",run);
            action.put("workflowReply","PAUSE_TASK".equals(op)?"已暂停这个任务，需求会保留；你可以插问其他问题，或说继续查找。":"已取消这个任务。历史需求保留为记录，不会再继续查找。");event(state,op,Map.of(),run);return;
        }
        if("SELECT_PROJECT".equals(op)) {
            if(reference.ambiguous()||reference.references().size()!=1 || Boolean.TRUE.equals(task.get("matchesStale")))action.put("workflowReply","请从当前有效列表明确选择一个项目；需求发生变化时需要先重新核对。");
            else {task.put("selectedProject",reference.references().getFirst().get("project"));task.put("status","COMPLETED");closeQuestion(state,"CANCELLED",run);wf.remove("activeTaskId");action.put("workflowReply","已记录你选定的项目。选定不等于导入；请使用该项目卡片的导入入口，服务端会再次校验资料和资格。");}
            return;
        }
        if(reference.ambiguous()) {ask(state,"REFERENCE","你指的是哪一批、哪一个项目？请在对应列表下指定项目。",List.of(),"reference:"+raw,run);action.put("workflowReply","目前不能唯一确定你指的项目，先确认对象再继续。");return;}
        var answer=map(action.get("questionAnswer"));var question=question(state);
        boolean answersCurrent=!question.isEmpty() && Objects.equals(question.get("id"),answer.get("questionId")) && answer.get("quote") instanceof String quote && !quote.isBlank()&&raw.contains(quote)
            && !Set.of("QUESTION","DETAIL","COMPARE","STATE","EXPLAIN").contains(action.get("intent"));
        boolean invalidated=map(question.get("requirementDependencies")).entrySet().stream().anyMatch(dep->active(state).stream().noneMatch(r->dep.getKey().equals(r.get("id"))&&dep.getValue() instanceof Number version&&number(r,"revision")==version.longValue()));
        if(invalidated&&!answersCurrent) {closeQuestion(state,"SUPERSEDED",run);event(state,"QUESTION_INVALIDATED",Map.of("questionId",question.get("id")),run);}
        if(answersCurrent) {
            String quote=answer.get("quote").toString();
            question.put("answerMessageId",Objects.toString(messageId,""));question.put("answerQuote",quote);question.put("revalidatedAtVersion",number(task,"requirementVersion"));closeQuestion(state,"ANSWERED",run);
        }
        if("SKIP_QUESTION".equals(op)) {closeQuestion(state,"SKIPPED",run);action.put("intent","RECOMMEND");task.put("previewRequested",true);}
        if("RECORD_ONLY".equals(op)||recordOnly(raw)) {
            if(!action.containsKey("workflowReply"))action.put("workflowReply",("SWITCH_TASK".equals(op)?"已切换到新任务。":"RESUME_TASK".equals(op)?"已恢复指定任务。":"")+"已记录本轮明确提出的需求，本轮不搜索项目。");return;
        }
        if(Set.of("PAUSED","CANCELLED","COMPLETED").contains(task.get("status")) && Set.of("RECOMMEND","COLLECT").contains(action.get("intent"))) {
            action.put("workflowReply","当前任务已暂停或结束。请明确恢复任务，或说明要换一个新目标。");return;
        }
        if(action.containsKey("workflowReply"))return;
        if(!Set.of("COLLECT","RECOMMEND").contains(action.get("intent")))return;
        if(state.get("pendingConditions") instanceof List<?> pending&&!pending.isEmpty()) {
            String q=Objects.toString(map(pending.getFirst()).get("question"),"请澄清这一项要求。");ask(state,"REQUIREMENT",q,List.of(),"conflict:"+q,run);action.put("workflowReply","其他明确需求已经保存，还有一处需要确认。");return;
        }
        if(!question(state).isEmpty() && !"SKIP_QUESTION".equals(op)) {
            // The same question stays open across interruptions; never count an unrelated reply as its answer.
            action.put("workflowReply","已记下本轮信息。还有一个会影响筛选的问题需要确认，也可以选择先看候选。");return;
        }
        var proposed=map(action.get("clarification"));String q=Objects.toString(proposed.get("question"),"");
        boolean ready=state.containsKey("topic") || state.containsKey("language") || active(state).stream().anyMatch(r->Set.of("FEATURE","GOAL").contains(r.get("kind")));
        boolean skip=Boolean.TRUE.equals(task.get("previewRequested"))||"SKIP_QUESTION".equals(op);
        if(!skip && (!ready || !q.isBlank() && !Boolean.TRUE.equals(task.get("initialQuestionAsked")))) {
            if(q.isBlank())q="你希望项目解决什么问题，或用于什么场景？";
            ask(state,"REQUIREMENT",q,proposed.get("options") instanceof List<?> options?options:List.of(),"initial",run);
            task.put("initialQuestionAsked",true);action.put("workflowReply","先确认一个最影响项目选择的问题，然后就可以试着找候选。");return;
        }
        task.put("status","READY_TO_SEARCH");
    }
    public static void ask(Map<String,Object> state,String kind,String text,List<?> options,String fingerprint,String run) {
        if(text.length()>300)text=text.substring(0,300);
        var old=question(state);if(fingerprint.equals(old.get("validityFingerprint")))return;
        closeQuestion(state,"SUPERSEDED",run);var task=task(state);var q=new LinkedHashMap<String,Object>();
        q.put("id",id("q-"));q.put("taskId",task.get("id"));q.put("kind",kind);q.put("text",text);q.put("status","OPEN");q.put("options",options.stream().filter(String.class::isInstance).map(Object::toString).filter(s->s.length()<=80).limit(3).toList());q.put("allowFreeText",true);q.put("createdRequirementVersion",number(task,"requirementVersion"));q.put("validityFingerprint",fingerprint);q.put("sourceRunId",run);
        Map<String,Object> dependencies=new LinkedHashMap<>();
        for(var r:active(state))if(!fingerprint.startsWith("tradeoff:")||fingerprint.endsWith(":"+r.get("id")))dependencies.put(r.get("id").toString(),number(r,"revision"));
        q.put("requirementDependencies",dependencies);q.put("relatedRequirementIds",new ArrayList<>(dependencies.keySet()));
        var questions=new ArrayList<>(rows(task.get("questions")));questions.add(q);task.put("questions",questions);task.put("openQuestionId",q.get("id"));task.put("status","WAITING_USER");
    }
    static void closeQuestion(Map<String,Object> state,String status,String run){var q=question(state);if(!q.isEmpty()){q.put("status",status);q.put("closedRunId",run);if("WAITING_USER".equals(task(state).get("status")))task(state).put("status","DISCOVERING");}task(state).remove("openQuestionId");}
    public static void finish(Map<String,Object> state,Map<String,Object> result,String intent,String run) {
        var task=task(state);var cards=rows(result.get("projects"));
        if(!cards.isEmpty()&&!Set.of("DETAIL","COMPARE").contains(intent)) {
            String listId=id("list-");var lists=new ArrayList<>(rows(task.get("lists")));
            lists.add(Map.of("id",listId,"taskId",task.get("id"),"requirementVersion",number(task,"requirementVersion"),"runId",run,"projects",cards.stream().map(CustomerProjectFeedback::identity).toList()));
            task.put("lists",lists);task.put("currentListId",listId);task.put("matchesStale",false);task.put("status","PRESENTED");task.remove("previewRequested");result.put("listId",listId);
            var uncertain=cards.stream().flatMap(card->rows(card.get("requirementMatches")).stream()).filter(m->("HARD".equals(m.get("strength"))||Boolean.TRUE.equals(m.get("requiredForVerified")))&&!"SATISFIED".equals(m.get("verdict"))).findFirst();
            if(uncertain.isPresent() && question(state).isEmpty()&&!"UNAVAILABLE".equals(result.get("verificationStatus"))&&!"UNAVAILABLE".equals(result.get("requirementVerificationStatus"))) {
                String fingerprint="tradeoff:"+number(task,"requirementVersion")+":"+uncertain.get().get("requirementId");
                boolean asked=rows(task.get("questions")).stream().anyMatch(q->fingerprint.equals(q.get("validityFingerprint")));
                if(!asked)ask(state,"TRADEOFF","这些候选的“"+uncertain.get().get("requirement")+"”还未完整核实。你愿意先考察，还是只看已核实满足必要条件的项目？",List.of("先考察待核实候选","只要已核实满足的项目"),fingerprint,run);
            }
        }
        if(cards.isEmpty()&&"EVALUATING".equals(task.get("status")))task.put("status","READY_TO_SEARCH");
        task.put("lastOutcome",result.getOrDefault("status","UNKNOWN"));
        // Interruption must preserve the current task question, not publish a generic profile question.
        result.remove("pendingQuestion");result.remove("question");var q=question(state);
        if(!"QUESTION".equals(intent)&&!q.isEmpty()&&!Set.of("PAUSED","CANCELLED","COMPLETED").contains(task.get("status"))) {result.put("pendingQuestion",q.get("text"));result.put("question",q);}
        result.put("task",visible(state));result.put("workflowVersion",2);
        if(workflow(state).containsKey("migrationNotice")) {result.put("notice",Objects.toString(result.get("notice"),"")+workflow(state).get("migrationNotice"));workflow(state).remove("migrationNotice");}
    }
}
