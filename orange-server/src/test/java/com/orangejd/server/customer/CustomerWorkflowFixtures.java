package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Explicit current-state fixtures for legacy business-service regressions; migration has separate tests. */
final class CustomerWorkflowFixtures {
    static Map<String,Object> session(Object payload) {
        var json=new CustomerJson(new ObjectMapper());var state=json.read(payload);
        if(!CustomerWorkflow.enabled(state)) {
            var root=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(root,List.of(),"fixture");state.put(CustomerWorkflow.KEY,root.get(CustomerWorkflow.KEY));
            if(state.get("lastCandidates") instanceof List<?> projects) {
                CustomerWorkflow.task(state).put("currentListId","fixture-list");CustomerWorkflow.task(state).put("lists",List.of(Map.of("id","fixture-list","projects",projects,"runId","fixture")));
            }
        }
        return Map.of("state_json",json.write(state));
    }
}
