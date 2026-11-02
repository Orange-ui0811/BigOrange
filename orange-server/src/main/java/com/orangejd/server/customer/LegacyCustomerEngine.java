package com.orangejd.server.customer;

import org.springframework.stereotype.Service;

@Service
public final class LegacyCustomerEngine implements CustomerExecutionEngine {
    private final CustomerConversation conversation;private final CustomerExecutionTrace trace;
    public LegacyCustomerEngine(CustomerConversation conversation,CustomerExecutionTrace trace){this.conversation=conversation;this.trace=trace;}
    public Kind kind(){return Kind.LEGACY;}
    public CustomerExecutionResult execute(CustomerRunContext context){
        var turn=conversation.newTurn(context);
        for(int count=0;!"END".equals(turn.next());count++) {
            if(count>=32)throw new IllegalStateException("GRAPH_NODE_LIMIT");
            trace.node(turn,kind().name(),turn.next());
        }
        return turn.output();
    }
}
