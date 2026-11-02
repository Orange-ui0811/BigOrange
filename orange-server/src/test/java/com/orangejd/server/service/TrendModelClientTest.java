package com.orangejd.server.service;

import com.orangejd.server.foundation.external.AiRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TrendModelClientTest {
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private final TrendModelClient client = new TrendModelClient(http);
    private final AiRuntimeConfig runtime = new AiRuntimeConfig("test", "v1", "https://ai.example/v1", "secret", "requested", "TEST");

    @Test void preservesProviderUsageAndActualModel() {
        respond("\"model\":\"actual\",\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120},", "stop");
        var result = client.completeMeasured(runtime, Map.of());
        assertEquals("{}", result.content());
        assertEquals("actual", result.metrics().get("model"));
        assertEquals("ai.example", result.metrics().get("providerHost"));
        assertEquals("PROVIDER", result.metrics().get("usageSource"));
        assertEquals(120L, ((Map<?, ?>) result.metrics().get("usage")).get("total_tokens"));
        assertFalse(result.metrics().toString().contains("secret"));
        server.verify();
    }
    @Test void absentUsageIsUnavailableRatherThanZero() {
        respond("", "stop");
        var result = client.completeMeasured(runtime, Map.of());
        assertEquals("UNAVAILABLE", result.metrics().get("usageSource"));
        assertEquals(Map.of(), result.metrics().get("usage"));
        server.verify();
    }
    @Test void retainsCacheAndReasoningTokensForCostAccounting() {
        respond("\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":40,\"prompt_cache_hit_tokens\":80,"
                + "\"prompt_cache_miss_tokens\":20,\"completion_tokens_details\":{\"reasoning_tokens\":15}},", "stop");
        var usage=(Map<?,?>)client.completeMeasured(runtime,Map.of()).metrics().get("usage");
        assertEquals(80L,usage.get("prompt_cache_hit_tokens"));
        assertEquals(15L,((Map<?,?>)usage.get("completion_tokens_details")).get("reasoning_tokens"));
    }
    @Test void truncatedOutputIsRejected() {
        respond("", "length");
        assertThrows(IllegalStateException.class, () -> client.completeMeasured(runtime, Map.of()));
        server.verify();
    }
    private void respond(String metadata, String finish) {
        server.expect(requestTo("https://ai.example/v1/chat/completions"))
                .andRespond(withSuccess("{" + metadata + "\"choices\":[{\"finish_reason\":\"" + finish
                        + "\",\"message\":{\"content\":\"{}\"}}]}", MediaType.APPLICATION_JSON));
    }
}
