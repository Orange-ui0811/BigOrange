package com.orangejd.server.customer;

import java.util.*;

/** Completion of an HTTP/run protocol is not evidence of recommendation quality. */
public final class CustomerOutcome {
    private CustomerOutcome() {}
    public static String classify(Map<String,Object> result) {
        return switch(Objects.toString(result.get("status"),"")) {
            case "VERIFIED_PROJECTS" -> "EVIDENCED_RECOMMENDATION";
            case "PROJECTS_WITH_LIMITATIONS" -> "PARTIAL_RECOMMENDATION";
            case "SUPPORTED_EXTRACTS" -> "EVIDENCED_ANSWER";
            case "PARTIALLY_SUPPORTED_EXTRACTS" -> "PARTIAL_ANSWER";
            case "REFUSE_NO_PLATFORM_FAQ_EVIDENCE","REFUSE_INSUFFICIENT_EVIDENCE","REFUSE_NO_MATCH" -> "INSUFFICIENT_EVIDENCE";
            case "NO_MATCH" -> "NO_MATCH";
            case "NO_VERIFIED_MATCH" -> "NO_VERIFIED_MATCH";
            case "PURPOSE_REVIEW_PARTIAL" -> "COVERAGE_INCOMPLETE";
            case "TASK_UPDATED" -> "TASK_UPDATED";
            case "NEEDS_CLARIFICATION","FOLLOW_UP" -> "AWAITING_USER";
            case "CONVERSATION","CONFIRMED_CONDITIONS","PROCESS_EXPLANATION" -> "DIALOGUE_COMPLETED";
            case "RELEVANCE_UNAVAILABLE","DIAGNOSTICS_UNAVAILABLE","PROJECTS_CHANGED","FAILED","TIMED_OUT","INTERRUPTED","CANCELLED" -> "INCOMPLETE";
            default -> "UNASSESSED";
        };
    }
}
