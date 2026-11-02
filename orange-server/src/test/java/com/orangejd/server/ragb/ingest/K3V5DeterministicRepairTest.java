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

/** Real frozen failures plus counterexamples; no live provider or business/index bootstrap. */
@EnabledIfSystemProperty(named="readme.v5.repair.root",matches=".+")
class K3V5DeterministicRepairTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path root=Path.of(System.getProperty("readme.v5.repair.root"));
    @TempDir Path temp;
    private final ReadmeKnowledgeFilter filter=new ReadmeKnowledgeFilter();
    private Source frozen(String id)throws Exception{
        for(var row:JSON.readTree(Files.readString(root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/r4/sources.json"))).path("documents"))
            if(row.path("source").path("documentId").asText().equals(id))return JSON.treeToValue(row.path("source"),Source.class);
        throw new AssertionError("Missing frozen document");
    }
    private Block line(Preview p,int at){return p.blocks().stream().filter(b->b.startLine()==at).findFirst().orElseThrow();}
    private Assessment assessment(Projection p,String id){return p.assessments().stream().filter(a->a.blockId().equals(id)).findFirst().orElseThrow();}
    private ReadmeModelReview engine(int maximum){return new ReadmeModelReview("OFFLINE_V5_REPAIR",new Limits(0,12000,2048,maximum,0,0),
            temp.resolve("cache"),new ReadmeReviewJournal(temp.resolve("unused.jsonl"),"OFFLINE",true),(s,u,o)->{throw new AssertionError("No provider allowed");});}

    @Test void layoutDoesNotBlockFlowiseStartupPlanning()throws Exception{
        Source source=frozen("244");var p=filter.preview(source);String layout=line(p,145).blockId();
        var dependencies=ReadmeKnowledgeFilter.dependencies(p.blocks());
        assertFalse(dependencies.values().stream().anyMatch(refs->refs.contains(layout)),"Pure closing markup is not a code prerequisite");
        Set<String> targets=new HashSet<>();engine(8).plan(source,p).forEach(b->targets.addAll(b.targetIds()));
        for(int at:List.of(139,141,147,149))assertTrue(targets.contains(line(p,at).blockId()),"Missing Flowise model target at line "+at);
        assertEquals(source.text(),p.blocks().stream().map(Block::text).reduce("",String::concat));
    }
    @Test void licenseNavigationIsNotGlobalGuard()throws Exception{
        Source source=frozen("427");var p=filter.preview(source);Block nav=line(p,59);
        assertFalse(ReadmeSourceSemantics.condition(nav),"Unread license navigation does not need body admission");
        var out=ReadmeModelReview.assembleSelections(source,p,List.of(new Selection(nav.blockId(),Decision.KEEP,Kind.SOURCE_POINTER,Scope.UNKNOWN,
                Reason.POINTER,Applicability.UNKNOWN,List.of(nav.blockId()))),"SIMULATED","NAVIGATION_TEST");
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(out.projection(),nav.blockId()).disposition());
    }
    @Test void appsmithGuideAndDirectoryCannotPassAsBody()throws Exception{
        Source source=frozen("522");var p=filter.preview(source);var out=new ReadmeRecommendationProjection().project(source,p,List.of());
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),line(p,34).blockId()).disposition());
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(out.projection(),line(p,39).blockId()).disposition());
        var pointers=out.projection().pointers().stream().filter(x->x.evidenceBlockId().equals(line(p,39).blockId())).toList();
        assertEquals(3,pointers.size());
        var docker=pointers.stream().filter(x->x.label().equals("Docker")).findFirst().orElseThrow();
        assertTrue(JSON.valueToTree(docker).path("hints").toString().contains("Recommended"));
        for(var c:out.projection().knowledgeCandidates())assertTrue(c.evidence().stream().noneMatch(a->List.of(line(p,34).blockId(),line(p,39).blockId()).contains(a.blockId())));
    }
    private Source source(String text){return new Source("test","v1","owner/tool",text);}
    private Block text(Preview p,String needle){return p.blocks().stream().filter(b->b.text().contains(needle)).findFirst().orElseThrow();}
    private Output rules(String text){var s=source(text);return new ReadmeRecommendationProjection().project(s,filter.preview(s),List.of());}
    @Test void licenseInfoNavigationDoesNotBlockKnownFactsOrClaimVerifiedLicense(){
        var out=rules("# Tool\nThis is an application for reports.\n\n## License\nMIT license applies with attribution.\n\n有关许可证模式的更多信息，请参阅[文档](https://example.test/license)。\n");
        String id=text(out.preview(),"更多信息").blockId();
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(out.projection(),id).disposition());
        assertFalse(out.projection().knowledgeCandidates().isEmpty());
        assertTrue(out.preview().qualityFlags().contains("UNREAD_CONDITION_POINTERS_NOT_VERIFIED"));
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(a->a.blockId().equals(id)));
    }
    @Test void actualRestrictionsCannotBeHiddenByNavigationClassification(){
        for(String warning:List.of("Requires 8 GB RAM. See [docs](https://example.test/docs).",
                "See [license](LICENSE). Commercial use is prohibited.","See [docs](docs.md). Not for production use.")){
            var s=source("# Tool\nThis is an application for reports.\n\n## Requirements\n"+warning+"\n");var p=filter.preview(s);Block b=text(p,warning);
            assertTrue(ReadmeSourceSemantics.condition(b),warning);
            var out=ReadmeModelReview.assembleSelections(s,p,List.of(new Selection(b.blockId(),Decision.KEEP,Kind.SOURCE_POINTER,Scope.UNKNOWN,Reason.POINTER,Applicability.UNKNOWN,List.of(b.blockId()))),"SIMULATED","NEGATIVE");
            assertTrue(out.projection().knowledgeCandidates().isEmpty(),warning);
        }
    }
    @Test void actualLicenseParagraphAndExceptionsRemainBody(){
        var out=rules("# Tool\nThis is an application for reports.\n\n## License\nMIT license applies except to embedded commercial assets.\n");
        Block b=text(out.preview(),"except to");assertTrue(ReadmeSourceSemantics.condition(b));
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out.projection(),b.blockId()).disposition());
    }
    @Test void mixedLicenseModesStillNeedReviewAndCannotBeCalledUnrestricted()throws Exception{
        Source source=frozen("427");var p=filter.preview(source);var old=JSON.readTree(Files.readString(root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/r5/results/primary/427.json"))).path("result");
        List<Selection> choices=new ArrayList<>();for(var s:old.path("selections"))choices.add(JSON.treeToValue(s,Selection.class));
        var out=ReadmeModelReview.assembleSelections(source,p,choices,"OLD_SELECTIONS","MIXED_LICENSE_TEST");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),line(p,51).blockId()).disposition());
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),line(p,53).blockId()).disposition());
        assertEquals(Disposition.POINTER_METADATA_ONLY,assessment(out.projection(),line(p,59).blockId()).disposition());
    }
    @Test void sourceFactsAndNegativeSupportTablesAreNotNavigation(){
        var out=rules("# Tool\nThis is an application for reports.\n\n## Features\n| Feature | Linux | Documentation |\n|---|---|---|\n| Authentication | No | [Details](https://example.test/auth) |\n");
        Block b=text(out.preview(),"Authentication");assertFalse(ReadmeSourceSemantics.navigationTable(b));
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out.projection(),b.blockId()).disposition());
        assertTrue(b.text().contains("No"));
    }
    @Test void sourceGuideCannotBePromotedByModelAndConcreteStepsStillWork(){
        var s=source("# Tool\nThis is an application for reports.\n\n## Installation\nThere are two ways to start using Tool:\n\n```sh\ntool --local\n```\n");var p=filter.preview(s);Block lead=text(p,"two ways");
        var out=ReadmeModelReview.assembleSelections(s,p,List.of(new Selection(lead.blockId(),Decision.KEEP,Kind.USAGE,Scope.CORE,Reason.KNOWLEDGE,Applicability.IN_SCOPE,List.of(lead.blockId()))),"SIMULATED","GUIDE");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),lead.blockId()).disposition());
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,assessment(out.projection(),text(p,"tool --local").blockId()).disposition());
    }
    @Test void layoutInsideIndentedCodeIsNeverDeletedAsMarkup(){
        var p=filter.preview(source("# Tool\n\n## Example\n    ```html\n    <details>\n\n    </details>\n\n    ```\n"));
        for(var b:p.blocks())if(b.text().contains("</details>"))assertNotEquals(Decision.DROP,b.decision());
    }
    @Test void errorExplanationAndRestoreStayMandatory(){
        var p=filter.preview(source("# Tool\nThis is an application for reports.\n\n## Setup\n    <details>\n    <summary>Error recovery</summary>\n    如果遇到错误，请恢复配置：\n\n    ```sh\n    isolate app\n\n    restore app\n    ```\n\n    </details>\n"));
        String cause=text(p,"如果遇到").blockId();String code=text(p,"isolate app").blockId();
        assertTrue(ReadmeKnowledgeFilter.dependencies(p.blocks()).get(code).contains(cause));
        assertNotEquals(Decision.DROP,text(p,"restore app").decision());
    }
    @Test void sharedPrerequisitesCanBeSeparateButMissingApprovalStillBlocksUse()throws Exception{
        var s=source("# Tool\nThis is an application for reports.\n\n## Requirements\nInstall PNPM via this setup:\n\n    ```sh\n    npm i -g pnpm\n    ```\n\n## Alpha usage\n    ```sh\n    pnpm build alpha\n    ```\n\n## Beta usage\n    ```sh\n    pnpm build beta\n    ```\n");var p=filter.preview(s);
        Set<String> targets=new HashSet<>();engine(4).plan(s,p).forEach(b->targets.addAll(b.targetIds()));
        assertTrue(targets.contains(text(p,"pnpm build alpha").blockId()));assertTrue(targets.contains(text(p,"pnpm build beta").blockId()));
        Block required=text(p,"npm i -g");List<Selection> choices=new ArrayList<>();
        for(Block b:p.blocks())if(targets.contains(b.blockId()))choices.add(new Selection(b.blockId(),b.equals(required)?Decision.REVIEW:Decision.KEEP,
                b.equals(required)?Kind.OTHER:Kind.USAGE,b.equals(required)?Scope.UNKNOWN:Scope.CORE,b.equals(required)?Reason.UNCERTAIN:Reason.KNOWLEDGE,
                b.equals(required)?Applicability.UNKNOWN:Applicability.IN_SCOPE,List.of(b.blockId())));
        var out=ReadmeModelReview.assembleSelections(s,p,choices,"SIMULATED","DEPENDENCY");
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),text(p,"pnpm build alpha").blockId()).disposition());
        assertEquals(Disposition.REVIEW_QUEUE,assessment(out.projection(),text(p,"pnpm build beta").blockId()).disposition());
    }
}
