package com.orangejd.server.customer;

import java.util.*;

/** Grounded optional context; never substitutes for server-enforced constraints. */
public final class CustomerPreferences {
    private CustomerPreferences() {}
    public static void reconcile(Map<String,Object> state, Object proposed, String message,
                                 Map<String,Map<String,Object>> pending) {
        List<String> values=new ArrayList<>();
        pending.entrySet().removeIf(e->"other".equals(e.getKey()) && protocolRule(Objects.toString(e.getValue().get("quote"),"")));
        if(state.get("preferences") instanceof List<?> prior) prior.stream().filter(String.class::isInstance).map(String.class::cast).forEach(values::add);
        if(proposed instanceof List<?> list) for(Object item:list.stream().limit(6).toList()) {
            if(!(item instanceof String quote) || quote.isBlank() || quote.length()>200 || !message.contains(quote)) continue;
            if(protocolRule(quote)) continue;
            if(coveredByConfirmedLanguage(state,quote)) {
                pending.entrySet().removeIf(e->"other".equals(e.getKey()) && quote.equals(e.getValue().get("quote")));
                continue;
            }
            if(quote.matches("(?is).*(必须|只能|仅限|禁止|不允许|不能|不要|不用|不使用|不超过|至少|最多|仅推荐|只推荐|must|only|without).*")) {
                pending.put("other",Map.of("field","other","quote",quote,"question","这项明确要求暂不能自动核实："+quote+"。可以说明具体限制吗？"));
                continue;
            }
            if(!values.contains(quote)) values.add(quote);
            // The same optional context must not simultaneously block a recommendation.
            pending.entrySet().removeIf(e->"other".equals(e.getKey()) && quote.equals(e.getValue().get("quote")));
        }
        if(!values.isEmpty()) state.put("preferences",new ArrayList<>(values.subList(Math.max(0,values.size()-6),values.size())));
    }
    private static boolean protocolRule(String quote) {
        // Only full protocol-only clauses are covered. Mixed or unknown project restrictions stay pending.
        return quote.strip().matches("(?:没有符合的就提出调整建议[，,]?\\s*)?(?:必须|要|请)?(?:等我同意|经过我同意|征得我同意)(?:后|才|才能)?(?:修改|调整|放宽)(?:条件|要求)[。！!]*")
            || quote.strip().matches("(?:不要|不能)(?:擅自|自动)(?:修改|调整|放宽)(?:我的)?(?:条件|要求)[。！!]*");
    }
    private static boolean coveredByConfirmedLanguage(Map<String,Object> state,String quote) {
        if(!(state.get("language") instanceof String language)) return false;
        return quote.strip().matches("(?i)(?:只|仅|只能)(?:推荐|接受|使用|用|要)?\\s*"+java.util.regex.Pattern.quote(language)+"\\s*(?:语言|项目|的项目|开发的项目)?[。.!！]*");
    }
}
