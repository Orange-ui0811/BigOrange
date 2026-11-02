package com.orangejd.server.customer;

import org.junit.jupiter.api.BeforeEach;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

/** All new behavioral cases run through the actual LangGraph4j engine too. */
class CustomerGraphEvidenceGapConversationTest extends CustomerEvidenceGapConversationTest {
    @BeforeEach @Override void setup(){
        super.setup();var repository=mock(CustomerGraphRepository.class);
        var trace=new CustomerExecutionTrace(repository,json,new SimpleMeterRegistry());
        conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties()){
            @Override public void execute(String id,List<Map<String,Object>> usage,Instant deadline){
                var result=new LangGraphCustomerEngine(this,repository,json,trace).execute(new CustomerRunContext(id,deadline,usage));
                store.complete(id,result.result(),result.state(),metrics(usage));
            }
        };
    }
}
