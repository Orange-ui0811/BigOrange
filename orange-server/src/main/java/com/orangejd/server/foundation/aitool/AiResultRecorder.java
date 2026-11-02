package com.orangejd.server.foundation.aitool;

public interface AiResultRecorder {
    default Object findExisting(AiToolContext context) {
        return null;
    }

    void saveRequired(AiToolContext context, Object result);
}
