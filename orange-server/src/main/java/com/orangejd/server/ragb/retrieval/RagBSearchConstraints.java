package com.orangejd.server.ragb.retrieval;

import java.util.Set;

public record RagBSearchConstraints(
        Set<String> sourceTypes,
        Set<String> requiredLanguages,
        Integer maxDurationDays,
        String difficulty,
        Boolean allowDocker,
        Boolean allowDatabase,
        Boolean allowSecrets,
        boolean beginnerPreferred,
        boolean careerPreferred,
        Set<Long> documentIds,
        boolean diversifyProjects) {

    public RagBSearchConstraints(Set<String> sources, Set<String> languages, Integer maxDays, String difficulty,
                                Boolean docker, Boolean database, Boolean secrets, boolean beginner, boolean career,Set<Long> documents) {
        this(sources,languages,maxDays,difficulty,docker,database,secrets,beginner,career,documents,false);
    }

    public RagBSearchConstraints(Set<String> sources, Set<String> languages, Integer maxDays, String difficulty,
                                Boolean docker, Boolean database, Boolean secrets, boolean beginner, boolean career) {
        this(sources,languages,maxDays,difficulty,docker,database,secrets,beginner,career,Set.of());
    }

    public RagBSearchConstraints {
        sourceTypes = sourceTypes == null ? Set.of() : Set.copyOf(sourceTypes);
        requiredLanguages = requiredLanguages == null ? Set.of() : Set.copyOf(requiredLanguages);
        documentIds = documentIds == null ? Set.of() : Set.copyOf(documentIds);
        if(documentIds.size()>128 || documentIds.stream().anyMatch(id->id==null || id<=0)) throw new IllegalArgumentException("无效的文档范围");
        if (maxDurationDays != null && maxDurationDays < 1) throw new IllegalArgumentException("最大周期必须大于 0 天");
    }

    public static RagBSearchConstraints sources(Set<String> sourceTypes) {
        return new RagBSearchConstraints(sourceTypes, Set.of(), null, null, null, null, null, false, false);
    }
    public RagBSearchConstraints documents(Set<Long> ids) {
        return new RagBSearchConstraints(sourceTypes,requiredLanguages,maxDurationDays,difficulty,allowDocker,allowDatabase,allowSecrets,beginnerPreferred,careerPreferred,ids,diversifyProjects);
    }
    public RagBSearchConstraints projectDiversity() {
        return new RagBSearchConstraints(sourceTypes,requiredLanguages,maxDurationDays,difficulty,allowDocker,allowDatabase,allowSecrets,beginnerPreferred,careerPreferred,documentIds,true);
    }
    public RagBSearchConstraints indexScope() { return sources(sourceTypes).documents(documentIds); }
}
