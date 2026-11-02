package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;

/** Run the unchanged behavioral suite through the actual graph, including evidence and multi-turn cases. */
class CustomerGraphConversationRegressionTest extends CustomerConversationTest {
    @BeforeEach @Override void setup(){
        super.setup();
        var repository=mock(CustomerGraphRepository.class);
        var trace=new CustomerExecutionTrace(repository,json,new SimpleMeterRegistry());
        conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties()) {
            @Override public void execute(String id,List<Map<String,Object>> usage,Instant deadline) {
                var out=new LangGraphCustomerEngine(this,repository,json,trace).execute(new CustomerRunContext(id,deadline,usage));
                store.complete(id,out.result(),out.state(),metrics(usage));
            }
        };
    }
}
