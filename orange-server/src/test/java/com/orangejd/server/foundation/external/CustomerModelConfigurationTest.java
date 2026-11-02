package com.orangejd.server.foundation.external;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerModelConfigurationTest {
    @Test void flashOverridePreservesTextConfigurationForOtherCallers() {
        var runtime=mock(AiRuntimeConfigService.class);
        var text=new AiRuntimeConfig("text","env","https://api.deepseek.com","test-only","deepseek-v4-pro","ENV");
        when(runtime.resolveText(null)).thenReturn(text);
        var client=new ExternalAiClient(runtime,"deepseek-flash");
        assertThat(client.customerConfig().model()).isEqualTo("deepseek-flash");
        assertThat(client.customerConfig().endpoint()).isEqualTo(text.endpoint());
        assertThat(client.customerConfig().apiKey()).isEqualTo(text.apiKey());
        assertThat(runtime.resolveText(null).model()).isEqualTo("deepseek-v4-pro");
        assertThat(ExternalAiClient.usesDeepSeekThinkingSwitch(client.customerConfig())).isTrue();
    }

    @Test void thinkingSwitchSupportsFlashAliasAndAvoidsUnrelatedProviders() {
        assertThat(ExternalAiClient.usesDeepSeekThinkingSwitch(new AiRuntimeConfig("text","env","https://api.deepseek.com","test-only","deepseek-v4-flash","ENV"))).isTrue();
        assertThat(ExternalAiClient.usesDeepSeekThinkingSwitch(new AiRuntimeConfig("text","env","https://ai.gitee.com/v1","test-only","deepseek-flash","ENV"))).isFalse();
    }
}
