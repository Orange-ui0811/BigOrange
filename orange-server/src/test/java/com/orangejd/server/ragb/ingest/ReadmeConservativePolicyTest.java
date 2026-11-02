package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;
import static org.junit.jupiter.api.Assertions.*;

class ReadmeConservativePolicyTest {
    private static final String INTRO="# Tool\nThis is an application for managing inventory.\n\n";
    private record Fixture(Source source,ReadmeModelReview.Result classified){}
    private Fixture fixture(String text){
        var s=new Source("test","v1","owner/tool",text);var p=new ReadmeKnowledgeFilter().preview(s);
        var old=new ReadmeRecommendationProjection().project(s,p,List.of());
        return new Fixture(s,new ReadmeModelReview.Result(p,List.of(),List.of(),List.of(),p.reviewBlockIds(),old.projection()));
    }
    private Fixture approveRestriction(Fixture f,String marker){
        var p=f.classified().preview();List<Block> selected=new ArrayList<>();List<Selection> selections=new ArrayList<>();
        for(var b:p.blocks()){
            if(b.text().contains(marker)){
                selected.add(new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),b.startLine(),b.endLine(),b.text(),b.sha256(),b.sectionPath(),b.headingIds(),Decision.KEEP,"TEST_REVIEW_APPROVED_RESTRICTION",b.ruleIds(),List.of("LIMITATION")));
                selections.add(new Selection(b.blockId(),Decision.KEEP,Kind.LIMITATION,Scope.NON_PRODUCTION,Reason.KNOWLEDGE,Applicability.IN_SCOPE,List.of(b.blockId())));
            }else selected.add(b);
        }
        var rebuilt=new ReadmeKnowledgeFilter().reassemble(f.source(),p,selected);
        var old=new ReadmeRecommendationProjection().project(f.source(),rebuilt,selections);
        return new Fixture(f.source(),new ReadmeModelReview.Result(rebuilt,List.of(),List.of(),selections,rebuilt.reviewBlockIds(),old.projection()));
    }
    private ReadmeConservativePolicy.Result result(Fixture f){return new ReadmeConservativePolicy().prepare(f.source(),f.classified());}
    private Assessment assessment(ReadmeConservativePolicy.Result r,String marker){
        var b=r.output().preview().blocks().stream().filter(x->x.text().contains(marker)).findFirst().orElseThrow();
        return r.output().projection().assessments().stream().filter(a->a.blockId().equals(b.blockId())).findFirst().orElseThrow();
    }
    private boolean bodyContains(ReadmeConservativePolicy.Result r,String marker){
        var ids=r.output().projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).map(Assessment::blockId).toList();
        return r.output().preview().blocks().stream().anyMatch(b->ids.contains(b.blockId())&&b.text().contains(marker));
    }
    @Test void mixedLicenceReviewDoesNotVetoIndependentUsageButRemainsUnresolved(){
        var f=fixture(INTRO+"## Usage\nRequires Java 21.\n\n```sh\n./start-tool\n```\n\n## License\nDistributed under [Source License](LICENSE) and [Enterprise License](LICENSE_EE).\n");
        var r=result(f);assertTrue(bodyContains(r,"./start-tool"));assertTrue(bodyContains(r,"Requires Java 21"));
        assertEquals(Disposition.REVIEW_QUEUE,assessment(r,"Distributed under").disposition());
        assertTrue(r.conditions().stream().anyMatch(c->c.status().equals("UNRESOLVED")&&c.affectedConclusion().equals("LICENSE_AND_COMMERCIAL_ELIGIBILITY")));
        assertFalse(r.output().projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).anyMatch(a->a.blockId().equals(assessment(r,"Distributed under").blockId())));
    }
    @Test void unresolvedExamplePrerequisiteStillBlocksItsCode(){
        var f=fixture(INTRO+"## Usage\nCommunity edition requires Node 18; Enterprise requires Node 20:\n\n```sh\n./start-tool\n```\n");
        var r=result(f);assertFalse(bodyContains(r,"./start-tool"));
    }
    @Test void knownNonProductionConditionStillTravelsWithFeature(){
        var r=result(approveRestriction(fixture(INTRO+"Not for production.\n\n## Features\nSupports inventory exports.\n"),"Not for production"));
        var feature=r.output().preview().blocks().stream().filter(b->b.text().contains("Supports inventory")).findFirst().orElseThrow();
        var candidate=r.output().projection().knowledgeCandidates().stream().filter(c->c.packet().primaryBlockIds().contains(feature.blockId())).findFirst().orElseThrow();
        String guard=assessment(r,"Not for production").blockId();assertTrue(candidate.packet().contextBlockIds().contains(guard));
    }
    @Test void pendingSemanticSourceIsNeverPromotedToImproveCoverage(){
        var f=fixture("# Tool\n一段尚未判定的项目说明。\n");var r=result(f);
        var b=f.classified().preview().blocks().stream().filter(x->x.text().contains("尚未判定")).findFirst().orElseThrow();
        assertEquals(Decision.REVIEW,b.decision());assertEquals(Disposition.REVIEW_QUEUE,assessment(r,"尚未判定").disposition());
    }
    @Test void explicitLocalInstallationSurvivesWithNavigationMetadata(){
        var f=fixture(INTRO+"## Installation\nThere are two ways to start using Tool:\n\n- Signup on [Tool Cloud](https://example.org/cloud).\n- Install Tool on your machine. See the installation guides below.\n\n[Docker documentation](https://example.org/docker)\n");
        assertEquals(Decision.KEEP,f.classified().preview().blocks().stream().filter(b->b.text().contains("Install Tool on your machine")).findFirst().orElseThrow().decision());
        var r=result(f);
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(r,"Install Tool on your machine").disposition());
        assertEquals(List.of(Scope.PLATFORM_SPECIFIC),assessment(r,"Install Tool on your machine").scopes());
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(r,"[Docker documentation]").disposition());
        assertFalse(bodyContains(r,"[Docker documentation]"));
    }
    @Test void alternativeDockerExampleDoesNotInheritNpxRuntime(){
        var f=fixture(INTRO+"## Quick start\nUse npx (requires Node.js 20):\n\n```sh\nnpx tool\n```\n\nOr use Docker:\n\n```sh\ndocker run tool\n```\n");
        var r=result(f);String node=assessment(r,"requires Node.js").blockId();
        var docker=r.output().preview().blocks().stream().filter(b->b.text().contains("docker run tool")).findFirst().orElseThrow();
        var c=r.output().projection().knowledgeCandidates().stream().filter(x->x.packet().primaryBlockIds().contains(docker.blockId())).findFirst().orElseThrow();
        assertFalse(c.packet().contextBlockIds().contains(node));
        var npx=r.output().preview().blocks().stream().filter(b->b.text().contains("npx tool")).findFirst().orElseThrow();
        assertTrue(r.output().projection().knowledgeCandidates().stream().filter(x->x.packet().primaryBlockIds().contains(npx.blockId())).allMatch(x->x.packet().contextBlockIds().contains(node)));
    }
    @Test void sourceTraceAndReviewIsolationSurviveThePreparationEntry(){
        var f=fixture(INTRO+"## Features\nSupports exports 😀.\n\n[Documentation](https://example.org/docs)\n\nSupports local search.\n");
        var p=new ReadmeKnowledgePreparation().prepare(f.source(),f.classified(),"test-v5");
        assertEquals(ReadmeConservativePolicy.POLICY,p.admissionPolicy());assertFalse(p.structural().chunks().isEmpty());
        for(var c:p.structural().chunks()){
            assertTrue(c.tokens()<=768);assertFalse(c.text().contains("https://example.org/docs"));
            assertTrue(c.sourceSpans().stream().anyMatch(s->!s.blockKind().equals("HEADING")));
            for(var s:c.sourceSpans()){assertEquals(s.text(),f.source().text().substring(s.startUtf16(),s.endUtf16()));assertEquals(s.sha256(),sha256(s.text()));}
        }
    }
    @Test void pureHeadingCanNeverBeAnIndependentVector(){
        var f=fixture(INTRO+"## Features\nSupports exports.\n\n## Documentation\n[Manual](https://example.org/docs)\n");
        var p=new ReadmeKnowledgePreparation().prepare(f.source(),f.classified(),"test-headings");
        assertTrue(p.structural().chunks().stream().allMatch(c->c.sourceSpans().stream().anyMatch(s->!s.blockKind().equals("HEADING"))));
        assertTrue(p.structural().chunks().stream().anyMatch(c->c.text().contains("# Tool")));
    }
    @Test void onlyLinksAndEmptySourcesDoNotBecomeBodyKnowledge(){
        var f=fixture("# Tool\n\n[Installation](https://example.org/install)\n");
        assertTrue(new ReadmeKnowledgePreparation().prepare(f.source(),f.classified(),"links-only").structural().chunks().isEmpty());
    }
}
