package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;

/** Frozen source defect plus counterexamples. Every model choice here is simulated. */
@EnabledIfSystemProperty(named="readme.v5.admission.root",matches=".+")
class K3V5UnqualifiedPromiseTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String HOLD="UNQUALIFIED_PROMISE_REQUIRES_REVIEW";
    private final ReadmeKnowledgeFilter filter=new ReadmeKnowledgeFilter();
    @TempDir Path temp;
    private Source frozen()throws Exception{
        Path root=Path.of(System.getProperty("readme.v5.admission.root"));
        for(var row:JSON.readTree(Files.readString(root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/r4/sources.json"))).path("documents"))
            if(row.path("source").path("documentId").asText().equals("240"))return JSON.treeToValue(row.path("source"),Source.class);
        throw new AssertionError("Missing frozen source");
    }
    private Source source(String text){return new Source("promise","v1","owner/application",text);}
    private Block block(Preview p,String needle){return p.blocks().stream().filter(b->b.text().contains(needle)).findFirst().orElseThrow();}
    private Assessment at(Output out,String id){return out.projection().assessments().stream().filter(a->a.blockId().equals(id)).findFirst().orElseThrow();}
    private ReadmeModelReview engine(){return new ReadmeModelReview("OFFLINE_PROMISE",new Limits(0,12000,2048,8,0,0),
            temp.resolve("cache"),new ReadmeReviewJournal(temp.resolve("unused.jsonl"),"PROMISE",true),(s,u,o)->{throw new AssertionError("No provider allowed");});}
    private Output rules(Source s){return new ReadmeRecommendationProjection().project(s,filter.preview(s),List.of());}

    @Test void frozenCompleteSentenceIsReviewAndNotAClassificationTarget()throws Exception{
        var s=frozen();var p=filter.preview(s);var b=block(p,"无需妥协");
        assertEquals(52,b.startLine());assertEquals(Decision.REVIEW,b.decision());assertEquals(HOLD,b.reason());
        assertFalse(ReadmeModelReview.eligible(b));assertFalse(engine().plan(s,p).stream().flatMap(x->x.targetIds().stream()).anyMatch(b.blockId()::equals));
        assertEquals(p,filter.reassemble(s,p,p.blocks()));assertEquals(s.text(),p.blocks().stream().map(Block::text).reduce("",String::concat));
        assertTrue(b.text().endsWith("无需妥协。"),"Complete source is not labelled as truncation");
    }
    @Test void simulatedKeepCannotPromotePromiseThroughScopeOrPointerKind()throws Exception{
        var s=frozen();var p=filter.preview(s);var b=block(p,"无需妥协");
        for(var choice:List.of(new Selection(b.blockId(),Decision.KEEP,Kind.PURPOSE,Scope.CORE,Reason.KNOWLEDGE,Applicability.IN_SCOPE,List.of(b.blockId())),
                new Selection(b.blockId(),Decision.KEEP,Kind.SOURCE_POINTER,Scope.RELATED_PRODUCT,Reason.POINTER,Applicability.OUT_OF_SCOPE,List.of(b.blockId())))){
            var out=ReadmeModelReview.assembleSelections(s,p,List.of(choice),"SIMULATED","PROMISE_REVIEW");var a=at(out,b.blockId());
            assertEquals(Disposition.REVIEW_QUEUE,a.disposition());assertEquals(Applicability.UNKNOWN,a.applicability());assertTrue(a.scopes().isEmpty());
            assertEquals(HOLD,a.reason());assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(x->x.blockId().equals(b.blockId())));
        }
    }
    @Test void equivalentUnqualifiedPromisesInBothLanguagesStayReview(){
        for(String claim:List.of("Private AI application — without compromise.","An all-in-one application with no compromises.",
                "A reporting application with no trade-offs.","私有 AI 应用——无需任何妥协。","这是一款工具，毫无妥协。")){
            var s=source("# Tool\nThis is an application for reports.\n\n## Overview\n"+claim+"\n");var p=filter.preview(s);var b=block(p,claim);
            assertEquals(HOLD,b.reason(),claim);assertEquals(Disposition.REVIEW_QUEUE,at(rules(s),b.blockId()).disposition());
        }
    }
    @Test void descriptiveFactsAndRealRestrictionsDoNotBecomePromises(){
        for(String fact:List.of("This is an all-in-one application for private document search.","A fully featured application for reports.",
                "Supports local inference without a GPU.","无需 GPU，支持 CPU 部署。","仅限单用户使用，无认证。","MIT license applies with attribution.")){
            var s=source("# Tool\nThis is an application for reports.\n\n## Features\n"+fact+"\n");var out=rules(s);var b=block(out.preview(),fact);
            assertNotEquals(HOLD,b.reason(),fact);assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,b.blockId()).disposition(),fact);
        }
    }
    @Test void explicitRejectionOfAnAbsolutePromiseRemainsARestriction(){
        for(String fact:List.of("We do not promise an application without compromise.","We cannot guarantee no trade-offs.",
                "我们不保证所有需求都能无需妥协地满足。")){
            var s=source("# Tool\nThis is an application for reports.\n\n## Features\n"+fact+"\n");var out=rules(s);var b=block(out.preview(),fact);
            assertNotEquals(HOLD,b.reason(),fact);assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,b.blockId()).disposition(),fact);
        }
    }
    @Test void inlineAndFencedCodeAreNotReadAsProductPromises(){
        var s=source("# Tool\nThis is an application for reports.\n\n## Usage\nThe option prints `without compromise` as literal output.\n\n```sh\necho '无需妥协'\n```\n");var out=rules(s);
        for(String needle:List.of("option prints","echo '")){
            var b=block(out.preview(),needle);assertNotEquals(HOLD,b.reason());assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,b.blockId()).disposition());
        }
    }
    @Test void quarantineDoesNotDeleteIndependentFactsOrBecomeTheirEvidence(){
        var s=source("# Tool\nThis is an application for reports.\n\nPrivate AI application — without compromise.\n\n## Features\nSupports CSV export.\n");var out=rules(s);
        var promise=block(out.preview(),"without compromise");var fact=block(out.preview(),"CSV export");
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,fact.blockId()).disposition());
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(a->a.blockId().equals(promise.blockId())));
        assertEquals(s.text(),out.preview().blocks().stream().map(Block::text).reduce("",String::concat));
    }
    @Test void uncertainPurposeCannotQualifyEveryOtherBlockAsProjectKnowledge(){
        var s=source("# Tool\nThis is an application without compromise.\n\n## Features\nSupports CSV export.\n");var out=rules(s);var p=out.preview();
        assertTrue(ReadmeRecommendationProjection.anchorContext(p).isEmpty());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,block(p,"CSV export").blockId()).disposition());
    }
}
