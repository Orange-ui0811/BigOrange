package com.orangejd.server.contribution.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobAiAuditClientTest {
    private final JobAiAuditClient client = new JobAiAuditClient(
            new ObjectMapper(), new RestTemplateBuilder(), "https://example.com/v1", "test-key", "test-model");

    @Test
    void parsesStrictDecisionJson() {
        JobAiAuditClient.Decision decision = client.parseDecision(
                "```json\n{\"action\":\"APPROVE\",\"confidence\":0.96,\"reason\":\"内容完整\"}\n```");
        assertEquals("APPROVE", decision.action());
        assertEquals(0.96, decision.confidence());
    }

    @Test
    void malformedOrUnknownDecisionFallsBackToManualReview() {
        assertEquals("MANUAL_REVIEW", client.parseDecision("not-json").action());
        assertEquals("MANUAL_REVIEW", client.parseDecision(
                "{\"action\":\"APPROVE\",\"confidence\":2,\"reason\":\"bad\"}").action());
    }
}
