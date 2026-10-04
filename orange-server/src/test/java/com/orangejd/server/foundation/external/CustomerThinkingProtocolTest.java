package com.orangejd.server.foundation.external;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerThinkingProtocolTest {
    private AiRuntimeConfig config(String endpoint) {
        return new AiRuntimeConfig("TEXT","test",endpoint,"synthetic-key","deepseek-flash","TEST");
    }
    @Test void thinkingKeepsTheActionSchemaButOmitsUnsupportedNamedToolChoice() {
        var fields=ExternalAiClient.customerActionFields(config("https://api.deepseek.com"),true);
        assertThat(fields).containsKey("tools").doesNotContainKey("tool_choice");
        var tool=(Map<?,?>)((List<?>)fields.get("tools")).getFirst();
        var function=(Map<?,?>)tool.get("function");
        assertThat(function.get("name")).isEqualTo("customerAction");
        var schema=(Map<?,?>)function.get("parameters");
        assertThat(((List<?>)schema.get("required")).contains("reply")).isTrue();
    }
    @Test void normalModeAndOtherProvidersKeepTheExistingNamedToolProtocol() {
        assertThat(ExternalAiClient.customerActionFields(config("https://api.deepseek.com"),false)).containsKey("tool_choice");
        assertThat(ExternalAiClient.customerActionFields(config("https://synthetic.invalid"),true)).containsKey("tool_choice");
    }
}
