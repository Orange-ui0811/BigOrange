package com.orangejd.server.ragb.retrieval;

import java.util.List;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

/** Shared Redis index metadata schema. */
final class RagBIndexFields {
    static final List<RedisVectorStore.MetadataField> FIELDS = List.of(
            RedisVectorStore.MetadataField.numeric("documentId"),
            RedisVectorStore.MetadataField.numeric("versionId"),
            RedisVectorStore.MetadataField.numeric("chunkId"),
            RedisVectorStore.MetadataField.numeric("chunkIndex"),
            RedisVectorStore.MetadataField.text("title"),
            RedisVectorStore.MetadataField.tag("sourceType"),
            RedisVectorStore.MetadataField.text("sourceRef"),
            RedisVectorStore.MetadataField.text("sectionPath"),
            RedisVectorStore.MetadataField.numeric("startOffset"),
            RedisVectorStore.MetadataField.numeric("endOffset"),
            RedisVectorStore.MetadataField.text("language"),
            RedisVectorStore.MetadataField.text("techStack"),
            RedisVectorStore.MetadataField.text("difficulty"),
            RedisVectorStore.MetadataField.numeric("durationMaxDays"),
            RedisVectorStore.MetadataField.tag("requiresDocker"),
            RedisVectorStore.MetadataField.tag("requiresDatabase"),
            RedisVectorStore.MetadataField.tag("requiresSecrets"),
            RedisVectorStore.MetadataField.tag("requirementsKnown"),
            RedisVectorStore.MetadataField.text("repositoryUrl"),
            RedisVectorStore.MetadataField.text("businessId"));

}
