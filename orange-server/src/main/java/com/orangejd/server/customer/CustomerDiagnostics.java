package com.orangejd.server.customer;

import java.util.*;

/** Counterfactual counts over one eligible catalog/document snapshot; never mutates confirmed conditions. */
public final class CustomerDiagnostics {
    private CustomerDiagnostics() {}
    static final List<String> HARD=List.of("language","sourceType","difficulty","maxDurationDays","minDurationDays","topic","allowDocker","allowDatabase","allowSecrets","allowHardware");
    static Map<String,Object> counts(CustomerProjects service,List<Map<String,Object>> pool,Map<String,Object> state) {
        Set<String> known=new HashSet<>(),possible=new HashSet<>();
        for(var p:pool) {
            if(service.accepts(p,state)) known.add(CustomerProjects.identity(p));
            else if(service.explorable(p,state)) possible.add(CustomerProjects.identity(p));
        }
        possible.removeAll(known);
        return Map.of("confirmed",known.size(),"uncertain",possible.size(),"total",known.size()+possible.size());
    }
    public static Map<String,Object> evaluate(CustomerProjects service,List<Map<String,Object>> pool,Map<String,Object> state) {
        if(CustomerPurpose.unavailable(state))return Map.of("status","RELEVANCE_UNAVAILABLE","reviewStatus",CustomerPurpose.status(state),"alternatives",List.of(),"answer",CustomerPurpose.explanation(state));
        var base=counts(service,pool,state); List<Map<String,Object>> options=new ArrayList<>();
        for(String field:HARD) {
            if(field.equals("topic") || !state.containsKey(field) || field.startsWith("allow") && !Boolean.FALSE.equals(state.get(field))) continue;
            Map<String,Object> removed=new LinkedHashMap<>(); removed.put(field,null);
            add(service,pool,state,base,options,removed);
            if(field.equals("maxDurationDays") && state.get(field) instanceof Number n) {
                pool.stream().map(p->CustomerProjects.durationDays((String)p.get("duration"))).filter(Objects::nonNull).filter(days->days>n.intValue()).distinct().sorted().limit(30)
                    .forEach(days->add(service,pool,state,base,options,Map.of(field,days)));
            }
            if(field.equals("minDurationDays") && state.get(field) instanceof Number n) {
                pool.stream().map(p->CustomerDuration.estimate((String)p.get("duration"),false)).filter(Objects::nonNull).filter(days->days<n.intValue()).distinct().sorted(Comparator.reverseOrder()).limit(30)
                    .forEach(days->add(service,pool,state,base,options,Map.of(field,days)));
            }
            if(field.equals("language")) pool.stream().map(p->Objects.toString(p.get("language"),"")).filter(v->!v.isBlank()&&!v.equals(state.get(field))).distinct().sorted().limit(30)
                .forEach(language->add(service,pool,state,base,options,Map.of(field,language)));

        }
        // Only if no single change helps, calculate pairs and label both changes explicitly.
        if(options.isEmpty()) for(int i=0;i<HARD.size();i++) for(int j=i+1;j<HARD.size();j++) {
            String a=HARD.get(i),b=HARD.get(j);
            if(a.equals("topic")||b.equals("topic")||!state.containsKey(a)||!state.containsKey(b)||a.startsWith("allow")&&!Boolean.FALSE.equals(state.get(a))||b.startsWith("allow")&&!Boolean.FALSE.equals(state.get(b))) continue;
            Map<String,Object> change=new LinkedHashMap<>(); change.put(a,null);change.put(b,null);add(service,pool,state,base,options,change);
        }
        // Prefer concrete bounded changes before completely removing a limit, then confirmed results.
        options.sort(Comparator.<Map<String,Object>>comparingInt(o->((Map<?,?>)o.get("changes")).size())
            .thenComparingInt(o->((Map<?,?>)o.get("changes")).values().stream().anyMatch(Objects::isNull)?1:0)
            .thenComparingDouble(o->((Map<?,?>)o.get("changes")).entrySet().stream().filter(e->e.getValue() instanceof Number && state.get(e.getKey()) instanceof Number).mapToDouble(e->Math.abs(((Number)e.getValue()).doubleValue()-((Number)state.get(e.getKey())).doubleValue())).sum())
            .thenComparing(Comparator.comparingInt((Map<String,Object> o)->(Integer)o.get("confirmed")).reversed()));
        List<Map<String,Object>> selected=new ArrayList<>(); Set<String> fields=new HashSet<>();
        for(var option:options) if(fields.add(((Map<?,?>)option.get("changes")).keySet().toString())) {selected.add(option);if(selected.size()==3)break;}
        Map<String,Object> hard=new LinkedHashMap<>(); HARD.forEach(k->{if(state.containsKey(k))hard.put(k,state.get(k));});
        return Map.of("current",base,"available",counts(service,pool,Map.of()),"conditions",hard,"alternatives",selected,"countUnit","DISTINCT_REPOSITORY","calculatedAt",java.time.Instant.now().toString());
    }
    static void add(CustomerProjects service,List<Map<String,Object>> pool,Map<String,Object> original,Map<String,Object> base,List<Map<String,Object>> options,Map<String,Object> changes) {
        Map<String,Object> candidate=new LinkedHashMap<>(original); changes.forEach((k,v)->{if(v==null)candidate.remove(k);else candidate.put(k,v);});
        var count=counts(service,pool,candidate);
        if((Integer)count.get("confirmed")<=(Integer)base.get("confirmed") && (Integer)count.get("total")<=(Integer)base.get("total"))return;
        List<String> labels=new ArrayList<>(); changes.forEach((k,v)->labels.add(v==null?CustomerState.label(k)+"不限":CustomerState.label(k)+"改为"+display(k,v)));
        Map<String,Object> option=new LinkedHashMap<>(count); option.put("changes",changes);option.put("label",String.join("，并且",labels));options.add(option);
    }
    public static String display(String field,Object value) {
        if(value==null)return "不限";
        if(field.equals("goal"))return CustomerGoals.display(value);
        if(field.equals("topic"))return CustomerTopics.display(value);
        if(field.equals("verificationMode"))return "STRICT".equals(value)?"仅已核实候选":"允许相关的待核实候选";
        if(field.endsWith("DurationDays"))return value+"天";
        if(field.equals("sourceType"))return "BEGINNER_PROJECT".equals(value)?"学习项目分类":"求职项目分类";
        if(value instanceof Boolean allowed)return allowed?"允许使用":"不使用";
        return value.toString();
    }
    public static String describe(Map<String,Object> diagnosis) {
        if("RELEVANCE_UNAVAILABLE".equals(diagnosis.get("status")))return Objects.toString(diagnosis.get("answer"),"当前核对尚未完成，不能判断是否有符合条件的项目。条件已保留。");
        Map<?,?> current=(Map<?,?>)diagnosis.get("current");
        boolean anyKnown=((Number)current.get("confirmed")).intValue()>0;
        boolean anyUncertain=((Number)current.get("uncertain")).intValue()>0;
        StringBuilder out=new StringBuilder(anyKnown?"可以按当前条件继续考察项目，也可以调整范围。":anyUncertain?"找到了一些相关项目，但部分要求还缺少资料支持。可以先核对这些项目，也可以调整范围。":"本次已检查的候选中，暂未确认符合当前组合要求的项目；这不代表整个知识库都没有，我们可以继续查找。");
        List<?> alternatives=(List<?>)diagnosis.get("alternatives");
        if(alternatives.isEmpty())return out.append("\n暂时还没有核对出有效的调整方案。我会保留你的方向，不自动换成其他领域。可以从原目标里选一个最重要的功能继续找，或补充一个参考项目；目前不能保证放宽时间或语言就有合适结果。你最想先实现哪部分？").toString();
        out.append("\n根据现有资料，可以考虑以下调整；每个方案都保留其余条件：");
        for(Object raw:alternatives) {Map<?,?> o=(Map<?,?>)raw;out.append("\n- ").append(o.get("label")).append(((Number)o.get("confirmed")).intValue()>0?"，可以找到通过当前字段检查的项目，具体功能还需核对。":"，可以继续查看相关项目，不过仍有条件需要核实。");}
        return out.append("\n尚未修改条件。你愿意先尝试哪一种调整？也可以告诉我哪项要求必须保留。").toString();
    }
}
