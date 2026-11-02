package com.orangejd.server.customer;

import java.util.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Verification scope is independent of user strength: a soft business goal still needs evidence. */
public final class CustomerRecommendationPolicy {
    private CustomerRecommendationPolicy() {}
    public static final String VERSION="recommendation-fit-v1";
    static boolean purpose(Map<String,Object> requirement) {
        String field=Objects.toString(requirement.get("field"),"");
        if("topic".equals(field))return true;
        if(!"goal".equals(field))return false;
        Object raw=requirement.get("normalizedValue");
        List<?> goals=raw instanceof List<?> list?list:List.of(Objects.toString(raw,Objects.toString(requirement.get("text"),"")));
        return goals.stream().map(Object::toString).anyMatch(g->!generalGoal(g));
    }
    private static boolean generalGoal(String value) {
        return value.strip().matches("(?iu)(学习|练习|练手|自学|求职|面试|就业|简历|赚钱/接单/创业|赚钱|接单|创业|探索技术兴趣|探索|技术兴趣|提升技能|熟悉)(项目|用途|目标)?");
    }
    static boolean required(Map<String,Object> requirement) {return purpose(requirement)||"HARD".equals(requirement.get("strength"));}
    static boolean semantic(Map<String,Object> requirement) {return purpose(requirement)||"open".equals(requirement.get("field"));}
    static String dimension(Map<String,Object> requirement) {return purpose(requirement)?"PURPOSE":"HARD".equals(requirement.get("strength"))?"CONSTRAINT":"PREFERENCE";}
    /** Do not trust the saved card's importable flag or its old requiredForVerified classification. */
    static boolean requiredSatisfied(Map<String,Object> card,Map<String,Object> state) {
        var needed=active(state).stream().filter(r->!"verificationMode".equals(r.get("field"))).filter(CustomerRecommendationPolicy::required).toList();
        if(needed.isEmpty())return false;
        var matches=rows(card.get("requirementMatches"));
        return needed.stream().allMatch(r->matches.stream().anyMatch(m->Objects.equals(r.get("id"),m.get("requirementId"))
            &&number(r,"revision")==number(m,"requirementRevision")&&"SATISFIED".equals(m.get("verdict"))&&!rows(m.get("evidenceRefs")).isEmpty()
            &&(!purpose(r)||"EXISTING".equals(m.get("capabilityScope")))));
    }
    static Map<String,Object> purposeFit(List<Map<String,Object>> matches) {
        var goals=matches.stream().filter(m->"PURPOSE".equals(m.get("dimension"))).toList();
        long satisfied=goals.stream().filter(m->"SATISFIED".equals(m.get("verdict"))).count();
        String status=goals.isEmpty()?"NOT_REQUESTED":satisfied==goals.size()?"SATISFIED":goals.stream().anyMatch(m->"CONFLICT".equals(m.get("verdict")))?"CONFLICT":goals.stream().anyMatch(m->"PARTIAL".equals(m.get("verdict")))?"PARTIAL":"UNKNOWN";
        String message=switch(status){case "SATISFIED"->"本轮具体用途有项目自身原文支持。";case "CONFLICT"->"项目资料与本轮具体用途冲突。";case "PARTIAL"->"仅有部分用途依据，需要自行改造；不能视为已经具备所需功能。";case "UNKNOWN"->"尚无原文证明项目已经具备本轮所需功能；用途相关或可以搭建不等于功能满足。";default->"本轮未指定可核验的具体业务用途。";};
        return Map.of("status",status,"requiredCount",goals.size(),"satisfiedCount",satisfied,"message",message);
    }
}
