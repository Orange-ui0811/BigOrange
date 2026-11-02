package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;
import static org.junit.jupiter.api.Assertions.*;

class ReadmeRecommendationProjectionTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ReadmeKnowledgeFilter filter = new ReadmeKnowledgeFilter();
    private Source source(String raw) { return new Source("test", "v1", "owner/tool", raw); }
    private Output rules(String raw) { Source s=source(raw); return new ReadmeRecommendationProjection().project(s,filter.preview(s),List.of()); }
    private Assessment assessment(Output o,String needle) {
        String id=o.preview().blocks().stream().filter(b->b.text().contains(needle)).findFirst().orElseThrow().blockId();
        return o.projection().assessments().stream().filter(a->a.blockId().equals(id)).findFirst().orElseThrow();
    }
    private ReadmeModelReview model(Consumer<ObjectNode> edit) {
        return new ReadmeModelReview("SIMULATED_R3",new Limits(1,16000,2048,8,0,100000),temp.resolve("cache"),
                new ReadmeReviewJournal(temp.resolve("simulated.jsonl"),"r3-"+UUID.randomUUID(),true),(system,user,output)->{
            ArrayNode rows=JSON.createArrayNode();
            for(JsonNode target:JSON.readTree(user).path("targets")) {
                ObjectNode row=rows.addObject().put("blockId",target.path("blockId").asText()).put("decision","KEEP")
                        .put("kind","TECHNICAL_TEXT").put("scope","CORE").put("reason","KNOWLEDGE").put("applicability","IN_SCOPE");
                row.putArray("evidenceBlockIds").add(target.path("blockId").asText());
                edit.accept(row);
            }
            return new Reply(JSON.createObjectNode().put("schemaVersion",SCHEMA).set("selections",rows).toString(),"stop",Map.of());
        });
    }
    @Test void validCourseAdministrationIsArchivedWithoutBecomingNoiseOrProjectEvidence() {
        String raw="# Tool\nThis is a library for building web apps.\n\n## Certification\nYou must finish all quizzes to qualify for the exam.\n";
        var out=rules(raw);var a=assessment(out,"quizzes");
        assertEquals(Decision.KEEP,a.effectiveDecision()); assertEquals(Applicability.OUT_OF_SCOPE,a.applicability());
        assertEquals(Disposition.ARCHIVE_ONLY,a.disposition()); assertFalse(out.preview().knowledgeCandidates().isEmpty());
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(e->e.blockId().equals(a.blockId())));
    }
    @Test void mixedRepositoryKeepsRunnableExampleAndExcludesCertification() {
        var out=rules("# Learning\nYou must finish quizzes to qualify for the exam.\n\n## Runnable example\nThis is an application for tracking inventory.\n\n## Installation\n```sh\n./run-demo\n```\n");
        assertEquals(Disposition.ARCHIVE_ONLY,assessment(out,"quizzes").disposition());
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out,"tracking inventory").disposition());
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out,"./run-demo").disposition());
    }
    @Test void directoryLinksOnlyProduceLiteralNavigation() {
        var out=rules("# Resources\nA curated list of tools.\n\n- [备份](https://github.com/example/guide#backups)\n- [协作](#collaboration)\n");
        assertTrue(out.preview().knowledgeCandidates().isEmpty()); assertEquals(2,out.projection().pointers().size());
        assertTrue(out.preview().pointerCandidates().stream().allMatch(p->p.contextBlockIds().isEmpty()));
        assertEquals("备份",out.projection().pointers().getFirst().label());
        assertEquals("https://github.com/example/guide#backups",out.projection().pointers().getFirst().href());
    }
    @Test void unsafeOrUnparsedLinkCannotBePromotedToNavigation() {
        var out=rules("# Resources\n[备份](javascript:alert(1))\n");
        assertTrue(out.preview().knowledgeCandidates().isEmpty()); assertTrue(out.projection().pointers().isEmpty());
    }
    @Test void enterpriseAndPlatformQualifiersTravelWithCandidates() {
        var out=rules("# Tool\nThis is an application for collaboration.\n\n## Enterprise Windows Features\nSupports audit logs.\n");
        var a=assessment(out,"audit logs"); assertTrue(a.scopes().containsAll(List.of(Scope.ENTERPRISE,Scope.PLATFORM_SPECIFIC)));
        assertFalse(a.scopes().contains(Scope.CORE));
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).anyMatch(a::equals));
    }
    @Test void mixedEditionsStayReviewEvenUnderEnterpriseHeading() {
        var out=rules("# Tool\nThis is a library for data processing.\n\n## Enterprise Features\nCommunity edition has an editor; Enterprise includes SSO.\n");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out,"Community edition").disposition());
    }
    @Test void ruleKeepWithUnknownPurposeCannotBypassAdmissionOrModelAssessment() throws Exception {
        Source s=source("# Features\nSupports frobnication.\n"); var p=filter.preview(s);
        var unresolved=new ReadmeModelReview("offline",Limits.offline(),temp.resolve("none"),new ReadmeReviewJournal(temp.resolve("zero.jsonl"),"zero",true),
                (a,b,c)->{fail("No real transport");return null;}).review(s,p);
        assertTrue(unresolved.preview().knowledgeCandidates().isEmpty()); assertFalse(unresolved.batches().isEmpty());
        var result=model(row->{}).review(s,p);
        assertFalse(result.preview().knowledgeCandidates().isEmpty());
        assertEquals("SIMULATED_R3",result.recommendation().versions().modelIdentity());
    }
    @Test void modelProductScopeSurvivesMergeInsteadOfBecomingCore() throws Exception {
        Source s=source("# Tool\n未来可以研究此相关产品的调试能力。\n");
        var result=model(row->row.put("scope","RELATED_PRODUCT")).review(s,filter.preview(s));
        var a=result.recommendation().assessments().stream().filter(x->x.disposition()==Disposition.KNOWLEDGE_CANDIDATE).findFirst().orElseThrow();
        assertEquals(List.of(Scope.RELATED_PRODUCT),a.scopes());
        assertTrue(result.recommendation().knowledgeCandidates().getFirst().evidence().contains(a));
    }
    @Test void modelCannotExcludeAProjectRestrictionToEnableOtherContent() throws Exception {
        Source s=source("# Tool\nThis is a library for rendering.\n\n## Browser support\nA browser needs 4 GB memory.\n");
        var result=model(row->row.put("applicability","OUT_OF_SCOPE")).review(s,filter.preview(s));
        assertTrue(result.preview().knowledgeCandidates().isEmpty());
        // A valid restriction is not inferred to be course administration from the model label.
        assertTrue(result.recommendation().assessments().stream().noneMatch(a->a.disposition()==Disposition.KNOWLEDGE_CANDIDATE));
        assertTrue(result.recommendation().assessments().stream().anyMatch(a->a.reason().equals("PROTECTED_CONDITION_SCOPE_CONFLICT")));
    }
    @Test void outOfScopePrerequisiteCannotLeakThroughContext() {
        var out=rules("# Tool\nThis is a library for rendering.\n\n## Usage\nYou must finish quizzes to qualify for the exam:\n\n```sh\n./run-example\n```\n");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out,"./run-example").disposition());
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(a->a.applicability()==Applicability.OUT_OF_SCOPE));
    }
    @Test void protocolRejectsMissingForeignDuplicateAndUncertainEvidence() throws Exception {
        for(String fault:List.of("missing","foreign","duplicate","unknownApp")) {
            Source s=source("# Tool\n一段尚未判定的项目说明。\n");
            var result=model(row->{
                if(fault.equals("missing"))row.remove("evidenceBlockIds");
                if(fault.equals("foreign"))row.putArray("evidenceBlockIds").add("other-project");
                if(fault.equals("duplicate"))((ArrayNode)row.path("evidenceBlockIds")).add(row.path("blockId").asText());
                if(fault.equals("unknownApp"))row.put("applicability","UNKNOWN");
            }).review(s,filter.preview(s));
            assertTrue(result.selections().isEmpty(),fault); assertTrue(result.preview().knowledgeCandidates().isEmpty(),fault);
            assertTrue(result.attempts().getFirst().status().startsWith("OUTPUT_REJECTED_"),fault);
        }
    }
    @Test void curriculumTextIsExcludedWhileRunnableModuleSurvives() {
        var out=rules("# Demo\nThis is an application for managing tasks.\n\n## Course Overview\nOverview of the lessons and practice activities.\n\n## Installation\n```sh\n./start\n```\n");
        assertEquals(Disposition.ARCHIVE_ONLY,assessment(out,"lessons").disposition());
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out,"./start").disposition());
    }
    @Test void cryptographicCertificatesAreNotCourseAdministration() {
        var out=rules("# TLS Tool\nThis is a library for secure networking.\n\n## Features\nSupports TLS certificates and private keys.\n");
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out,"TLS certificates").disposition());
    }
    @Test void oversizedPacketCannotBeReportedAsUsableKnowledge() {
        var out=rules("# Tool\nThis is an application for file management.\n\n## Usage\n```txt\n"+"x".repeat(65000)+"\n```\n");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out,"x".repeat(100)).disposition());
        assertEquals("NO_BUDGETED_KNOWLEDGE_PACKET",assessment(out,"x".repeat(100)).reason());
    }
    @Test void navigationSurvivesOversizedBodyGuardWithoutCopyingIt() {
        var out=rules("# Tool\nThis is a library for rendering.\n\n## License\nLicense conditions: "+"x".repeat(65000)+"\n\n## Documentation\n[API](https://example.com/api)\n");
        assertEquals(1,out.projection().pointers().size());
        assertTrue(out.preview().pointerCandidates().getFirst().contextBlockIds().isEmpty());
    }
    @Test void licenseAndRequirementLinksRemainNavigationWithExplicitKnowledgeGap() {
        var out=rules("# Tool\nThis is a library for rendering.\n\n[MIT License](LICENSE)\n\n[Requires GPU](requirements.md)\n");
        assertEquals(2,out.projection().pointers().size());
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(out,"Requires GPU").disposition());
        assertTrue(out.preview().qualityFlags().contains("UNREAD_CONDITION_POINTERS_NOT_VERIFIED"));
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(a->a.kind()==Kind.SOURCE_POINTER));
    }
    @Test void proseWithLinksRequiresClassificationInsteadOfSilentlyBecomingNavigation() throws Exception {
        Source s=source("# Tool\nThis is a library for rendering.\n\n## Setup\nFollow the [installation guide](https://example.com/npm) and configure the renderer cache.\n");
        var p=filter.preview(s);String id=p.blocks().getLast().blockId();
        assertTrue(p.blocks().getLast().knowledgeTypeHints().contains("SOURCE_POINTER"),"Exercise the old paragraph-as-pointer rule");
        var result=model(row->{}).review(s,p);
        assertTrue(result.selections().stream().anyMatch(a->a.blockId().equals(id)));
        assertTrue(result.recommendation().assessments().stream().anyMatch(a->a.blockId().equals(id)&&a.disposition()==Disposition.KNOWLEDGE_CANDIDATE));
    }
    @Test
    @EnabledIfSystemProperty(named="readme.recommendation.root",matches=".+")
    void allFrozenSourcesHaveCompleteMetadataAndNoDispositionLeaks() throws Exception {
        Path root=Path.of(System.getProperty("readme.recommendation.root"));
        var corpus=JSON.readTree(Files.readString(root.resolve("_experiment_staging/readme-filter-v1-20261029/snapshot.json")));
        int documents=0,body=0,navigation=0,archived=0,review=0;
        List<Map<String,Object>> examples=new ArrayList<>();
        for(JsonNode d:corpus.path("documents")) {
            Source s=new Source(d.path("documentId").asText(),d.path("versionId").asText(),d.path("sourceRef").asText(),d.path("content").asText());
            assertEquals(d.path("sha256").asText(),sha256(s.text()));
            var out=new ReadmeRecommendationProjection().project(s,filter.preview(s),List.of());
            ReadmeRecommendationProjection.verify(s,out.preview(),out.projection());
            assertEquals(s.text(),out.preview().blocks().stream().map(Block::text).reduce("",String::concat));
            body+=out.preview().knowledgeCandidates().size();navigation+=out.projection().pointers().size();
            archived+=(int)out.projection().assessments().stream().filter(a->a.disposition()==Disposition.ARCHIVE_ONLY).count();
            review+=out.projection().reviewBlockIds().size();documents++;
            if(Set.of("46","51","267","329","523").contains(s.documentId())) examples.add(Map.of("documentId",s.documentId(),"sourceRef",s.sourceRef(),"result",out));
        }
        assertEquals(303,documents);
        Path out=root.resolve("_experiment_staging/readme-recommendation-v3");Files.createDirectories(out);
        Files.writeString(out.resolve("corpus-check.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "documents",documents,"bodyPackets",body,"navigationLinks",navigation,"archivedBlocks",archived,"reviewBlocks",review,
                "sourceMappingErrors",0,"dispositionLeaks",0,"additionalProviderCalls",0,"indexWrites",0)));
        Files.writeString(out.resolve("examples.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(examples));
    }
}
