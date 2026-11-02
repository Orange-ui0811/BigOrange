package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads frozen local files only. It deliberately has no application bootstrap or provider client. */
public final class ReadmeFilterOfflineCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("snapshot.json manifest.json output-directory development|validation|all");
        System.out.println(JSON.writeValueAsString(run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args[3])));
    }
    public static Map<String, Object> run(Path snapshotFile, Path manifestFile, Path output, String split) throws Exception {
        if (!Set.of("development", "validation", "all").contains(split)) throw new IllegalArgumentException("Invalid split");
        String rawSnapshot = Files.readString(snapshotFile, StandardCharsets.UTF_8);
        JsonNode snapshot = JSON.readTree(rawSnapshot), manifest = JSON.readTree(Files.readString(manifestFile));
        if (!ReadmeKnowledgeFilter.sha256(rawSnapshot).equals(manifest.path("snapshotSha256").asText()))
            throw new IllegalArgumentException("Frozen snapshot hash mismatch");
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        snapshot.path("documents").forEach(d -> {
            if (documents.put(d.path("documentId").asText(), d) != null) throw new IllegalArgumentException("Duplicate document");
        });
        ReadmeKnowledgeFilter filter = new ReadmeKnowledgeFilter();
        List<ReadmeKnowledgeFilter.Preview> previews = new ArrayList<>();
        Set<String> repositories = new HashSet<>();
        for (JsonNode sample : manifest.path("samples")) {
            if (!split.equals("all") && !split.equals(sample.path("split").asText())) continue;
            JsonNode doc = documents.get(sample.path("documentId").asText());
            if (doc == null || !doc.path("versionId").equals(sample.path("versionId"))
                    || !doc.path("sourceRef").equals(sample.path("sourceRef"))
                    || !ReadmeKnowledgeFilter.sha256(doc.path("content").asText()).equals(sample.path("contentSha256").asText()))
                throw new IllegalArgumentException("Sample source mismatch");
            if (!repositories.add(doc.path("sourceRef").asText().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate repository");
            previews.add(filter.preview(new ReadmeKnowledgeFilter.Source(doc.path("documentId").asText(), doc.path("versionId").asText(),
                    doc.path("sourceRef").asText(), doc.path("content").asText())));
        }
        if (previews.isEmpty()) throw new IllegalArgumentException("No selected documents");
        Files.createDirectories(output);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyVersion", ReadmeKnowledgeFilter.POLICY);
        summary.put("parserVersion", ReadmeKnowledgeFilter.PARSER);
        summary.put("split", split); summary.put("documents", previews.size());
        summary.put("snapshotSha256", manifest.path("snapshotSha256").asText());
        summary.put("sourceMappingVerified", true);
        summary.put("networkAccess", false); summary.put("indexWrites", 0);
        summary.put("blocks", previews.stream().mapToInt(p -> p.blocks().size()).sum());
        summary.put("reviewBlocks", previews.stream().mapToInt(p -> p.reviewBlockIds().size()).sum());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReadmeKnowledgeFilter.Decision d : ReadmeKnowledgeFilter.Decision.values())
            counts.put(d.name(), previews.stream().flatMap(p -> p.blocks().stream()).filter(b -> b.decision() == d).count());
        summary.put("decisionCounts", counts);
        summary.put("knowledgePackets", previews.stream().mapToInt(p -> p.knowledgeCandidates().size()).sum());
        summary.put("pointerPackets", previews.stream().mapToInt(p -> p.pointerCandidates().size()).sum());
        write(output.resolve(split + "-preview.json"), Map.of("summary", summary, "previews", previews));
        write(output.resolve(split + "-summary.json"), summary);
        StringBuilder report = new StringBuilder("# K2 离线预览 · " + split + "\n\n"
                + "仅规则预测，尚未经模型/人工复核；原文块不等于最终 Token 检索块。未执行索引写入。\n\n");
        for (var preview : previews) {
            report.append("## ").append(preview.documentId()).append(" · ").append(preview.sourceRef()).append("\n\n");
            report.append("仓库类型提示：").append(preview.repoRoleHint()).append("；不是已确认业务属性。\n\n");
            for (var block : preview.blocks()) {
                if (block.kind().equals("SPACE")) continue;
                report.append("### ").append(block.decision()).append(" · L").append(block.startLine()).append("–")
                        .append(block.endLine()).append(" · ").append(block.kind()).append("\n\n")
                        .append(block.reason()).append(" / ").append(block.ruleIds()).append("\n\n")
                        .append("UTF-16 `[").append(block.startUtf16()).append(", ").append(block.endUtf16()).append(")`；原文块 `")
                        .append(block.blockId()).append("`。\n\n");
                // The fence is longer than any tilde run in the source, so README markup remains inert source text.
                int run = 0, max = 7;
                for (char c : block.text().toCharArray()) { run = c == '~' ? run + 1 : 0; max = Math.max(max, run); }
                String fence = "~".repeat(max + 1);
                report.append(fence).append("text\n").append(block.text());
                if (!block.text().endsWith("\n")) report.append('\n');
                report.append(fence).append("\n\n");
            }
        }
        Files.writeString(output.resolve(split + "-preview.md"), report, StandardCharsets.UTF_8);
        return summary;
    }
    private static void write(Path target, Object value) throws Exception {
        Files.writeString(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n", StandardCharsets.UTF_8);
    }
}
