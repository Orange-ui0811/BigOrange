package com.orangejd.server.ragb.ingest;

import java.util.*;
import java.util.regex.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;

/** Offline adapter from final K3 admission to source-bound knowledge units and embedding drafts.
 * No lifecycle hook, model, database or index access. Offsets refer to the unmodified source (UTF-16).
 */
public final class ReadmeKnowledgeChunker {
    public static final String POLICY = "README_KNOWLEDGE_M3_V2";
    public record Settings(int targetTokens, int maxTokens, int maxChunks) {
        public Settings {
            if (targetTokens < 64 || maxTokens < targetTokens || maxTokens > 768 || maxChunks < 1)
                throw new IllegalArgumentException("Invalid knowledge chunk budget");
        }
        public static Settings defaults() { return new Settings(512, 768, 4096); }
    }
    /** A business project ID must come from a verified mapping; documentId is never substituted. */
    public record Identity(String projectId, String generationId) {
        public Identity { if (generationId == null || generationId.isBlank()) throw new IllegalArgumentException("Generation required"); }
    }
    public record Span(String blockId, int startUtf16, int endUtf16, String text, String sha256,
                       String role, List<Scope> scopes, String blockKind) {}
    public record Unit(String unitId, List<String> primaryBlockIds, List<String> requiredBlockIds,
                       List<Scope> subjectScopes, List<Kind> knowledgeTypes, List<String> sectionPath,
                       String status, List<String> reasons, List<Span> sourceSpans, List<String> chunkIds) {}
    public record Chunk(String chunkId, String unitId, int index, String embeddingText, int tokens,
                        List<Span> sourceSpans, List<String> requiredBlockIds) {}
    public record Preview(String policyVersion, Identity identity, String documentId, String versionId,
                          String sourceRef, String sourceSha256, Versions filterVersions, Settings settings,
                          List<Unit> units, List<Chunk> chunks, List<Pointer> navigation,
                          List<String> reviewBlockIds, List<String> qualityFlags) {}
    public record Evidence(String status, String unitId, List<Span> sourceSpans, String text, int tokens) {}
    private record Segment(int a, int b) {}
    private static final Pattern SENTENCE = Pattern.compile("[。！？](?:[\\s]+|(?=[^\\x00-\\x7f]))|[.!?](?:[\\s]+)|\\r?\\n");
    private final RagBTokenChunker tokenizer = new RagBTokenChunker();
    private final Settings settings;
    public ReadmeKnowledgeChunker() { this(Settings.defaults()); }
    public ReadmeKnowledgeChunker(Settings settings) { this.settings = Objects.requireNonNull(settings); }

