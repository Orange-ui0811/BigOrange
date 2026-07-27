package com.orangejd.server.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardRefreshTaskTest {

    @Test
    void appendsChatCompletionsPathToBaseEndpoint() {
        assertEquals(
                "https://ai.gitee.com/v1/chat/completions",
                LeaderboardRefreshTask.resolveChatCompletionsUrl("https://ai.gitee.com/v1/"));
    }

    @Test
    void keepsCompleteChatCompletionsEndpointUnchanged() {
        assertEquals(
                "https://ai.gitee.com/v1/chat/completions",
                LeaderboardRefreshTask.resolveChatCompletionsUrl(
                        "https://ai.gitee.com/v1/chat/completions"));
    }

    @Test
    void rejectsMissingEndpoint() {
        assertThrows(
                IllegalStateException.class,
                () -> LeaderboardRefreshTask.resolveChatCompletionsUrl(" "));
    }
}
