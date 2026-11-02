package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadmeSpringAiReviewTransportTest {
    @Test void forwardsExactRawMessagesAndBoundedOptionsThroughSpringAi() {
        ChatModel model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}"),
                ChatGenerationMetadata.builder().finishReason("stop").build()))));
        var result=new ReadmeSpringAiReviewTransport(model).call("rules","raw {braces} ```data```",512);
        var capture=ArgumentCaptor.forClass(Prompt.class);verify(model).call(capture.capture());
        assertEquals(512,capture.getValue().getOptions().getMaxTokens());
        assertEquals("raw {braces} ```data```",capture.getValue().getInstructions().get(1).getText());
        assertEquals("stop",result.finishReason());assertEquals("{}",result.text());
    }
    @Test void unexpectedToolCallsAreRejectedWithoutExecution() {
        ChatModel model=mock(ChatModel.class);
        var message=new AssistantMessage("",Map.of(),List.of(new AssistantMessage.ToolCall("id","function","execute","{}")));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(message))));
        assertThrows(IllegalArgumentException.class,()->new ReadmeSpringAiReviewTransport(model).call("rules","raw",512));
        verify(model,times(1)).call(any(Prompt.class));
    }
}
