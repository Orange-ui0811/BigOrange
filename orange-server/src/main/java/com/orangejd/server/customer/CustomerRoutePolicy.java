package com.orangejd.server.customer;

import java.util.Map;
import java.util.Objects;

/** Public workflow vocabulary with an adapter for existing customer API intent names. */
public final class CustomerRoutePolicy {
    private CustomerRoutePolicy() {}
    public static String intent(Map<String,Object> action) {
        if("SELECT_PROJECT".equals(action.get("validatedTaskAction")))return "PROJECT_ACTION";
        return switch(Objects.toString(action.get("intent"),"")) {
            case "CHAT"->"CHAT";case "QUESTION"->"KNOWLEDGE_QA";case "COLLECT","RECOMMEND"->"FIND_PROJECT";
            case "DETAIL"->"PROJECT_DETAIL";case "COMPARE"->"COMPARE_PROJECTS";case "STATE","DIAGNOSE","EXPLAIN"->"TASK_STATUS";default->"UNKNOWN";
        };
    }
    public static String route(Map<String,Object> action){return switch(intent(action)){case "KNOWLEDGE_QA"->"KNOWLEDGE";case "FIND_PROJECT"->"RECOMMEND";case "PROJECT_DETAIL","COMPARE_PROJECTS"->"PROJECT_REVIEW";case "TASK_STATUS"->"INSPECT";case "PROJECT_ACTION"->"ACTION";default->"DIRECT";};}
    public static String subtype(Map<String,Object> action){return switch(Objects.toString(action.get("intent"),"")){case "STATE"->"CONDITIONS";case "DIAGNOSE"->"NO_MATCH";case "EXPLAIN"->"EXECUTION";default->"";};}
}
