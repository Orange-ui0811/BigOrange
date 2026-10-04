package com.orangejd.server.customer;

import java.util.*;

/** System facts are rendered from committed state, never guessed by the conversational model. */
public final class CustomerConditionFacts {
    private CustomerConditionFacts() {}
    public static String route(String message) {
        String m=message.strip();
        // Composite turns must reach semantic extraction; a read-only shortcut must not swallow a task.
        if(CustomerTurnPlan.hasExplicitTask(m))return null;
        // Explain operational questions without another provider call or a counterfactual search.
        if(m.length()<100 && !m.matches("(?s).*(推荐给我|帮我找|开始查找|改成|换成|改为|我想找).*" )
            && (m.matches("(?s).*(用途核对|项目核对|功能核对|相关性核对|用途审核).*(是什么|什么意思|为什么|怎么回事|失败|没完成|未完成).*" )
                || m.matches("(?s).*(什么是|解释.*|为什么|为何).*(用途核对|项目核对|功能核对|相关性核对|用途审核).*" )
                || m.matches("(?s).*(为什么|为何|怎么).*(又失败|一直失败|核对失败|核对未完成|检索失败|服务失败).*" )))return "EXPLAIN";
        if(CustomerState.isConfirmation(m)||CustomerState.isCancellation(m))return "STATE";
        if(m.contains("目标") && m.matches("(?s).*(支持|可选|哪些|有哪些|多选|自定义|多个|同时选择).*" )
            && !m.matches("(?s).*(设置|改成|改为|增加|追加|取消|移除|我想|希望|想要).*"))return "STATE";
        if(m.matches("(?s).*(为什么|为何|怎么).*(没|不|无).*(推荐|项目|结果|候选).*" ) || m.matches("(?s).*(怎么|如何|哪些|什么).*(调整|放宽).*(条件|项目|推荐).*"))return "DIAGNOSE";
        if(m.matches("(?s).*(为什么|为何).*(条件|周期).*(没|不).*(放宽|修改|改变).*"))return "STATE";
        if(!m.matches("(?s).*(改成|换成|改为|放宽到|设置为).*" ) && m.matches("(?s).*(当前|目前|现在|刚才).*(什么条件|哪些条件|的条件|条件是什么|条件有哪些|有什么条件|指定的语言).*"))return "STATE";
        return null;
    }
    public static String changes(Map<String,Object> before,Map<String,Object> after,Map<String,Object> action) {
        List<String> lines=new ArrayList<>();
        CustomerState.FIELDS.stream().sorted().forEach(key->{
            Object a=before.get(key),b=after.get(key);
            if(!Objects.equals(a,b))lines.add(b==null?"已取消“"+CustomerState.label(key)+"”限制":a==null?"已记录"+CustomerState.label(key)+"："+CustomerDiagnostics.display(key,b):"已将"+CustomerState.label(key)+"从"+CustomerDiagnostics.display(key,a)+"调整为"+CustomerDiagnostics.display(key,b));
            else if(!before.containsKey(key)&&!after.containsKey(key)&&action.get("changes") instanceof Map<?,?> c&&c.get(key) instanceof Map<?,?> op&&("CLEAR".equals(op.get("operation"))||op.containsKey("value")&&op.get("value")==null)
                && after.get("clearedFields") instanceof List<?> cleared && cleared.contains(key))lines.add("当前本来就没有“"+CustomerState.label(key)+"”限制，已保持不限");
        });
        return lines.isEmpty()?"":String.join("；",lines)+"。其他已确认条件保持不变。";
    }
    public static String describe(Map<String,Object> state) {
        List<String> lines=new ArrayList<>();
        for(String key:List.of("language","maxDurationDays","minDurationDays","difficulty","sourceType","topic","allowDocker","allowDatabase","allowSecrets","allowHardware","verificationMode","goal","experience"))
            if(state.containsKey(key))lines.add("- "+CustomerState.label(key)+"："+CustomerDiagnostics.display(key,state.get(key)));
        String answer=lines.isEmpty()?"当前没有已确认的项目条件。":"当前已确认条件：\n"+String.join("\n",lines);
        if(state.get("preferences") instanceof List<?> preferences && !preferences.isEmpty()) answer+="\n实践偏好（不作为硬筛选）："+String.join("；",preferences.stream().map(Object::toString).toList());
        answer+="\n目标支持同时选择学习、求职、赚钱/接单/创业、探索技术兴趣，也可记录自己的目标；可追加、移除或替换。项目目标和用户基础用于排序，不自动限制项目分类或难度。";
        if(!state.containsKey("difficulty"))answer+="当前没有项目难度限制。";
        if(state.get("lastChangeSummary") instanceof String last)answer+="\n最近一次条件处理："+last;
        if(state.get("pendingConditions") instanceof List<?> pending&&!pending.isEmpty()) {
            answer+="\n还有待确认内容，尚未视为有效条件：";
            for(Object raw:pending)answer+="\n"+((Map<?,?>)raw).get("question");
        }
        return answer;
    }
}
