package com.orangejd.server.ragb.retrieval;

public record RagBSearchHit(
        long documentId,
        long versionId,
        long chunkId,
        int chunkIndex,
        String title,
        String sourceType,
        String sourceRef,
        String sectionPath,
        int startOffset,
        int endOffset,
        String content,
        double score,
        String retrievalRoute,
        Double rerankScore) {
    public RagBSearchHit(long documentId,long versionId,long chunkId,int chunkIndex,String title,String sourceType,
                         String sourceRef,String sectionPath,int startOffset,int endOffset,String content,double score,String retrievalRoute){
        this(documentId,versionId,chunkId,chunkIndex,title,sourceType,sourceRef,sectionPath,startOffset,endOffset,content,score,retrievalRoute,null);
    }
    public RagBSearchHit withRerankScore(double value){
        return new RagBSearchHit(documentId,versionId,chunkId,chunkIndex,title,sourceType,sourceRef,sectionPath,startOffset,endOffset,content,score,retrievalRoute,value);
    }
}
