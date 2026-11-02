package com.orangejd.server.ragb.ingest;

import static com.orangejd.server.ragb.ingest.ReadmeMarkdownParser.*;
import static com.orangejd.server.ragb.ingest.ReadmeDependencyGraph.isLeadIn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/** Offline, conservative source selection. No Spring lifecycle, I/O, model or index side effects. */
public final class ReadmeKnowledgeFilter {
    public static final String POLICY = "README_RULE_PREVIEW_V1_6";
    public static final String PARSER = "CONTIGUOUS_MARKDOWN_BLOCKS_V1";
    public enum Decision { KEEP, DROP, REVIEW }
    public record Source(String documentId, String versionId, String sourceRef, String text) {
        public Source {
            Objects.requireNonNull(documentId); Objects.requireNonNull(versionId);
            Objects.requireNonNull(sourceRef); Objects.requireNonNull(text);
        }
    }
    public record Block(String blockId, String kind, int startUtf16, int endUtf16,
                        int startLine, int endLine, String text, String sha256,
                        List<String> sectionPath, List<String> headingIds, Decision decision,
                        String reason, List<String> ruleIds, List<String> knowledgeTypeHints) {}
    public record Packet(String packetId, String documentId, String versionId, String sourceRef,
                         String sourceSha256, List<String> primaryBlockIds, List<String> contextBlockIds,
                         String disposition) {}
    public record Preview(String policyVersion, String parserVersion, String documentId, String versionId,
                          String sourceRef, String sourceSha256, String repoRoleHint, List<Block> blocks,
                          List<Packet> knowledgeCandidates, List<Packet> pointerCandidates,
                          List<String> reviewBlockIds, List<String> qualityFlags) {}
    private record Verdict(Decision decision, String reason, String rule, String hint) {}
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();
    private static Pattern rx(String regex) { return PATTERNS.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)); }
    static boolean has(String regex, String value) { return rx(regex).matcher(value).find(); }
    static final Pattern FENCE = rx("^ {0,3}(`{3,}|~{3,})(.*)$");
    static final Pattern HEADING = rx("^ {0,3}(#{1,6})\\s+(.+)$");
    private static final String LICENSE = "licen[cs]e|copyright|\\b(?:SPDX|AGPL|GPL|MIT|BSD)\\b|许可证|许可协议|版权|商标|royalt";
    private static final String LIMIT = "\\b(?:warning|limitations?|caveats?|unsupported|incompatible|not recommended|not supported|no auth|no cloud|no RFID|only|must|requires?|recommended|prerequisites?)\\b|不支持|不兼容|并非|不能|无法|不适合|非生产|警告|注意|仅支持|仅限|必须|强烈建议|请谨慎|无认证|零依赖";
    private static final String MAINTENANCE = "maintainers?.{0,20}wanted|急需维护者|开发已迁移|项目已迁移|已归档|development.{0,30}(?:moved|ended|stopped)|\\b(?:deprecated|archived)\\b|developer preview|breaking changes|不稳定|维护至|归档";
    private static final String POINTER = "\\b(?:docs?|documentation|API|tutorial|guide|wiki|DOI|OpenSSF|colab|runwayml|demo|examples?|contribut|development|books)\\b|文档|指南|教程|示例|演示|书单|入门|案例展示|支持.{0,12}硬件|开发板|构建说明";
    private static final String NOISE_SECTION = "^(?:[\\p{Punct}\\s\\p{So}]*)(?:sponsors?|backers|donations?|star history|translations?|赞助|赞助者|捐赠|翻译|谁在使用|(?:项目)?(?:微信)?交流群|QQ群|微信群|community chat|social links)(?:[\\s\\p{Punct}].*)?$";
    private static final String NOISE = "github/(?:stars|forks|downloads|sponsors)|shields\\.io/(?:discord|jsdelivr|pepy|badge/(?:Telegram|Twitter|Discord))|pepy/dt|pypistats|badge\\.svg.*actions|actions/[^\\s]+badge|trendshift|star-history|buymeacoffee|github\\.com/sponsors|请.{0,8}(?:star|咖啡|啤酒)|give.{0,12}star|consider.{0,25}star";
    private static final String KNOWLEDGE_SECTION = "feature|overview|introduction|architecture|installation|setup|configuration|requirements?|quick start|usage|example|build|test|deployment|monorepo|功能|特性|概述|介绍|架构|安装|部署|配置|环境|依赖|使用|用法|示例|构建|测试|版本说明|软硬件|迁移|快速开始|快速入门";

    public Preview preview(Source source) {
        String text = source.text();
        List<Line> lines = lines(text);
        List<Draft> drafts = parse(lines);
        String sourceHash = sha256(text);
        String identity = sha256(source.documentId() + "\0" + source.versionId() + "\0" + source.sourceRef() + "\0" + sourceHash);
        List<Block> blocks = new ArrayList<>();
        String[] titles = new String[6], headingIds = new String[6];
        for (Draft draft : drafts) {
            Line first = lines.get(draft.from()), last = lines.get(draft.to() - 1);
            String raw = text.substring(first.start(), last.end());
            String id = identity + ":" + first.start() + "-" + last.end();
            if (draft.kind().equals("HEADING")) {
                Matcher m = HEADING.matcher(first.value());
                int level = m.matches() ? m.group(1).length() : (lines.get(draft.to() - 1).value().startsWith("=") ? 1 : 2);
                String title = m.matches() ? m.group(2) : first.value().strip();
                titles[level - 1] = title; headingIds[level - 1] = id;
                for (int k = level; k < 6; k++) { titles[k] = null; headingIds[k] = null; }
            }
            List<String> path = Arrays.stream(titles).filter(Objects::nonNull).toList();
            List<String> context = Arrays.stream(headingIds).filter(Objects::nonNull).filter(x -> !x.equals(id)).toList();
            Verdict v = decide(draft, raw, path, text.strip().equals(raw.strip()));
            if (draft.kind().equals("PROSE") && ReadmeSourceSemantics.incompleteTail(raw,text.substring(last.end()).isBlank()))
                v=verdict(Decision.REVIEW,"POSSIBLY_TRUNCATED_TEXT","R10","INCOMPLETE");
            blocks.add(new Block(id, draft.kind(), first.start(), last.end(), first.number(), last.number(),
                    raw, sha256(raw), path, context, v.decision(), v.reason(), List.of(v.rule()), List.of(v.hint())));
        }
        return assemble(source, blocks);
    }

    /** Rebuild source packets after reviewed decisions; fixed K2 decisions and original text are immutable. */
    public Preview reassemble(Source source, Preview original, List<Block> reviewed) {
        verify(source, original);
        if (reviewed.size() != original.blocks().size()) throw new IllegalArgumentException("Review block count mismatch");
        for (int i = 0; i < reviewed.size(); i++) {
            Block a = original.blocks().get(i), b = reviewed.get(i);
            if (!a.blockId().equals(b.blockId()) || a.startUtf16()!=b.startUtf16() || a.endUtf16()!=b.endUtf16()
                    || a.startLine()!=b.startLine() || a.endLine()!=b.endLine() || !a.text().equals(b.text())
                    || !a.sha256().equals(b.sha256()) || !a.kind().equals(b.kind()) || !a.sectionPath().equals(b.sectionPath())
                    || !a.headingIds().equals(b.headingIds()) || a.decision()!=Decision.REVIEW && !a.equals(b))
                throw new IllegalArgumentException("Model attempted to alter source or fixed rule decision");
        }
        return assemble(source, new ArrayList<>(reviewed));
    }

    private Preview assemble(Source source, List<Block> blocks) {
        String text = source.text(), sourceHash = sha256(text);
        String identity = sha256(source.documentId() + "\0" + source.versionId() + "\0" + source.sourceRef() + "\0" + sourceHash);
        // Quarantine a lead-in to a broken example, rather than approve an unsupported usage fragment.
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (!b.kind().equals("CODE") || !b.reason().equals("INCOMPLETE_STRUCTURE")) continue;
            for (int j = i - 1; j >= 0; j--) {
                Block before = blocks.get(j);
                if (before.kind().equals("SPACE")) continue;
                if (before.kind().equals("PROSE") && before.sectionPath().equals(b.sectionPath())
                        && isLeadIn(before)) blocks.set(j, withReview(before, "INCOMPLETE_EXAMPLE_CONTEXT"));
                break;
            }
        }
        for(var span:ReadmeSourceSemantics.indentedSpans(blocks))if(!span.complete())
            for(Block member:span.blocks()){
                int at=blocks.indexOf(member);blocks.set(at,withReview(member,"INCOMPLETE_STRUCTURE"));
            }
        Set<String> codeFragments=new HashSet<>();
        ReadmeSourceSemantics.indentedSpans(blocks).forEach(s->s.blocks().forEach(b->codeFragments.add(b.blockId())));
        for(int i=0;i<blocks.size();i++){
            Block b=blocks.get(i);
            if(ReadmeSourceSemantics.layoutOnly(b)&&!codeFragments.contains(b.blockId()))blocks.set(i,new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),
                    b.startLine(),b.endLine(),b.text(),b.sha256(),b.sectionPath(),b.headingIds(),Decision.DROP,"PURE_LAYOUT_ONLY",List.of("R11"),List.of("FORMAT")));
        }
        Map<String, List<String>> dependencies = dependencies(blocks);
        boolean changed;
        do {
            changed = false;
            Map<String, Block> current = byId(blocks);
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                if (b.decision() == Decision.KEEP && dependencies.get(b.blockId()).stream()
                        .anyMatch(id -> current.get(id).decision() != Decision.KEEP)) {
                    blocks.set(i, withReview(b, "DEPENDENCY_NOT_APPROVED")); changed = true;
                }
            }
        } while (changed);
        Map<String, Block> all = byId(blocks);
        List<String> guards = blocks.stream().filter(b -> b.decision() == Decision.KEEP && ReadmeSourceSemantics.condition(b))
                .map(Block::blockId).toList();
        // Group whole sections, so paired commands and table/edition context travel together.
        Map<List<String>, List<Block>> sections = new LinkedHashMap<>();
        for (Block b : blocks) if (b.decision() == Decision.KEEP && !b.kind().equals("HEADING"))
            sections.computeIfAbsent(b.headingIds(), ignored -> new ArrayList<>()).add(b);
        List<Packet> knowledge = new ArrayList<>(), pointers = new ArrayList<>();
        List<String> quality = new ArrayList<>();
        for (List<Block> section : sections.values()) {
            List<String> primary = section.stream().filter(b -> !b.knowledgeTypeHints().contains("SOURCE_POINTER")).map(Block::blockId).toList();
            List<String> links = section.stream().filter(b -> b.knowledgeTypeHints().contains("SOURCE_POINTER")).map(Block::blockId).toList();
            for (boolean pointer : List.of(false, true)) {
                List<String> chosen = pointer ? links : primary;
                if (chosen.isEmpty()) continue;
                LinkedHashSet<String> refs = new LinkedHashSet<>();
                if(!pointer)for(String guard:guards)if(chosen.stream().anyMatch(id->ReadmeSourceSemantics.applies(all.get(guard),all.get(id)))) {
                    refs.add(guard);addDependencies(guard,dependencies,refs);
                }
                for (String id : chosen) addDependencies(id, dependencies, refs);
                refs.removeAll(chosen);
                long size = chosen.stream().mapToLong(id -> all.get(id).text().length()).sum()
                        + refs.stream().mapToLong(id -> all.get(id).text().length()).sum();
                if (size > 64000) { quality.add("OVERSIZED_SECTION_REQUIRES_BUDGETED_CHUNKING:" + chosen.getFirst()); continue; }
                Packet packet = new Packet(identity + ":packet:" + (pointer ? "pointer:" : "knowledge:") + chosen.getFirst(),
                        source.documentId(), source.versionId(), source.sourceRef(), sourceHash, chosen, List.copyOf(refs),
                        pointer ? "POINTER_METADATA_ONLY" : "OFFLINE_KNOWLEDGE_CANDIDATE");
                (pointer ? pointers : knowledge).add(packet);
            }
        }
        List<String> review = blocks.stream().filter(b -> b.decision() == Decision.REVIEW).map(Block::blockId).toList();
        if (!review.isEmpty()) quality.add("HAS_REVIEW_BLOCKS");
        if (knowledge.isEmpty()) quality.add("NO_APPROVED_BODY_PACKET");
        quality.add("SOURCE_CLAIMS_NOT_RUNTIME_VERIFIED");
        Preview result = new Preview(POLICY, PARSER, source.documentId(), source.versionId(), source.sourceRef(), sourceHash,
                roleHint(text), List.copyOf(blocks), List.copyOf(knowledge), List.copyOf(pointers), review, List.copyOf(quality));
        verify(source, result);
        return result;
    }

    /** Shared by packet assembly and model planning. Decisions cannot erase an original dependency. */
    static Map<String,List<String>> dependencies(List<Block> blocks) { return ReadmeDependencyGraph.dependencies(blocks); }
    static void addDependencies(String id, Map<String, List<String>> dependencies, Set<String> refs) {
        ArrayDeque<String> pending = new ArrayDeque<>(dependencies.get(id));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (visited.add(next)) { refs.add(next); pending.addAll(dependencies.get(next)); }
        }
    }
    private static Block withReview(Block b, String reason) {
        return new Block(b.blockId(), b.kind(), b.startUtf16(), b.endUtf16(), b.startLine(), b.endLine(), b.text(), b.sha256(),
                b.sectionPath(), b.headingIds(), Decision.REVIEW, reason, List.of("R10"), b.knowledgeTypeHints());
    }
    static Map<String, Block> byId(List<Block> blocks) {
        Map<String, Block> result = new LinkedHashMap<>();
        for (Block b : blocks) if (result.put(b.blockId(), b) != null) throw new IllegalArgumentException("Duplicate block ID");
        return result;
    }
    public static void verify(Source source, Preview result) {
        if (!source.documentId().equals(result.documentId()) || !source.versionId().equals(result.versionId())
                || !source.sourceRef().equals(result.sourceRef()) || !sha256(source.text()).equals(result.sourceSha256()))
            throw new IllegalArgumentException("Source identity mismatch");
        int at = 0;
        Map<String, Block> blocks = byId(result.blocks());
        for (Block b : result.blocks()) {
            if (b.startUtf16() != at || b.endUtf16() <= at || b.endUtf16() > source.text().length()
                    || !source.text().substring(at, b.endUtf16()).equals(b.text()) || !sha256(b.text()).equals(b.sha256()))
                throw new IllegalArgumentException("Source mapping mismatch");
            if (b.startUtf16() > 0 && Character.isLowSurrogate(source.text().charAt(b.startUtf16())))
                throw new IllegalArgumentException("Split surrogate pair");
            at = b.endUtf16();
        }
        if (at != source.text().length()) throw new IllegalArgumentException("Source not fully accounted for");
        for (Packet p : concat(result.knowledgeCandidates(), result.pointerCandidates())) {
            if (!p.documentId().equals(source.documentId()) || !p.versionId().equals(source.versionId())
                    || !p.sourceRef().equals(source.sourceRef()) || !p.sourceSha256().equals(result.sourceSha256()))
                throw new IllegalArgumentException("Cross-source packet");
            for (String id : concat(p.primaryBlockIds(), p.contextBlockIds()))
                if (!blocks.containsKey(id) || blocks.get(id).decision() != Decision.KEEP)
                    throw new IllegalArgumentException("Unapproved packet reference");
        }
    }
    private static <T> List<T> concat(List<T> a, List<T> b) { List<T> r = new ArrayList<>(a); r.addAll(b); return r; }

    private Verdict verdict(Decision d, String reason, String rule, String hint) { return new Verdict(d, reason, rule, hint); }
    private Verdict decide(Draft draft, String raw, List<String> path, boolean entireDocument) {
        String value = raw.strip(), section = path.isEmpty() ? "" : path.getLast();
        if (Set.of("SPACE", "WRAPPER", "LAYOUT").contains(draft.kind())) return verdict(Decision.DROP, "LAYOUT_ONLY", "R11", "FORMAT");
        if (draft.broken()) return verdict(Decision.REVIEW, "INCOMPLETE_STRUCTURE", "R10", "INCOMPLETE");
        if (draft.kind().equals("FIGURE")) return verdict(Decision.REVIEW, "CAPTION_AND_UNREAD_IMAGE", "R10", "IMAGE_UNREAD");
        if (draft.kind().equals("CODE")) return verdict(Decision.KEEP, "CODE_WITH_SOURCE_CONTEXT", "R04", "USAGE");
        if (has("ignore (?:all |previous )?instructions|忽略.{0,12}(?:指令|规则)|reveal.{0,20}(?:secret|prompt)", value))
            return verdict(Decision.REVIEW, "INSTRUCTION_LIKE_SOURCE", "R10", "UNTRUSTED_INSTRUCTION");
        if (has("\\][(][^)]*$", value) || has("(?:如果|他们|\\b(?:if|they|the))\\s*$", value))
            return verdict(Decision.REVIEW, "POSSIBLY_TRUNCATED_TEXT", "R10", "INCOMPLETE");
        if (has("(?:free|免费).{0,70}(?:credits?|calls?|requests?|tokens?|额度|调用)|(?:beta|公开测试).{0,150}(?:free|免费)|commercial.{0,70}coming online|very likely.{0,80}(?:vendor|support)|效率.{0,20}\\d+%|效率\\d+%", value.replace('\n', ' ')))
            return verdict(Decision.REVIEW, "TIME_SENSITIVE_OR_UNVERIFIED_CLAIM", "R10", "UNCERTAIN_CLAIM");
        if (draft.kind().equals("HEADING")) {
            if (has(NOISE_SECTION, section) && !has(LICENSE + "|" + LIMIT + "|" + MAINTENANCE, value))
                return verdict(Decision.DROP, "NOISE_SECTION_HEADING", "R01", "NOISE");
            return verdict(Decision.KEEP, "SCOPE_HEADING_ONLY", "R07", "CONTEXT_ONLY");
        }
        // Decorative attributes, badge labels and URLs cannot certify restrictions or licenses.
        if(ReadmeSourceSemantics.imageOnly(raw)) {
            if(has(NOISE_SECTION,section)||has(NOISE,value))return verdict(Decision.DROP,"DECORATIVE_OR_PROMOTIONAL","R01","NOISE");
            return verdict(Decision.REVIEW,"IMAGE_OR_MIXED_MARKUP","R10","IMAGE_UNREAD");
        }
        String visible=ReadmeSourceSemantics.visible(raw);
        if(has(NOISE_SECTION,section)&&!has(LICENSE+"|"+LIMIT+"|"+MAINTENANCE,visible))
            return verdict(Decision.DROP,"EXPLICIT_NOISE_SECTION","R01","NOISE");
        if(ReadmeSourceSemantics.literalNavigation(raw))return verdict(Decision.KEEP,"LITERAL_NAVIGATION_METADATA","R09","SOURCE_POINTER");
        if(draft.kind().equals("PROSE")&&ReadmeSourceSemantics.unqualifiedPromise(raw))
            return verdict(Decision.REVIEW,"UNQUALIFIED_PROMISE_REQUIRES_REVIEW","R10","UNCERTAIN_CLAIM");
        if(has("why.{0,25}(?:name|called)|name.{0,15}(?:origin|meaning)|命名|名称由来|名字由来|为什么.{0,12}(?:叫|名)",section)
                &&has("name|called|名字|名称|命名",visible))return verdict(Decision.DROP,"ORIGIN_STORY","R01","NOISE");
        if (has(MAINTENANCE, visible)) return verdict(Decision.KEEP, "MAINTENANCE_CONDITION", "R08", "MAINTENANCE");
        if (has(LICENSE, visible)) return verdict(Decision.KEEP, "LICENSE_OR_EXCEPTION", "R03", "LICENSE");
        if (has(LIMIT, visible)) return verdict(Decision.KEEP, "CONDITION_OR_NEGATION", "R05", "LIMITATION");
        // A documentation column does not turn a table's factual cells into navigation.
        // Pure link tables are separated by the final source-semantic projection.
        if (draft.kind().equals("TABLE")) return verdict(Decision.KEEP, "COMPLETE_TABLE_WITH_HEADER", "R06", "TABLE");
        if (draft.kind().equals("LINK") && ReadmeRecommendationProjection.literalPointer(value)
                && !has(NOISE_SECTION, section) && !has(NOISE, value))
            return verdict(Decision.KEEP, "LITERAL_NAVIGATION_METADATA", "R09", "SOURCE_POINTER");
        if (has("\\]\\(#[^)]*\\)", value)) return verdict(Decision.REVIEW, "INTERNAL_ANCHOR_REQUIRES_BODY_CHECK", "R10", "SOURCE_POINTER");
        String labels = value.replaceAll("(?i)(?:href|src|srcset)\\s*=\\s*(?:\"[^\"]*\"|'[^']*')", "")
                .replaceAll("https?://[^\\s)\"'>]+", "");
        boolean link = has("https?://|\\]\\(|href=|\\[[^]]+\\]\\[[^]]+\\]", value);
        if (link && (has(POINTER, labels + " " + section) || has("colab\\.research\\.google|open-app\\.runwayml", value)))
            return verdict(Decision.KEEP, "LABELED_SOURCE_POINTER", "R09", "SOURCE_POINTER");
        if (has(NOISE_SECTION, section)) return verdict(Decision.DROP, "EXPLICIT_NOISE_SECTION", "R01", "NOISE");
        if (has("translate\\.google\\.com|README[_-][a-z]{2}\\.|badge/(?:English|简体|繁體|日本語)", value)
                && !has(KNOWLEDGE_SECTION + "|" + LIMIT, value)) return verdict(Decision.DROP, "README_TRANSLATION_NAV", "R01", "NOISE");
        if (has(NOISE, value) && has("!\\[|<img|sponsors|buymeacoffee|star", value)
                && (draft.kind().equals("LINK") || !has(KNOWLEDGE_SECTION, labels)))
            return verdict(Decision.DROP, "DECORATIVE_OR_PROMOTIONAL", "R01", "NOISE");
        if (has("!\\[|<img|<picture", value)) {
            String outsideImages = value.replaceAll("(?s)<[^>]*>|!\\[[^]]*]\\([^\\n]*?\\)|\\[[^]]*]\\([^\\n]*?\\)", "").strip();
            if (outsideImages.isBlank() && has("logo|标志|徽标", value)) return verdict(Decision.DROP, "LOGO_ONLY", "R01", "NOISE");
            return verdict(Decision.REVIEW, "IMAGE_OR_MIXED_MARKUP", "R10", "IMAGE_UNREAD");
        }
        if (has("^</?(?:div|p|details|summary|br|hr|span)(?:\\s[^>]*)?>$|^<!--.*-->$", value))
            return verdict(Decision.REVIEW, "HTML_CONTEXT_REQUIRES_PARSE", "R11", "MIXED");
        if (draft.kind().equals("HTML_TABLE") || draft.kind().equals("INDENTED"))
            return verdict(Decision.REVIEW, "STRUCTURE_REQUIRES_SEMANTIC_REVIEW", "R10", "UNKNOWN");
        if (has(KNOWLEDGE_SECTION, section)) return verdict(Decision.KEEP, "SCOPED_TECHNICAL_TEXT", "R02", "TECHNICAL_TEXT");
        if (has("\\bis (?:an?|the)\\b|\\bare (?:an?|the)\\b|provides|enables|这些脚本|是一个|是一款|是一个专门|is the source for|source for the|是一套|实时编码|for the web", value))
            return verdict(Decision.KEEP, "EXPLICIT_PURPOSE_STATEMENT", "R12", "PURPOSE");
        if (entireDocument && value.length() < 80) return verdict(Decision.REVIEW, "INSUFFICIENT_CONTENT", "R10", "UNKNOWN");
        return verdict(Decision.REVIEW, "SEMANTIC_REVIEW_NEEDED", "R10", "UNKNOWN");
    }
    private String roleHint(String text) {
        String intro = text.substring(0, Math.min(text.length(), 2500));
        if (has("source for.{0,80}website|网站.{0,8}源码|website.{0,20}source", intro.replace('\n', ' '))) return "DOCUMENTATION_SITE_HINT";
        if (has("缺陷仓库|bug.{0,20}repository|issue tracker", intro)) return "ISSUE_TRACKER_HINT";
        if (has("reference implementation|参考实现|并非为生产用途", intro)) return "REFERENCE_IMPLEMENTATION_HINT";
        if (has("free learning resources|资源集合|curated list", intro)) return "RESOURCE_COLLECTION_HINT";
        if (has("\\bSDK\\b|\\blibrary\\b|工具包|库，", intro)) return "LIBRARY_OR_SDK_HINT";
        if (has("firmware|固件", intro)) return "FIRMWARE_HINT";
        return "UNKNOWN";
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
