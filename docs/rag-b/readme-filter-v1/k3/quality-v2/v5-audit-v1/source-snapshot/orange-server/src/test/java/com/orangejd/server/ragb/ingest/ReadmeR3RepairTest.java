package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;

class ReadmeR3RepairTest {
    private final ReadmeKnowledgeFilter filter=new ReadmeKnowledgeFilter();
    private Source source(String text){return new Source("repair","v1","owner/cuboid",text);}
    private Output rules(String text){var s=source(text);return new ReadmeRecommendationProjection().project(s,filter.preview(s),List.of());}
    private Block block(Preview p,String needle){return p.blocks().stream().filter(b->b.text().contains(needle)).findFirst().orElseThrow();}
    private Assessment at(Output out,String needle){String id=block(out.preview(),needle).blockId();return out.projection().assessments().stream().filter(a->a.blockId().equals(id)).findFirst().orElseThrow();}
    private Output proposed(String text,String needle,Kind kind,Scope scope,Applicability app,Decision decision){
        var s=source(text);var p=filter.preview(s);var b=block(p,needle);
        return ReadmeModelReview.assembleSelections(s,p,List.of(new Selection(b.blockId(),decision,kind,scope,
                decision==Decision.REVIEW?Reason.UNCERTAIN:kind==Kind.SOURCE_POINTER?Reason.POINTER:Reason.KNOWLEDGE,app,List.of(b.blockId()))),"SIMULATED","REPAIR_TEST");
    }
    @Test void htmlNavigationKeepsLabelsHrefAndRelatedProductScope(){
        var out=proposed("# Cuboid\nThis is an application for reports.\n\n<a href=\"https://example.test/cloud?a=1&amp;b=2\" target=\"_blank\">Hosted instance</a> |\n",
                "Hosted instance",Kind.SOURCE_POINTER,Scope.RELATED_PRODUCT,Applicability.IN_SCOPE,Decision.KEEP);
        var a=at(out,"Hosted instance");assertEquals(Disposition.POINTER_METADATA_ONLY,a.disposition());
        assertEquals(List.of(Scope.RELATED_PRODUCT),a.scopes());assertEquals("Hosted instance",out.projection().pointers().getFirst().label());
        assertEquals("https://example.test/cloud?a=1&amp;b=2",out.projection().pointers().getFirst().href());
        assertTrue(out.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(e->e.blockId().equals(a.blockId())));
    }
    @Test void unresolvedPointerCannotFallThroughToBody(){
        var out=proposed("# Cuboid\nThis is a library for storage.\n\nVisit the incomplete documentation.\n","incomplete documentation",Kind.SOURCE_POINTER,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"incomplete documentation").disposition());
    }
    @Test void imageOnlyLicenseCannotBePromotedByModelOrPoisonBody(){
        for(String badge:List.of("[![License: MIT](https://img.shields.io/license-MIT)](#license)",
                "<a href=\"LICENSE\"><img alt=\"License\" src=\"badge.svg\"></a>","![requires GPU](badge.svg)")){
            var out=proposed("# Cuboid\nThis is an application for reports.\n\n"+badge+"\n",badge,Kind.LICENSE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
            assertEquals(Disposition.REVIEW_QUEUE,at(out,badge).disposition());
            assertFalse(out.projection().knowledgeCandidates().isEmpty());
            assertFalse(ReadmeSourceSemantics.condition(block(out.preview(),badge)));
        }
    }
    @Test void imageUrlsAndAttributesDoNotBecomeGlobalRequirements(){
        var out=rules("# Cuboid\nThis is an application for reports.\n\n<img src=\"logo.svg#gh-light-mode-only\" alt=\"requires CPU 8GB\">\n\n## Features\nSupports CSV export.\n");
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,"CSV export").disposition());
        assertFalse(ReadmeSourceSemantics.condition(block(out.preview(),"mode-only")));
    }
    @Test void localPlatformConditionDoesNotContaminateOtherDeployment(){
        var out=rules("# Cuboid\nThis is an application for reports.\n\n## Windows deployment\nWindows requires 8 GB RAM.\n\n## Linux deployment\n```sh\n./start-linux\n```\n");
        String linux=block(out.preview(),"./start-linux").blockId();String win=block(out.preview(),"8 GB").blockId();
        assertTrue(out.projection().knowledgeCandidates().stream().filter(c->c.packet().primaryBlockIds().contains(linux))
                .allMatch(c->!c.packet().contextBlockIds().contains(win)));
    }
    @Test void unresolvedProjectLicenseStillBlocksAllBody(){
        var out=proposed("# Cuboid\nThis is an application for reports.\n\n## License\nLicense conditions require attribution.\n","conditions require",Kind.LICENSE,Scope.CORE,Applicability.UNKNOWN,Decision.REVIEW);
        assertTrue(out.projection().knowledgeCandidates().isEmpty());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"application").disposition());
    }
    @Test void realRequirementsDoNotDisappearThroughScopeExclusion(){
        var out=proposed("# Cuboid\nThis is an application for reports.\n\n## Requirements\nRequires 8 GB RAM.\n","8 GB",Kind.LIMITATION,Scope.CORE,Applicability.OUT_OF_SCOPE,Decision.KEEP);
        assertTrue(out.projection().knowledgeCandidates().isEmpty());
        assertEquals("PROTECTED_CONDITION_SCOPE_CONFLICT",at(out,"8 GB").reason());
    }
    @Test void resourceCollectionCannotBecomeItsOwnApplication(){
        var out=proposed("# Collection\n这是一个可以托管在自己服务器上的自由软件网络服务和 Web 应用程序列表。\n\n- [工具](https://example.test/tool)\n",
                "这是一个",Kind.PURPOSE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals(Disposition.ARCHIVE_ONLY,at(out,"这是一个").disposition());assertTrue(out.projection().knowledgeCandidates().isEmpty());
        assertEquals(1,out.projection().pointers().size());
    }
    @Test void configurationCollectionNeedsQualificationAndKeepsVersionCondition(){
        var out=proposed("# Configuration\nPretested firmware configurations.\n\n## Usage\n必须选择与固件版本对应的正确分支。\n",
                "必须",Kind.USAGE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        var a=at(out,"必须");assertEquals(Disposition.REVIEW_QUEUE,a.disposition());assertTrue(a.scopes().contains(Scope.VERSION_SPECIFIC));
    }
    @Test void mixedProductVariantsAreNotCollapsedIntoCore(){
        var out=proposed("# Cuboid\nThis is a library for storage.\n\n## Features\nCuboid supports distributed storage; [Cuboid Lite](lite.md) is a separate lightweight version.\n",
                "distributed storage",Kind.TECHNICAL_TEXT,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"distributed storage").disposition());
    }
    @Test void incompleteEndOfSourceIsNotRescuedByOldKeep(){
        var text="# Cuboid\nThis is a library for storage.\n\n## Usage\nUse StorageClient to create a cli";
        var out=proposed(text,"create a cli",Kind.USAGE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals("POSSIBLY_TRUNCATED_TEXT",block(out.preview(),"create a cli").reason());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"create a cli").disposition());assertEquals(text,out.preview().blocks().stream().map(Block::text).reduce("",String::concat));
    }
    @Test void optionExamplesCarryDefinitionAcrossSections(){
        var out=proposed("# Cuboid\nThis is an application for checking files.\n\n## CLI\n```text\nOptions:\n  -fail-on string\n    Default high.\n```\n\n## Examples\n```sh\ncuboid --fail-on medium ./docs\n```\n\n## Exit status\nExit code is 1 if --fail-on severity is met.\n", "Exit code",Kind.USAGE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        String definition=block(out.preview(),"Options:").blockId();
        for(String needle:List.of("cuboid --fail-on","Exit code")){
            String bid=block(out.preview(),needle).blockId();
            assertTrue(out.projection().knowledgeCandidates().stream().anyMatch(c->c.packet().primaryBlockIds().contains(bid)
                    &&(c.packet().primaryBlockIds().contains(definition)||c.packet().contextBlockIds().contains(definition))));
        }
    }
    @Test void languageVersionsAreNotOnlyPlatformLabels(){
        var out=rules("# Cuboid\nThis is an application for checking files.\n\n## Requirements\nRequires Go 1.22+ on Linux.\n");
        assertTrue(at(out,"Go 1.22").scopes().containsAll(List.of(Scope.VERSION_SPECIFIC,Scope.PLATFORM_SPECIFIC)));
    }
    @Test void navigationDoesNotInheritUnresolvedGlobalGuards(){
        var out=proposed("# Cuboid\nThis is a library for storage.\n\n## License\nLicense terms require attribution.\n\n## Contributing\n请查看我们的[贡献指南](CONTRIBUTING.md)，了解设置指南和最佳实践。\n",
                "License terms",Kind.LICENSE,Scope.CORE,Applicability.UNKNOWN,Decision.REVIEW);
        assertTrue(out.projection().knowledgeCandidates().isEmpty());assertEquals(1,out.projection().pointers().size());
    }
    @Test void unsafeHtmlAndCodeAreNotNavigable(){
        var out=rules("# Cuboid\nThis is a library for storage.\n\n<a href='javascript:alert(1)'>Docs</a>\n\n```html\n<a href='https://example.test/fake'>example data</a>\n```\n");
        assertTrue(out.projection().pointers().isEmpty());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"javascript:").disposition());
    }
    @Test void factualClausesAfterLinksAreNotDiscardedAsNavigation(){
        var out=proposed("# Cuboid\nThis is a library for storage.\n\n## Features\nSee [Docs](docs.md). This library supports TLS certificates.\n",
                "supports TLS",Kind.TECHNICAL_TEXT,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,"supports TLS").disposition());
    }
    @Test void genuineMaintenanceWarningStillTravelsAcrossSections(){
        var out=rules("# Cuboid\nThis is an application for storage.\n\n## Maintenance\nThis project is deprecated.\n\n## Features\nSupports CSV exports.\n");
        String warning=block(out.preview(),"deprecated").blockId();String feature=block(out.preview(),"CSV exports").blockId();
        assertTrue(out.projection().knowledgeCandidates().stream().filter(c->c.packet().primaryBlockIds().contains(feature))
                .allMatch(c->c.packet().contextBlockIds().contains(warning)));
        assertEquals(Disposition.KNOWLEDGE_CANDIDATE,at(out,"CSV exports").disposition());
    }
    @Test void inconsistentModuleCountIsNotRewrittenOrPromoted(){
        var out=proposed("# Cuboid\nThis is an application for storage.\n\n## Development\nCuboid contains 3 different modules.\n\n- server\n- ui\n- plugins\n- docs\n",
                "3 different",Kind.ARCHITECTURE,Scope.CORE,Applicability.IN_SCOPE,Decision.KEEP);
        assertEquals("DECLARED_LIST_COUNT_CONFLICT",at(out,"3 different").reason());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"3 different").disposition());
    }
    @Test void indentedCommandsKeepLeadInAndPackageManagerPrerequisite(){
        var out=rules("# Cuboid\nThis is an application for storage.\n\n## Development\n### Requirements\nInstall PNPM:\n\n    ```sh\n    npm i -g pnpm\n    ```\n\n### Setup\nBuild the application:\n\n    ```sh\n    pnpm build\n    ```\n");
        String build=block(out.preview(),"pnpm build").blockId();String required=block(out.preview(),"npm i -g").blockId();String lead=block(out.preview(),"Build the application:").blockId();
        var edges=ReadmeKnowledgeFilter.dependencies(out.preview().blocks());
        assertTrue(edges.get(build).containsAll(List.of(required,lead)));
    }
    @Test void sourceCompilerRequirementStaysWithItsOwnCliAndNotUnrelatedFeatures(){
        var out=rules("# Cuboid\nThis is an application for storage.\n\n## Install from source\n```sh\ngo install example.test/owner/cmd/cuboid@latest\n```\nRequires Go 1.22+.\n\n## CLI\n```txt\ncuboid [options] <path>\nOptions:\n -json\n   Output JSON.\n```\n\n## Features\nSupports reports.\n");
        String cli=block(out.preview(),"Options:").blockId();String go=block(out.preview(),"Go 1.22").blockId();String feature=block(out.preview(),"Supports reports").blockId();
        assertTrue(ReadmeKnowledgeFilter.dependencies(out.preview().blocks()).get(cli).contains(go));
        assertTrue(out.projection().knowledgeCandidates().stream().filter(c->c.packet().primaryBlockIds().contains(feature)).allMatch(c->!c.packet().contextBlockIds().contains(go)));
    }
    @Test void indentedFencePiecesAndConditionalExplanationAreAtomic(){
        var s=source("# Cuboid\nThis is an application for storage.\n\n## Setup\n    如果遇到错误，请增加堆大小：\n\n    ```bash\n    # Linux\n    export NODE_OPTIONS=4096\n\n    # Windows PowerShell\n    $env:NODE_OPTIONS=4096\n\n    # Windows CMD\n    set NODE_OPTIONS=4096\n    ```\n");
        var p=filter.preview(s);var pieces=ReadmeSourceSemantics.indentedSpans(p.blocks());assertEquals(1,pieces.size());assertTrue(pieces.getFirst().complete());
        var edges=ReadmeKnowledgeFilter.dependencies(p.blocks());String lead=block(p,"如果").blockId();
        for(Block b:pieces.getFirst().blocks())assertTrue(edges.get(b.blockId()).contains(lead));
        for(Block b:pieces.getFirst().blocks())for(Block other:pieces.getFirst().blocks())if(!other.equals(b))assertTrue(edges.get(b.blockId()).contains(other.blockId()));
    }
    @Test void unclosedIndentedExampleCannotBecomeStandaloneUsage(){
        var out=rules("# Cuboid\nThis is an application for storage.\n\n## Usage\n    ```sh\n    start --unsafe\n");
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"start --unsafe").disposition());
    }
    @Test void entityEncodedUnsafeHtmlSchemeIsRejected(){
        var out=rules("# Cuboid\nThis is a library for storage.\n\n<a href='javascript&#58;alert(1)'>Docs</a>\n");
        assertTrue(out.projection().pointers().isEmpty());assertEquals(Disposition.REVIEW_QUEUE,at(out,"javascript").disposition());
    }
    @Test void cloudSignupIsNavigationEvenUnderInstallationHeading(){
        var out=rules("# Cuboid\nThis is an application for storage.\n\n## Installation\n- Signup on [Cuboid Cloud](https://example.test/cloud).\n- Install Cuboid on your machine. See the installation guides below.\n");
        var a=at(out,"Signup");assertEquals(Disposition.POINTER_METADATA_ONLY,a.disposition());
        assertEquals(List.of(Scope.RELATED_PRODUCT),a.scopes());assertEquals(1,out.projection().pointers().size());
    }
    @Test void outOfScopeNavigationCannotBypassUnreviewedSourceState(){
        var out=rules("# Resources\nA curated list of tools.\n\nVisit [website](https://example.test/site) to join.\n");
        assertEquals(Decision.REVIEW,block(out.preview(),"to join").decision());
        assertEquals(Disposition.REVIEW_QUEUE,at(out,"to join").disposition());assertTrue(out.projection().pointers().isEmpty());
    }
}
