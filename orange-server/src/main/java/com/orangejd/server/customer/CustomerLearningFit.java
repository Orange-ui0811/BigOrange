package com.orangejd.server.customer;

import java.util.*;

/** Derived preference only. Never writes a difficulty/source constraint into user state. */
public final class CustomerLearningFit {
    private CustomerLearningFit() {}
    static boolean novice(Map<String,Object> state) {
        String value=Objects.toString(state.get("experience"),"");
        String literal=value.replaceAll("(?iu)(?:不是|不算|并非|非|不再是|不再算)(?:一个)?(?:新手|初学者?|入门者?|零基础|beginner|novice)","");
        return literal.matches("(?isu).*(新手|初学|入门|刚学|刚开始|零基础|没[有过]*经验|beginner|novice).*" );
    }
    static int order(Map<String,Object> card,Map<String,Object> state) {
        if(!novice(state))return 0;
        return switch(Objects.toString(assess(card,state).get("status"),"")) {case "ENTRY_LEVEL"->0;case "CAUTION"->1;case "SCOPE_UNVERIFIED"->3;case "CHALLENGING"->4;default->2;};
    }
    static Map<String,Object> assess(Map<String,Object> card,Map<String,Object> state) {
        boolean beginner=novice(state);String level=CustomerProjects.difficulty(Objects.toString(card.get("difficulty"),""));
        boolean large="LARGE".equalsIgnoreCase(Objects.toString(card.get("codebaseSize"),""));
        String status=!beginner?"NOT_ASSESSED":level.equals("进阶")?"CHALLENGING":large?"SCOPE_UNVERIFIED":level.equals("入门")?"ENTRY_LEVEL":level.equals("中等")?"CAUTION":"UNKNOWN";
        String message=switch(status){case "ENTRY_LEVEL"->"目录标记为入门，仅作为初步排序线索；仍需运行示例确认前置知识。";case "CHALLENGING"->"资料标记为进阶，对初学者有明显门槛；仅适合作为进阶备选，不能保证适合当前基础。";case "SCOPE_UNVERIFIED"->"目录难度为"+(level.isBlank()?"未知":level)+"，但仓库规模较大；尚未核对适合新手的示例或子模块，不能据此认定适合初学者。";case "CAUTION"->"资料标记为中等，建议先核对前置知识并限定一个小改造。";case "UNKNOWN"->"缺少可核对的难度资料，暂不能判断是否适合初学者。";default->"没有足够明确的初学者基础信息，未作入门适配判断。";};
        var result=new LinkedHashMap<String,Object>();result.put("profile",beginner?"BEGINNER":"UNSPECIFIED");result.put("sourceText",Objects.toString(state.get("experience"),""));result.put("status",status);result.put("catalogDifficulty",level);result.put("codebaseSize",Objects.toString(card.get("codebaseSize"),"UNKNOWN"));result.put("basis","CATALOG_DIFFICULTY_NOT_SUITABILITY_PROOF");result.put("message",message);return result;
    }
    static boolean uncertain(Map<String,Object> fit) {return Set.of("CHALLENGING","CAUTION","UNKNOWN","SCOPE_UNVERIFIED").contains(Objects.toString(fit.get("status"),""));}
}
