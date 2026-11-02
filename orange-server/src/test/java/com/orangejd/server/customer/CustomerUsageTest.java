package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerUsageTest {
    @Test void aggregatesPaidFailedAttemptsAndMarksMissingUsage() {
        var summary=CustomerUsage.summarize(List.of(
            Map.of("model","deepseek-flash","status","FAILED","usage",Map.of("prompt_tokens",10,"completion_tokens",5,"total_tokens",15)),
            Map.of("model","deepseek-flash","status","SUCCEEDED","usage",Map.of("prompt_tokens",20,"completion_tokens",8,"total_tokens",28)),
            Map.of("status","FAILED")));
        assertThat(summary).containsEntry("inputTokens",30L).containsEntry("outputTokens",13L).containsEntry("totalTokens",43L)
            .containsEntry("callsWithoutUsage",1).containsEntry("complete",false);
    }
    @Test void localReplyHasNoTokenConsumption() {
        assertThat(CustomerUsage.summarize(List.of())).containsEntry("totalTokens",0L).containsEntry("calls",0).containsEntry("complete",true);
    }
}
