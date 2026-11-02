package com.orangejd.server.ragb.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/** Offline, conservative source selection. No Spring lifecycle, I/O, model or index side effects. */
public final class ReadmeKnowledgeFilter {
    public static final String POLICY = "README_RULE_PREVIEW_V1_4";
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
    private record Line(int start, int end, int number, String value) {}
    private record Draft(int from, int to, String kind, boolean broken) {}
    private record Verdict(Decision decision, String reason, String rule, String hint) {}
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();
    private static Pattern rx(String regex) { return PATTERNS.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)); }
    private static boolean has(String regex, String value) { return rx(regex).matcher(value).find(); }
    private static final Pattern FENCE = rx("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern HEADING = rx("^ {0,3}(#{1,6})\\s+(.+)$");
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
    static Map<String, List<String>> dependencies(List<Block> blocks) {
        Map<String, Block> all = byId(blocks);
        Map<String, LinkedHashSet<String>> edges = new LinkedHashMap<>();
        for (Block b : blocks) {
            var refs = new LinkedHashSet<>(b.headingIds());
            if (b.knowledgeTypeHints().contains("SOURCE_POINTER"))
                refs.removeIf(id -> all.get(id).decision() == Decision.DROP);
            edges.put(b.blockId(), refs);
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block code = blocks.get(i);
            if (!ReadmeSourceSemantics.codeLike(code)) continue;
            int included = 0;
            for (int j = i - 1; j >= 0 && included < 2; j--) {
                Block before = blocks.get(j);
                if (!before.sectionPath().equals(code.sectionPath())) break;
                if (before.kind().equals("SPACE")) continue;
                // Badges, images, layout and headings are boundaries, not code prerequisites.
                if (!Set.of("PROSE", "CODE", "TABLE", "HTML_TABLE", "INDENTED").contains(before.kind())) break;
                edges.get(code.blockId()).add(before.blockId());
                // Paired commands (including restore commands) and their lead-ins are atomic.
                if (ReadmeSourceSemantics.codeLike(before) || included == 0 && isLeadIn(before))
                    edges.get(before.blockId()).add(code.blockId());
                if(included==0&&isLeadIn(before)&&has("if.{0,80}(?:error|fail)|如果.{0,80}(?:错误|失败)",ReadmeSourceSemantics.visible(before.text())))break;
                included++;
            }
        }
        for(var span:ReadmeSourceSemantics.indentedSpans(blocks)){
            List<String> members=span.blocks().stream().map(Block::blockId).toList();
            for(String id:members)for(String other:members)if(!id.equals(other))edges.get(id).add(other);
            int before=blocks.indexOf(span.blocks().getFirst())-1;
            while(before>=0&&blocks.get(before).kind().equals("SPACE"))before--;
            if(before>=0&&blocks.get(before).sectionPath().equals(span.blocks().getFirst().sectionPath())&&isLeadIn(blocks.get(before))){
                String lead=blocks.get(before).blockId();
                for(String id:members){edges.get(id).add(lead);edges.get(lead).add(id);}
            }
        }
        // Named CLI options have semantic definitions beyond their immediate Markdown neighbors.
        // Attach the closest preceding option reference when an example/exit-code explanation uses it.
        for(int i=0;i<blocks.size();i++) {
            Block subject=blocks.get(i);
            if(!Set.of("PROSE","CODE","INDENTED").contains(subject.kind()))continue;
            Matcher flags=Pattern.compile("(?<![\\w-])--([a-z][a-z0-9-]{1,40})").matcher(subject.text());
            Set<String> names=new LinkedHashSet<>();while(flags.find())names.add(flags.group(1));
            if(names.isEmpty())continue;
            for(String name:names)for(int j=i-1;j>=0;j--) {
                Block definition=blocks.get(j);
                if(!definition.kind().equals("CODE")||!has("(?m)^\\s*Options?:\\s*$",definition.text()))continue;
                if(has("(?m)^\\s*--?"+Pattern.quote(name)+"(?:\\s|=|$)",definition.text())) {edges.get(subject.blockId()).add(definition.blockId());break;}
            }
        }
        for(int i=0;i<blocks.size();i++){
            Block subject=blocks.get(i);
            if(ReadmeSourceSemantics.codeLike(subject)){
                Matcher manager=Pattern.compile("(?m)^\\s*(pnpm|yarn)\\s+(?:install|build|start|dev|run|test)\\b").matcher(subject.text());
                if(manager.find())for(int j=i-1;j>=0;j--){
                    Block prerequisite=blocks.get(j);
                    if(!Collections.disjoint(subject.headingIds(),prerequisite.headingIds())
                            &&has("(?m)^\\s*(?:npm\\s+(?:i|install)\\s+.*\\b"+manager.group(1)+"\\b|corepack\\s+(?:enable|prepare)\\b)",prerequisite.text())){
                        edges.get(subject.blockId()).add(prerequisite.blockId());break;
                    }
                }
                // Keep the source-install requirement with the CLI built by that command, not with unrelated project facts.
                Matcher cli=Pattern.compile("(?m)^([a-z][a-z0-9_-]+)\\s+\\[options]",Pattern.CASE_INSENSITIVE).matcher(subject.text());
                if(cli.find()&&has("(?m)^Options?:",subject.text())){
                    boolean sourceInstall=false;Block versionRequirement=null;
                    for(int j=i-1;j>=0;j--){Block prior=blocks.get(j);
                        if(has("go\\s+(?:install|build).{0,300}(?:/|\\b)"+Pattern.quote(cli.group(1))+"(?:@|\\s|$)",prior.text()))sourceInstall=true;
                        if(versionRequirement==null&&has("requires?\\s+Go\\s+\\d+(?:\\.\\d+)+",ReadmeSourceSemantics.visible(prior.text())))versionRequirement=prior;
                        if(sourceInstall&&versionRequirement!=null){edges.get(subject.blockId()).add(versionRequirement.blockId());break;}
                    }
                }
            }else if(Set.of("PROSE","INDENTED").contains(subject.kind())&&has("现在|now.{0,15}(?:access|visit)|访问应用|available at|accessible at",ReadmeSourceSemantics.visible(subject.text()))){
                for(int j=i-1;j>=0;j--){Block prior=blocks.get(j);if(prior.kind().equals("SPACE"))continue;
                    if(prior.sectionPath().equals(subject.sectionPath())&&ReadmeSourceSemantics.codeLike(prior))edges.get(subject.blockId()).add(prior.blockId());break;
                }
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        edges.forEach((id, refs) -> result.put(id, List.copyOf(refs)));
        return Collections.unmodifiableMap(result);
    }
    private static boolean isLeadIn(Block block) {
        return Set.of("PROSE","INDENTED").contains(block.kind()) && !ReadmeSourceSemantics.codeLike(block)
                && has("[:：](?:\\*\\*)?\\s*$",ReadmeSourceSemantics.visible(block.text()));
    }
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
    private static Map<String, Block> byId(List<Block> blocks) {
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

    private List<Line> lines(String text) {
        List<Line> result = new ArrayList<>();
        for (int at = 0, n = 1; at < text.length(); n++) {
            int end = text.indexOf('\n', at); end = end < 0 ? text.length() : end + 1;
            result.add(new Line(at, end, n, text.substring(at, end).replaceFirst("[\\r\\n]+$", ""))); at = end;
        }
        return result;
    }
    private List<Draft> parse(List<Line> lines) {
        List<Draft> out = new ArrayList<>();
        int n = lines.size(), from = 0, limit = n;
        // Repair only full-document wrappers with multiple real Markdown headings, never ordinary HTML examples.
        if (n >= 3 && has("^ {0,3}(`{3,}|~{3,})(?:html|markdown|md)\\s*$", lines.getFirst().value())
                && lines.subList(1, n - 1).stream().filter(l -> HEADING.matcher(l.value()).matches()).count() >= 2
                && lines.stream().noneMatch(l -> has("<(?:!doctype|html|head|body)\\b", l.value()))) {
            Matcher f = FENCE.matcher(lines.getFirst().value()); f.matches();
            if (closing(lines.getLast().value(), f.group(1))) { out.add(new Draft(0, 1, "WRAPPER", false)); from = 1; limit = n - 1; }
        }
        for (int i = from; i < limit;) {
            String value = lines.get(i).value(); int end = i + 1;
            if (value.isBlank()) {
                while (end < limit && lines.get(end).value().isBlank()) end++;
                out.add(new Draft(i, end, "SPACE", false)); i = end; continue;
            }
            Matcher f = FENCE.matcher(value);
            if (f.matches()) {
                while (end < limit && !closing(lines.get(end).value(), f.group(1))) end++;
                boolean broken = end == limit;
                if (!broken) end++;
                String body = joined(lines, i, end);
                broken |= has("(?m)^\\s*import\\s+['\"][^'\"\\r\\n]+$", body);
                out.add(new Draft(i, end, "CODE", broken)); i = end; continue;
            }
            if (HEADING.matcher(value).matches() || end < limit && has("^ {0,3}(?:={2,}|-{2,})\\s*$", lines.get(end).value()) && !has("^\\s*[-*+]\\s", value)) {
                if (!HEADING.matcher(value).matches()) end++;
                out.add(new Draft(i, end, "HEADING", false)); i = end; continue;
            }
            if (has("^\\s*<table\\b", value)) {
                boolean closed = has("</table\\s*>", value);
                while (end < limit && !closed) { closed = has("</table\\s*>", lines.get(end).value()); end++; }
                out.add(new Draft(i, end, "HTML_TABLE", !closed)); i = end; continue;
            }
            if (value.stripLeading().startsWith("|")) {
                while (end < limit && lines.get(end).value().stripLeading().startsWith("|")) end++;
                int columns = columns(value);
                boolean header = i + 1 < end && has("^\\s*\\|?[ :|-]+\\|\\s*$", lines.get(i + 1).value());
                int goodEnd = i + 2;
                if (header) while (goodEnd < end && columns(lines.get(goodEnd).value()) == columns) goodEnd++;
                if (header) out.add(new Draft(i, goodEnd, "TABLE", false));
                if (!header || goodEnd < end) out.add(new Draft(header ? goodEnd : i, end, "TABLE", true));
                i = end; continue;
            }
            if (standaloneLink(value)) {
                boolean broken = false;
                if (has("^\\s*<a\\s", value) && !has("</a\\s*>", value)) {
                    while (end < limit && !has("</a\\s*>", lines.get(end - 1).value())) end++;
                    broken = !has("</a\\s*>", lines.get(end - 1).value());
                }
                out.add(new Draft(i, end, "LINK", broken)); i = end; continue;
            }
            if (layoutTag(value)) { out.add(new Draft(i, end, "LAYOUT", false)); i = end; continue; }
            boolean indent = value.startsWith("    ") || value.startsWith("\t");
            while (end < limit && !lines.get(end).value().isBlank() && !special(lines, end, limit)) end++;
            out.add(new Draft(i, end, indent ? "INDENTED" : "PROSE", false)); i = end;
        }
        if (limit < n) out.add(new Draft(limit, n, "WRAPPER", false));
        List<Draft> grouped = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Draft d = out.get(i); int next = i + 1;
            if (next < out.size() && out.get(next).kind().equals("SPACE")) next++;
            String label = joined(lines, d.from(), d.to()).strip();
            if (d.kind().equals("PROSE") && label.length() < 120 && has("[:：](?:\\*\\*)?$", label)
                    && next < out.size() && out.get(next).kind().equals("LINK")
                    && has("^\\s*!\\[", joined(lines, out.get(next).from(), out.get(next).to()))) {
                grouped.add(new Draft(d.from(), out.get(next).to(), "FIGURE", false)); i = next;
            } else grouped.add(d);
        }
        return grouped;
    }
    private boolean special(List<Line> lines, int i, int limit) {
        String v = lines.get(i).value();
        return FENCE.matcher(v).matches() || HEADING.matcher(v).matches() || v.stripLeading().startsWith("|")
                || has("^\\s*<table\\b", v) || standaloneLink(v) || layoutTag(v)
                || i + 1 < limit && has("^ {0,3}(?:={2,}|-{2,})\\s*$", lines.get(i + 1).value());
    }
    private boolean standaloneLink(String value) {
        return has("^\\s*(?:\\[?!\\[|<a\\s)", value)
                || has("^\\s*(?:[-*+]\\s+)?[\\p{So}\\s\\uFE0F]*\\[[^\\n]+]\\([^\\n]+\\)\\s*(?:&#160;)?$", value);
    }
    private boolean layoutTag(String value) { return has("^\\s*</?(?:div|p|br|hr|span)(?:\\s[^>]*)?>\\s*$", value); }
    private boolean closing(String value, String opening) {
        return value.matches("^ {0,3}" + Pattern.quote(opening.substring(0, 1)) + "{" + opening.length() + ",}\\s*$");
    }
    private String joined(List<Line> lines, int from, int to) {
        StringBuilder b = new StringBuilder(); for (int i = from; i < to; i++) b.append(lines.get(i).value()).append('\n'); return b.toString();
    }
    private int columns(String line) {
        int pipes = 0; boolean escaped = false, code = false;
        for (char c : line.toCharArray()) { if (escaped) { escaped = false; continue; }
            if (c == '\\') escaped = true; else if (c == '`') code = !code; else if (c == '|' && !code) pipes++; }
        return pipes;
    }
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
        if(has("why.{0,25}(?:name|called)|name.{0,15}(?:origin|meaning)|命名|名称由来|名字由来|为什么.{0,12}(?:叫|名)",section)
                &&has("name|called|名字|名称|命名",visible))return verdict(Decision.DROP,"ORIGIN_STORY","R01","NOISE");
        if (has(MAINTENANCE, visible)) return verdict(Decision.KEEP, "MAINTENANCE_CONDITION", "R08", "MAINTENANCE");
        if (has(LICENSE, visible)) return verdict(Decision.KEEP, "LICENSE_OR_EXCEPTION", "R03", "LICENSE");
        if (has(LIMIT, visible)) return verdict(Decision.KEEP, "CONDITION_OR_NEGATION", "R05", "LIMITATION");
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
        if (draft.kind().equals("TABLE")) return verdict(Decision.KEEP, "COMPLETE_TABLE_WITH_HEADER", "R06", "TABLE");
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
