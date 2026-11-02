package com.orangejd.server.foundation.external;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalAiClientUsageTest {
    @Test
    void retainsProviderUsageAlongsideTheExistingAnswerText() {
        AiRuntimeConfigService configs = mock(AiRuntimeConfigService.class);
        when(configs.resolveText(null)).thenReturn(new AiRuntimeConfig("TEXT", "test", "https://example.invalid/v1",
                "test-only", "test-model", "TEST"));
        ExternalAiClient client = new ExternalAiClient(configs);
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo("https://example.invalid/v1/chat/completions")).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5,\"total_tokens\":17}}",
                MediaType.APPLICATION_JSON));

        var result = client.callTextMeasured(null, "system", "query", 0.1, "test");

        assertThat(result.text()).isEqualTo("answer");
        assertThat(result.usage()).containsEntry("total_tokens", 17);
        server.verify();
    }
}
