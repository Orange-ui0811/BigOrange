package com.orangejd.server.ragb.ingest;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;
import static org.junit.jupiter.api.Assertions.*;

class ReadmeKnowledgeChunkerTest {
    private static final String INTRO = "# Atlas\nThis is an application for tracking inventory.\n\n";
    private record Fixture(Source source, Output output) {}
    /** Assistant-labeled synthetic fixtures. No model output is acquired or represented as real evaluation. */
    private Fixture fixture(String raw, String... pending) {
        Source source = new Source("test", "v1", "owner/atlas", raw);
        ReadmeKnowledgeFilter filter = new ReadmeKnowledgeFilter(); var initial = filter.preview(source);
        List<Block> decided = new ArrayList<>(); List<Selection> selections = new ArrayList<>();
        for (Block b : initial.blocks()) {
            boolean keepPending = Arrays.stream(pending).anyMatch(b.text()::contains);
            Block reviewed = b;
            if (b.decision() == Decision.REVIEW && !keepPending)
                reviewed = new Block(b.blockId(), b.kind(), b.startUtf16(), b.endUtf16(), b.startLine(), b.endLine(), b.text(), b.sha256(),
                        b.sectionPath(), b.headingIds(), Decision.KEEP, "SYNTHETIC_REVIEW", b.ruleIds(), List.of("TECHNICAL_TEXT"));
            decided.add(reviewed);
            if (!keepPending && reviewed.decision() == Decision.KEEP && !b.kind().equals("HEADING")) {
                Kind kind = b.kind().equals("TABLE") ? Kind.TECHNICAL_TEXT : ReadmeSourceSemantics.codeLike(b) ? Kind.USAGE : Kind.TECHNICAL_TEXT;
                if (ReadmeSourceSemantics.literalNavigation(b.text())) kind = Kind.SOURCE_POINTER;
                Scope scope = b.sectionPath().toString().contains("Enterprise") ? Scope.ENTERPRISE : Scope.CORE;
                selections.add(new Selection(b.blockId(), Decision.KEEP, kind, scope, Reason.KNOWLEDGE,
                        Applicability.IN_SCOPE, List.of(b.blockId())));
            }
        }
        var selected = filter.reassemble(source, initial, decided);
        return new Fixture(source, new ReadmeRecommendationProjection().project(source, selected, selections, "SYNTHETIC", "UNIT_TEST"));
    }
    private ReadmeKnowledgeChunker.Preview preview(Fixture fixture) {
        return new ReadmeKnowledgeChunker().preview(fixture.source(), fixture.output().preview(), fixture.output().projection(),
                new ReadmeKnowledgeChunker.Identity(null, "OFFLINE_TEST"));
    }
    @Test void keepsOriginalCrLfUnicodeAndSeparateSpansAcrossDiscardedNavigation() {
        var fixture = fixture(INTRO.replace("\n", "\r\n") + "## Features\r\nSupports inventory 😀.\r\n\r\n"
                + "[Documentation](https://example.org/docs)\r\n\r\nSupports exports.\r\n");
        var p = preview(fixture);
        assertFalse(p.chunks().isEmpty()); assertNull(p.identity().projectId());
        assertFalse(p.navigation().isEmpty());
        assertTrue(p.chunks().stream().noneMatch(c -> c.embeddingText().contains("https://example.org/docs")));
        assertTrue(p.chunks().stream().anyMatch(c -> c.embeddingText().contains("😀")));
        for (var c : p.chunks()) for (var s : c.sourceSpans()) {
            assertEquals(s.text(), fixture.source().text().substring(s.startUtf16(), s.endUtf16()));
            assertEquals(sha256(s.text()), s.sha256());
        }
        assertTrue(p.chunks().stream().anyMatch(c -> c.embeddingText().contains("\r\n")));
    }
    @Test void unresolvedIndentedInstructionDefersLeadInWithoutPromotingChild() {
        var fixture = fixture(INTRO + "## Development\n1. Development build:\n\n    - Create `.env` and specify `CUSTOM_PORT`.\n", "CUSTOM_PORT");
        var p = preview(fixture);
        assertTrue(p.units().stream().anyMatch(u -> u.status().equals("DEFERRED") && u.reasons().toString().contains("REQUIRED_BLOCK_NOT_ADMITTED")));
        assertTrue(p.chunks().stream().noneMatch(c -> c.embeddingText().contains("Development build:") || c.embeddingText().contains("CUSTOM_PORT")));
    }
    @Test void admittedProceduralPartsAreRecoveredTogetherFromAnyChildHit() {
        var fixture = fixture(INTRO + "## Development\n1. Development build:\n\n    - Create `.env` and specify `CUSTOM_PORT`.\n");
        var p = preview(fixture); var chunker = new ReadmeKnowledgeChunker();
        var hit = p.chunks().stream().filter(c -> c.embeddingText().contains("CUSTOM_PORT")).findFirst().orElseThrow();
        var evidence = chunker.evidence(p, hit.chunkId(), 4096);
        assertEquals("READY", evidence.status()); assertTrue(evidence.text().contains("Development build:"));
        assertTrue(evidence.text().contains("CUSTOM_PORT"));
    }
    @Test void preservesNegativeRequirementsInFullEvidence() {
        var fixture = fixture(INTRO + "## Installation\nRequires Java 21. This build does not support Windows.\n\n```sh\n./run\n```\n");
        var p = preview(fixture); var hit = p.chunks().stream().filter(c -> c.embeddingText().contains("./run")).findFirst().orElseThrow();
        var evidence = new ReadmeKnowledgeChunker().evidence(p, hit.chunkId(), 4096);
        assertTrue(evidence.text().contains("does not support Windows")); assertTrue(evidence.text().contains("Java 21"));
    }
    @Test void keepsEnterpriseAndCorePrimaryScopesSeparate() {
        var p = preview(fixture(INTRO + "## Features\nSupports exports.\n\n## Enterprise\nSupports audit logs.\n"));
        assertTrue(p.units().stream().anyMatch(u -> u.subjectScopes().equals(List.of(Scope.ENTERPRISE))));
        assertTrue(p.units().stream().anyMatch(u -> u.subjectScopes().equals(List.of(Scope.CORE))));
        assertTrue(p.chunks().stream().filter(c -> c.embeddingText().contains("audit logs")).allMatch(c -> c.embeddingText().contains("ENTERPRISE")));
    }
    @Test void splitsLongProseAtNaturalBoundariesAndCoversEverySourceCharacter() {
        var fixture = fixture(INTRO + "## Features\n" + "Stores multilingual inventory records 技术 😀。 It also supports exports. ".repeat(140) + "\n");
        var p = preview(fixture);
        assertTrue(p.chunks().size() > 2);
        assertTrue(p.chunks().stream().allMatch(c -> c.tokens() <= 768));
        new ReadmeKnowledgeChunker().verify(fixture.source(), fixture.output().preview(), fixture.output().projection(), p);
    }
    @Test void repeatsSourceMappedTableHeadersWhenSplittingRows() {
        StringBuilder table = new StringBuilder(INTRO + "## Features\n| Option | Meaning |\n| --- | --- |\n");
        for (int i = 0; i < 90; i++) table.append("| option").append(i).append(" | stores inventory records and exports reports |\n");
        var p = preview(fixture(table.toString()));
        var pieces = p.chunks().stream().filter(c -> c.embeddingText().contains("| option")).toList();
        assertTrue(pieces.size() > 1);
        assertTrue(pieces.stream().allMatch(c -> c.embeddingText().contains("| Option | Meaning |") && c.tokens() <= 768));
        for (int i = 0; i < 90; i++) { String row = "| option" + i + " |"; assertTrue(pieces.stream().anyMatch(c -> c.embeddingText().contains(row))); }
    }
    @Test void defersOversizedCodeRatherThanCuttingAnExecutableExample() {
        var p = preview(fixture(INTRO + "## Usage\n```sh\n" + "echo inventory\n".repeat(600) + "```\n"));
        assertTrue(p.units().stream().anyMatch(u -> u.reasons().toString().contains("OVERSIZED_ATOMIC_BLOCK")));
        assertTrue(p.chunks().stream().noneMatch(c -> c.embeddingText().contains("echo inventory")));
    }
    @Test void evidenceBudgetRefusesPartialConditionsAndKeepsTokenCount() {
        var p = preview(fixture(INTRO + "## Features\n" + "Stores inventory records with audit metadata. ".repeat(180) + "\n"));
        var hit = p.chunks().getLast(); var evidence = new ReadmeKnowledgeChunker().evidence(p, hit.chunkId(), 10);
        assertEquals("DEFERRED_EVIDENCE_BUDGET", evidence.status()); assertTrue(evidence.sourceSpans().isEmpty());
        assertTrue(evidence.text().isEmpty()); assertTrue(evidence.tokens() > 10);
    }
    @Test void refusesGlobalChunkOverflowInsteadOfReturningPartialCorpus() {
        var fixture = fixture(INTRO + "## Features\nSupports export.\n\n## Deployment\nSupports Docker.\n");
        var chunker = new ReadmeKnowledgeChunker(new ReadmeKnowledgeChunker.Settings(512, 768, 1));
        assertThrows(IllegalArgumentException.class, () -> chunker.preview(fixture.source(), fixture.output().preview(), fixture.output().projection(),
                new ReadmeKnowledgeChunker.Identity(null, "TEST")));
    }
    @Test void sourceIdentityMismatchIsRejectedAndOutputIsDeterministic() {
        var fixture = fixture(INTRO + "## Features\nSupports exports.\n");
        assertEquals(preview(fixture), preview(fixture));
        Source changed = new Source("test", "v1", "owner/atlas", fixture.source().text() + "edited");
        assertThrows(IllegalArgumentException.class, () -> new ReadmeKnowledgeChunker().preview(changed,
                fixture.output().preview(), fixture.output().projection(), new ReadmeKnowledgeChunker.Identity(null, "TEST")));
    }
}
