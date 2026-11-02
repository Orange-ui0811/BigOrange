package com.orangejd.server.customer;

import java.util.*;

/** A true citation does not prove the question was answered. Coverage remains a separate verdict. */
public final class CustomerEvidenceCoverage {
    private CustomerEvidenceCoverage() {}
    public record Assessment(String status,List<Map<String,Object>> missingFacts,String reason) {
        public Map<String,Object> view(){return Map.of("status",status,"missingFacts",missingFacts,"reason",reason);}
    }
    public static Assessment insufficient(String question,String reason) {
        return new Assessment("INSUFFICIENT",List.of(fact(question,reason)),reason);
    }
    private static Map<String,Object> fact(String question,String reason) {
        return Map.of("text",question,"questionQuote",question,"reason",reason,"owner","PROJECT_FACT");
    }
    public static Assessment assess(String question,Map<String,Object> verdict,int supportedClaims) {
        if(supportedClaims==0)return insufficient(question,"NO_VERIFIED_ANSWER");
        if(verdict==null||!Set.of("supportedIds","coverage","missingFacts").containsAll(verdict.keySet())
                ||!Set.of("COMPLETE","PARTIAL","INSUFFICIENT").contains(Objects.toString(verdict.get("coverage"),""))
                ||!(verdict.get("missingFacts") instanceof List<?> raw)||raw.size()>6)
            return unassessed(question);
        List<Map<String,Object>> missing=new ArrayList<>();
        for(Object item:raw) {
            if(!(item instanceof Map<?,?> gap)||!gap.keySet().equals(Set.of("questionQuote","reason"))
                    ||!(gap.get("questionQuote") instanceof String quote)||quote.isBlank()||quote.length()>200||!question.contains(quote)
                    ||!(gap.get("reason") instanceof String reason)||reason.isBlank()||reason.length()>160)
                return unassessed(question);
            missing.add(fact(quote,reason));
        }
        String status=verdict.get("coverage").toString();
        if(status.equals("COMPLETE")&&!missing.isEmpty())status="PARTIAL";
        if(!status.equals("COMPLETE")&&missing.isEmpty())missing.add(fact(question,"QUESTION_NOT_FULLY_ANSWERED"));
        return new Assessment(status,List.copyOf(missing),"VERIFIER_REPORTED_COVERAGE");
    }
    private static Assessment unassessed(String question) {
        return new Assessment("UNASSESSED",List.of(fact(question,"QUESTION_COVERAGE_NOT_CONFIRMED")),"COVERAGE_UNASSESSED");
    }
    static Map<String,Object> payload(String question,List<Map<String,Object>> claims,boolean projectMode) {
        Map<String,Object> input=new LinkedHashMap<>();input.put("claims",CustomerEvidence.verificationInput(claims));
        if(!projectMode)input.put("question",question);
        return input;
    }
}
