package com.orangejd.server.customer;

import java.util.*;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;

/** Fits complete evidence spans into the model input budget. */
final class CustomerEvidenceBudget {
    static List<Map<String,Object>> prepare(List<RagBSearchHit> hits,String question,
                                            Map<String,String> aliases,CustomerJson json,int inputTokens) {
        for(int budget=4200;budget>=1000;budget-=400) {
            var input=CustomerEvidence.input(hits,budget,aliases);
            if(!input.isEmpty() && CustomerModel.estimate(CustomerEvidence.PROMPT+json.write(Map.of("question",question,"evidence",CustomerEvidence.promptEvidence(input))))<=inputTokens) return input;
        }
        throw new IllegalArgumentException(hits.isEmpty()?"NO_USABLE_EVIDENCE":"EVIDENCE_BUDGET_EXCEEDED");
    }
}
