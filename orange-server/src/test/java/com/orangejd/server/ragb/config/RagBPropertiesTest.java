package com.orangejd.server.ragb.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagBPropertiesTest {
    @Test void validatesRerankBounds(){
        var p=new RagBProperties();p.getRerank().setMaxCandidates(1);assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p.getRerank().setMaxCandidates(80);p.getRerank().setTimeoutMillis(99);assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test
    void acceptsDedicatedNamespace() {
        assertThatCode(new RagBProperties()::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsLegacyOrWildcardNamespace() {
        RagBProperties legacy = new RagBProperties();
        legacy.setRedisPrefix("spring-ai:vector:");
        assertThatThrownBy(legacy::validate).isInstanceOf(IllegalStateException.class);

        RagBProperties wildcard = new RagBProperties();
        wildcard.setRedisPrefix("orange:rag:b:*");
        assertThatThrownBy(wildcard::validate).isInstanceOf(IllegalStateException.class);
    }
}