    public Preview preview(Source source, ReadmeKnowledgeFilter.Preview selected, Projection projection, Identity identity) {
        ReadmeRecommendationProjection.verify(source, selected, projection);
        Map<String, Block> blocks = new LinkedHashMap<>(); selected.blocks().forEach(b -> blocks.put(b.blockId(), b));
        Map<String, Assessment> admitted = new LinkedHashMap<>(); projection.assessments().forEach(a -> admitted.put(a.blockId(), a));
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        admitted.forEach((id, a) -> edges.put(id, new LinkedHashSet<>(a.dependencyBlockIds())));
        bindProceduralChildren(selected.blocks(), edges);
        Map<String, Unit> units = new LinkedHashMap<>(); List<Chunk> chunks = new ArrayList<>();
        for (Candidate candidate : projection.knowledgeCandidates()) {
            Packet packet = candidate.packet();
            for (String seed : packet.primaryBlockIds()) {
                LinkedHashSet<String> refs = new LinkedHashSet<>(List.of(seed));
                refs.addAll(packet.contextBlockIds());
                ArrayDeque<String> pending = new ArrayDeque<>(refs);
                while (!pending.isEmpty()) {
                    String id = pending.removeFirst();
                    if (!edges.containsKey(id)) throw new IllegalArgumentException("Unknown dependency");
                    for (String dep : edges.get(id)) if (refs.add(dep)) pending.addLast(dep);
                }
                List<String> ordered = refs.stream().sorted(Comparator.comparingInt(id -> blocks.get(id).startUtf16())).toList();
                List<String> primary = ordered.stream().filter(packet.primaryBlockIds()::contains).toList();
                List<String> required = ordered.stream().filter(id -> !primary.contains(id)).toList();
                String unitId = sha256(POLICY + "\0" + identity.generationId() + "\0" + source.documentId() + "\0"
                        + source.versionId() + "\0" + selected.sourceSha256() + "\0" + candidate.subjectScopes() + "\0" + ordered);
                if (units.containsKey(unitId)) continue;
                List<String> missing = ordered.stream().filter(id -> !allowed(blocks.get(id), admitted.get(id))).toList();
                List<Span> spans = ordered.stream().filter(id -> allowed(blocks.get(id), admitted.get(id)))
                        .map(id -> span(blocks.get(id), admitted.get(id), primary.contains(id))).toList();
                List<Kind> types = primary.stream().map(id -> admitted.get(id).kind()).distinct().toList();
                List<String> section = blocks.get(seed).sectionPath();
                List<String> reasons = new ArrayList<>();
                if (!missing.isEmpty()) reasons.add("REQUIRED_BLOCK_NOT_ADMITTED:" + String.join(",", missing));
                List<Chunk> drafts = List.of();
                if (reasons.isEmpty()) {
                    try { drafts = split(unitId, source.sourceRef(), section, candidate.subjectScopes(), types, spans, required); }
                    catch (Deferred ex) { reasons.add(ex.getMessage()); }
                }
                if (chunks.size() + drafts.size() > settings.maxChunks())
                    throw new IllegalArgumentException("Chunk limit exceeded; no partial preview returned");
                chunks.addAll(drafts);
                units.put(unitId, new Unit(unitId, primary, required, candidate.subjectScopes(), types, section,
                        reasons.isEmpty() ? "READY" : "DEFERRED", List.copyOf(reasons), spans,
                        drafts.stream().map(Chunk::chunkId).toList()));
            }
        }
        Preview result = new Preview(POLICY, identity, source.documentId(), source.versionId(), source.sourceRef(), selected.sourceSha256(),
                projection.versions(), settings, List.copyOf(units.values()), List.copyOf(chunks), projection.pointers(),
                projection.reviewBlockIds(), selected.qualityFlags());
        verify(source, selected, projection, result);
        return result;
    }

