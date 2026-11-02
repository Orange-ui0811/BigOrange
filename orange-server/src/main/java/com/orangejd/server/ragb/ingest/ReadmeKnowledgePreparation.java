package com.orangejd.server.ragb.ingest;

import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.Source;

/** Current experimental preparation entry; no model, storage or publication side effects. */
public final class ReadmeKnowledgePreparation {
    public record Prepared(String admissionPolicy,ReadmeConservativePolicy.Result admission,
                           ReadmeKnowledgeChunker.Preview knowledge,ReadmeApprovedStructuralChunker.Preview structural){}
    public Prepared prepare(Source source,ReadmeModelReview.Result classification,String generationId){
        var admission=new ReadmeConservativePolicy().prepare(source,classification);
        var output=admission.output();
        var knowledge=new ReadmeKnowledgeChunker().preview(source,output.preview(),output.projection(),new ReadmeKnowledgeChunker.Identity(null,generationId));
        var eligible=knowledge.units().stream().filter(u->u.reasons().stream().noneMatch(r->r.startsWith("REQUIRED_BLOCK_NOT_ADMITTED"))).flatMap(u->u.sourceSpans().stream()).toList();
        var structural=new ReadmeApprovedStructuralChunker().split(source,output.preview().blocks(),eligible);
        return new Prepared(ReadmeConservativePolicy.POLICY,admission,knowledge,structural);
    }
}
