package com.orangejd.server.customer;

import java.util.*;

/** Conservative code-point accounting. Provider-reported token usage remains the cost authority. */
public final class CustomerContextBudget {
    private CustomerContextBudget() {}
    public record Check(String stage,int budget,int estimated,int schemaReserve) {
        public boolean blocked(){return estimated>budget;}
        public String reason(){return blocked()?"CONTEXT_BUDGET_EXCEEDED":"";}
        public void requireFits(){if(blocked())throw new IllegalArgumentException(reason());}
        public Map<String,Object> manifest(){return Map.of("stage",stage,"budget",budget,"estimatedCodePoints",estimated,"schemaReserve",schemaReserve,"blocked",blocked(),"estimator","CODE_POINTS_CONSERVATIVE_V1");}
    }
    /** schemaReserve accounts for definitions not yet serialized here. The final wire body is checked separately. */
    public static Check check(String stage,String system,Object input,int budget,int schemaReserve,CustomerJson json) {
        if(stage==null||stage.isBlank()||budget<1||schemaReserve<0)throw new IllegalArgumentException("INVALID_CONTEXT_BUDGET");
        long size=(long)CustomerModel.estimate(Objects.requireNonNullElse(system,"")+json.write(input))+schemaReserve;
        return new Check(stage,budget,(int)Math.min(Integer.MAX_VALUE,size),schemaReserve);
    }
    public static int stageBudget(String stage,int globalBudget) {
        if(globalBudget<1)throw new IllegalArgumentException("INVALID_CONTEXT_BUDGET");
        return Set.of("REQUIREMENT_MATCH","REQUIREMENT_VERIFY").contains(stage)?Math.min(globalBudget,4000):globalBudget;
    }
}