    private boolean allowed(Block b, Assessment a) {
        return b != null && a != null && b.decision() == Decision.KEEP && a.effectiveDecision() == Decision.KEEP
                && a.kind() != Kind.SOURCE_POINTER && (a.disposition() == Disposition.CONTEXT_ONLY
                || a.disposition() == Disposition.KNOWLEDGE_CANDIDATE && a.applicability() == Applicability.IN_SCOPE && !a.scopes().isEmpty());
    }
    private Span span(Block b, Assessment a, boolean primary) {
        return new Span(b.blockId(), b.startUtf16(), b.endUtf16(), b.text(), b.sha256(),
                b.kind().equals("HEADING") ? "HEADING" : primary ? "PRIMARY" : "DEPENDENCY", a.scopes(), b.kind());
    }
    /** Additional unit boundary: a list-step lead-in and its indented instructions are inseparable.
     * Inspect source structure, including non-admitted children, but never promote those children.
     */
    private void bindProceduralChildren(List<Block> blocks, Map<String, Set<String>> edges) {
        for (int i = 0; i < blocks.size(); i++) {
            Block lead = blocks.get(i);
            if (!Set.of("PROSE", "INDENTED").contains(lead.kind()) || ReadmeSourceSemantics.codeLike(lead)
                    || !lead.text().stripTrailing().matches("(?s).*[:：](?:\\*\\*)?$")) continue;
            int indent = indentation(lead.text());
            for (int j = i + 1; j < blocks.size(); j++) {
                Block child = blocks.get(j);
                if (child.kind().equals("SPACE")) continue;
                if (ReadmeSourceSemantics.layoutOnly(child)) continue;
                // A collapsible sub-branch has its own steps. Preserve existing K3 prerequisites,
                // but do not turn all troubleshooting instructions into prerequisites of a preceding command.
                if (Pattern.compile("(?i)<details\\b").matcher(child.text()).find()) break;
                if (!child.sectionPath().equals(lead.sectionPath()) || child.kind().equals("HEADING")
                        || indentation(child.text()) <= indent) break;
                edges.get(lead.blockId()).add(child.blockId()); edges.get(child.blockId()).add(lead.blockId());
            }
        }
    }
    private int indentation(String text) {
        int n = 0; while (n < text.length() && (text.charAt(n) == ' ' || text.charAt(n) == '\t')) n++;
        return n;
    }
    private String prefix(String repo, List<String> section, List<Scope> scopes, List<Kind> kinds) {
        return "Repository: " + repo + "\nSection: " + String.join(" > ", section)
                + "\nKnowledge: " + kinds + "\nSubject scopes: " + scopes + "\n\n";
    }
    private String body(List<Span> spans) {
        StringBuilder text = new StringBuilder(); Span previous = null;
        for (Span span : spans) {
            if (previous != null && !(previous.blockId().equals(span.blockId())
                    && (previous.endUtf16() == span.startUtf16() || previous.text().endsWith("\n")))) text.append('\n');
            text.append(span.text()); previous = span;
        }
        return text.toString();
    }
    private List<Chunk> split(String unitId, String repo, List<String> section, List<Scope> scopes, List<Kind> types,
                              List<Span> spans, List<String> required) {
        String prefix = prefix(repo, section, scopes, types);
        if (tokenizer.tokens(prefix) >= settings.maxTokens()) throw new Deferred("METADATA_EXCEEDS_TOKEN_BUDGET");
        if (tokenizer.tokens(prefix + body(spans)) <= settings.maxTokens())
            return List.of(chunk(unitId, 0, prefix, spans, required));
        List<List<Span>> atoms = new ArrayList<>();
        for (Span span : spans) {
            if (tokenizer.tokens(prefix + span.text()) <= settings.maxTokens()) atoms.add(List.of(span));
            else if (table(span.text())) atoms.addAll(tableParts(prefix, span));
            else if (code(span.text()) || Set.of("CODE", "INDENTED", "HEADING").contains(span.blockKind()))
                throw new Deferred("OVERSIZED_ATOMIC_BLOCK:" + span.blockId());
            else atoms.addAll(proseParts(prefix, span));
        }
        List<Chunk> result = new ArrayList<>(); List<Span> current = new ArrayList<>();
        for (List<Span> atom : atoms) {
            List<Span> joined = new ArrayList<>(current); joined.addAll(atom);
            if (!current.isEmpty() && (tokenizer.tokens(prefix + body(joined)) > settings.maxTokens()
                    || tokenizer.tokens(prefix + body(current)) >= settings.targetTokens())) {
                result.add(chunk(unitId, result.size(), prefix, current, required)); current = new ArrayList<>();
            }
            current.addAll(atom);
        }
        if (!current.isEmpty()) result.add(chunk(unitId, result.size(), prefix, current, required));
        return List.copyOf(result);
    }
    private boolean code(String text) { return Pattern.compile("(?m)^\\s*(?:`{3,}|~{3,})").matcher(text).find(); }
    private boolean table(String text) {
        String[] lines = text.split("\\r?\\n");
        return lines.length >= 2 && lines[0].contains("|") && lines[1].matches("[ |:\\-\\t]+");
    }
    private Span slice(Span span, int a, int b) {
        String raw = span.text().substring(a, b);
        return new Span(span.blockId(), span.startUtf16() + a, span.startUtf16() + b, raw, sha256(raw), span.role(), span.scopes(), span.blockKind());
    }
    private List<List<Span>> tableParts(String prefix, Span span) {
        List<Segment> lines = lines(span.text());
        Span header = slice(span, 0, lines.get(1).b()); List<List<Span>> result = new ArrayList<>();
        int first = 2, last = 2;
        for (int i = 2; i < lines.size(); i++) {
            Segment row = lines.get(i);
            if (tokenizer.tokens(prefix + body(List.of(header, slice(span, row.a(), row.b())))) > settings.maxTokens())
                throw new Deferred("OVERSIZED_TABLE_ROW:" + span.blockId());
            List<Span> group = List.of(header, slice(span, lines.get(first).a(), row.b()));
            if (i > first && tokenizer.tokens(prefix + body(group)) > settings.targetTokens()) {
                result.add(List.of(header, slice(span, lines.get(first).a(), lines.get(last).b()))); first = i;
            }
            last = i;
        }
        if (lines.size() > 2) result.add(List.of(header, slice(span, lines.get(first).a(), lines.get(last).b())));
        if (result.isEmpty()) throw new Deferred("OVERSIZED_TABLE_HEADER:" + span.blockId());
        return result;
    }
    private List<Segment> lines(String raw) {
        List<Segment> result = new ArrayList<>();
        for (int a = 0; a < raw.length();) { int b = raw.indexOf('\n', a); b = b < 0 ? raw.length() : b + 1; result.add(new Segment(a, b)); a = b; }
        return result;
    }
    private List<List<Span>> proseParts(String prefix, Span span) {
        String raw = span.text(); TreeSet<Integer> ends = new TreeSet<>(); Matcher matcher = SENTENCE.matcher(raw);
        while (matcher.find()) ends.add(matcher.end()); ends.add(raw.length());
        List<List<Span>> result = new ArrayList<>();
        for (int at = 0; at < raw.length();) {
            int best = -1;
            for (int end : ends.tailSet(at, false)) {
                if (tokenizer.tokens(prefix + raw.substring(at, end)) > settings.maxTokens()) continue;
                best = end;
                if (tokenizer.tokens(prefix + raw.substring(at, end)) >= settings.targetTokens()) break;
            }
            if (best <= at) throw new Deferred("OVERSIZED_UNBROKEN_PROSE:" + span.blockId());
            result.add(List.of(slice(span, at, best))); at = best;
        }
        return result;
    }
    private Chunk chunk(String unitId, int index, String prefix, List<Span> spans, List<String> required) {
        String text = prefix + body(spans); int tokens = tokenizer.tokens(text);
        if (tokens > settings.maxTokens()) throw new Deferred("FINAL_TEXT_EXCEEDS_TOKEN_BUDGET");
        return new Chunk(sha256(unitId + "\0" + index + "\0" + text), unitId, index, text, tokens, List.copyOf(spans), required);
    }
    /** Retrieve the full approved unit, never arbitrary neighboring raw README blocks.
     * If the whole evidence exceeds the caller budget, return no partial evidence.
     */
    public Evidence evidence(Preview preview, String chunkId, int maxTokens) {
        if (maxTokens < 1) throw new IllegalArgumentException("Evidence budget required");
        Chunk chunk = preview.chunks().stream().filter(c -> c.chunkId().equals(chunkId)).findFirst().orElseThrow();
        Unit unit = preview.units().stream().filter(u -> u.unitId().equals(chunk.unitId())).findFirst().orElseThrow();
        if (!unit.status().equals("READY")) throw new IllegalArgumentException("Unready evidence unit");
        String text = body(unit.sourceSpans()); int count = tokenizer.tokens(text);
        return count > maxTokens ? new Evidence("DEFERRED_EVIDENCE_BUDGET", unit.unitId(), List.of(), "", count)
                : new Evidence("READY", unit.unitId(), unit.sourceSpans(), text, count);
    }
    public void verify(Source source, ReadmeKnowledgeFilter.Preview selected, Projection projection, Preview preview) {
        ReadmeRecommendationProjection.verify(source, selected, projection);
        if (!preview.sourceSha256().equals(sha256(source.text())) || !preview.documentId().equals(source.documentId())
                || !preview.versionId().equals(source.versionId()) || !preview.sourceRef().equals(source.sourceRef()))
            throw new IllegalArgumentException("Chunk source identity mismatch");
        Map<String, Block> blocks = new HashMap<>(); selected.blocks().forEach(b -> blocks.put(b.blockId(), b));
        Map<String, Assessment> assessments = new HashMap<>(); projection.assessments().forEach(a -> assessments.put(a.blockId(), a));
        Map<String, Unit> units = new HashMap<>(); preview.units().forEach(u -> units.put(u.unitId(), u));
        if (!POLICY.equals(preview.policyVersion()) || !settings.equals(preview.settings()) || units.size() != preview.units().size()
                || !projection.versions().equals(preview.filterVersions()) || !projection.pointers().equals(preview.navigation()))
            throw new IllegalArgumentException("Chunk policy or metadata mismatch");
        Set<String> chunks = new HashSet<>();
        for (Unit unit : preview.units()) {
            for (Span span : unit.sourceSpans()) {
                verifySpan(source, blocks, assessments, span);
                if (!span.text().equals(blocks.get(span.blockId()).text())) throw new IllegalArgumentException("Partial unit source");
            }
            if (unit.status().equals("DEFERRED") && !unit.chunkIds().isEmpty()) throw new IllegalArgumentException("Deferred unit leak");
            if (unit.status().equals("READY")) {
                Set<String> expected = new HashSet<>(unit.primaryBlockIds()); expected.addAll(unit.requiredBlockIds());
                if (unit.primaryBlockIds().isEmpty() || unit.chunkIds().isEmpty() || !unit.reasons().isEmpty()
                        || !expected.equals(new HashSet<>(unit.sourceSpans().stream().map(Span::blockId).toList())))
                    throw new IllegalArgumentException("Incomplete knowledge unit");
            }
            for (String id : unit.primaryBlockIds()) if (assessments.get(id).disposition() != Disposition.KNOWLEDGE_CANDIDATE
                    || !assessments.get(id).scopes().equals(unit.subjectScopes())) throw new IllegalArgumentException("Mixed primary scopes");
        }
        for (Chunk chunk : preview.chunks()) {
            Unit unit = units.get(chunk.unitId());
            if (!chunks.add(chunk.chunkId()) || unit == null || !unit.status().equals("READY") || !unit.chunkIds().contains(chunk.chunkId())
                    || !unit.requiredBlockIds().equals(chunk.requiredBlockIds()))
                throw new IllegalArgumentException("Invalid chunk membership");
            for (Span span : chunk.sourceSpans()) {
                verifySpan(source, blocks, assessments, span);
                if (unit.sourceSpans().stream().noneMatch(parent -> parent.blockId().equals(span.blockId())
                        && parent.startUtf16() <= span.startUtf16() && parent.endUtf16() >= span.endUtf16()))
                    throw new IllegalArgumentException("Chunk outside approved unit");
            }
            String expected = prefix(source.sourceRef(), unit.sectionPath(), unit.subjectScopes(), unit.knowledgeTypes()) + body(chunk.sourceSpans());
            if (!expected.equals(chunk.embeddingText()) || chunk.tokens() != tokenizer.tokens(expected) || chunk.tokens() > preview.settings().maxTokens())
                throw new IllegalArgumentException("Chunk text or budget mismatch");
        }
        for (Unit unit : preview.units()) if (!chunks.containsAll(unit.chunkIds())) throw new IllegalArgumentException("Missing chunk");
        for (Unit unit : preview.units()) if (unit.status().equals("READY")) for (Span whole : unit.sourceSpans()) {
            int covered = whole.startUtf16();
            List<Span> pieces = preview.chunks().stream().filter(c -> c.unitId().equals(unit.unitId()))
                    .flatMap(c -> c.sourceSpans().stream()).filter(s -> s.blockId().equals(whole.blockId()))
                    .sorted(Comparator.comparingInt(Span::startUtf16)).toList();
            for (Span piece : pieces) {
                if (piece.startUtf16() > covered) throw new IllegalArgumentException("Knowledge coverage gap");
                covered = Math.max(covered, piece.endUtf16());
            }
            if (covered != whole.endUtf16()) throw new IllegalArgumentException("Knowledge coverage incomplete");
        }
    }
    private void verifySpan(Source source, Map<String, Block> blocks, Map<String, Assessment> assessments, Span span) {
        Block block = blocks.get(span.blockId()); Assessment a = assessments.get(span.blockId());
        if (!allowed(block, a) || span.startUtf16() < block.startUtf16() || span.endUtf16() > block.endUtf16()
                || span.endUtf16() <= span.startUtf16() || !source.text().substring(span.startUtf16(), span.endUtf16()).equals(span.text())
                || !sha256(span.text()).equals(span.sha256()) || !a.scopes().equals(span.scopes()) || !block.kind().equals(span.blockKind())
                || span.startUtf16() > 0 && Character.isLowSurrogate(source.text().charAt(span.startUtf16()))
                || span.endUtf16() < source.text().length() && Character.isLowSurrogate(source.text().charAt(span.endUtf16())))
            throw new IllegalArgumentException("Chunk source span or admission mismatch");
    }
    private static final class Deferred extends RuntimeException { Deferred(String reason) { super(reason); } }
}
