package com.orangejd.server.ragb.ingest;

import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.*;
import java.util.*;

/** Framework adapter; provider timeouts and the acceptance ledger stay at the existing HTTP boundary. */
public final class ReadmeSpringAiReviewTransport implements ReadmeModelReview.Transport {
    private final ChatModel model;
    public ReadmeSpringAiReviewTransport(ChatModel model) {this.model=Objects.requireNonNull(model);}
    @Override public ReadmeModelReview.Reply call(String system,String user,int outputTokens) {
        var prompt=new Prompt(List.of(new SystemMessage(system),new UserMessage(user)),
                ChatOptions.builder().temperature(0.0).maxTokens(outputTokens).build());
        var response=model.call(prompt);
        if(response==null||response.getResult()==null||response.getResult().getOutput().hasToolCalls())
            throw new IllegalArgumentException("Missing response or unexpected tools");
        Map<String,Integer> usage=new LinkedHashMap<>();var tokens=response.getMetadata().getUsage();
        if(tokens!=null) {
            if(tokens.getPromptTokens()!=null)usage.put("prompt_tokens",tokens.getPromptTokens());
            if(tokens.getCompletionTokens()!=null)usage.put("completion_tokens",tokens.getCompletionTokens());
            if(tokens.getTotalTokens()!=null)usage.put("total_tokens",tokens.getTotalTokens());
        }
        return new ReadmeModelReview.Reply(response.getResult().getOutput().getText(),response.getResult().getMetadata().getFinishReason(),usage);
    }
}
