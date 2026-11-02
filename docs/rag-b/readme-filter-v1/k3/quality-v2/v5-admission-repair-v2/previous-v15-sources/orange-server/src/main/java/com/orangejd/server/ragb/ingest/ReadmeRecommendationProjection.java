package com.orangejd.server.ragb.ingest;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;

/** Final, source-bound admission policy for K3. Pure local transformation; no index or network access. */
public final class ReadmeRecommendationProjection {
    public static final String POLICY = "README_RECOMMENDATION_V3";
    public enum Applicability { IN_SCOPE, OUT_OF_SCOPE, UNKNOWN }
    public enum Disposition { KNOWLEDGE_CANDIDATE, POINTER_METADATA_ONLY, ARCHIVE_ONLY, REVIEW_QUEUE, CONTEXT_ONLY }
    public record Pointer(String label, String href, String evidenceBlockId,List<String> hints) {
        public Pointer {hints=List.copyOf(hints);}
        public Pointer(String label,String href,String evidenceBlockId){this(label,href,evidenceBlockId,List.of());}
    }
    public record Assessment(String blockId, Decision proposedDecision, Decision effectiveDecision, Kind kind,
                             Applicability applicability, List<Scope> scopes, List<String> evidenceBlockIds,
                             List<String> dependencyBlockIds, Disposition disposition, String reason) {}
    public record Candidate(Packet packet, List<Scope> subjectScopes, List<Assessment> evidence) {}
    public record Versions(String parser, String rulePolicy, String selectionPolicy, String prompt, String schema, String modelIdentity, String runId) {}
    public record Projection(String policyVersion, Versions versions, List<Assessment> assessments,
                             List<Candidate> knowledgeCandidates, List<Candidate> pointerCandidates,
                             List<Pointer> pointers, List<String> reviewBlockIds) {}
    public record Output(Preview preview, Projection projection) {}
    private static final String COURSE = "earn.{0,40}certificat|revoke.{0,40}certificat|qualify.{0,40}exam|quizzes|academic honesty|认证考试|考试资格|证书展示|撤销证书|课程宣传";
    private static final String PROJECT = "(?s)(?:\\bis\\b.{0,100}\\b(?:tool|library|framework|application|service|SDK|source for|source code|orchestration)\\b|source (?:code )?for.{0,80}website|(?:是一个|是一款|是一套).{0,60}(?:应用|工具|框架|服务|代码库)|网站.{0,10}源码)";
    private static final String PURE = "(?:only|solely|exclusively).{0,35}(?:course materials|learning resources)|(?:curated (?:list|collection)|collection of (?:free )?(?:learning )?resources)|仅.{0,12}(?:课程资料|资源集合)|(?:这是|本仓库是|这里是).{0,160}(?:软件|应用程序|资源|网络服务).{0,160}(?:列表|集合)|a (?:list|collection) of .{0,80}(?:software|web applications|network services)";
    private static final Set<String> HARD = Set.of("INCOMPLETE_STRUCTURE", "INCOMPLETE_EXAMPLE_CONTEXT", "POSSIBLY_TRUNCATED_TEXT",
            "CAPTION_AND_UNREAD_IMAGE", "IMAGE_OR_MIXED_MARKUP", "TIME_SENSITIVE_OR_UNVERIFIED_CLAIM", "INSTRUCTION_LIKE_SOURCE");
    private static final Map<String,Pattern> PATTERNS = new ConcurrentHashMap<>();
    private static boolean has(String pattern, String text) { return PATTERNS.computeIfAbsent(pattern, k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).matcher(text).find(); }

    /** Labels and addresses only. This neither resolves links nor interprets destination contents. */
    static List<Pointer> pointers(Block b) {
        return ReadmeSourceSemantics.pointers(b);
    }
    static boolean literalPointer(String raw) {
        return ReadmeSourceSemantics.literalNavigation(raw);
    }
    private static boolean heading(Block b) { return b.kind().equals("HEADING"); }
    static boolean guard(Block b) {
        return ReadmeSourceSemantics.condition(b);
    }
    private static List<Block> anchors(Preview p) {
        return p.blocks().stream().filter(b -> b.kind().equals("PROSE") && b.startUtf16() < 3000
                && b.decision() == Decision.KEEP && has(PROJECT, ReadmeSourceSemantics.visible(b.text()))
                && !has(PURE + "|" + COURSE, ReadmeSourceSemantics.visible(b.text()))).limit(2).toList();
    }
    static List<Block> anchorContext(Preview p) { return anchors(p); }
    private static boolean pureRepository(Preview p) {
        return anchors(p).isEmpty() && p.blocks().stream().filter(b -> b.startUtf16() < 2500 && b.kind().equals("PROSE"))
                .anyMatch(b -> has(PURE, ReadmeSourceSemantics.visible(b.text())));
    }
    private static List<Scope> localScopes(Block b) {
        String local = String.join(" ", b.sectionPath()) + " " + ReadmeSourceSemantics.visible(b.text());
        List<Scope> result = new ArrayList<>();
        if(ReadmeSourceSemantics.navigationProse(b)&&has("\\bCloud\\b|托管实例|云入口",local))result.add(Scope.RELATED_PRODUCT);
        if (has("enterprise|企业版|企业功能", local)) result.add(Scope.ENTERPRISE);
        if (has("community edition|社区版", local)) result.add(Scope.COMMUNITY);
        if (has("\\b(?:Windows|Linux|macOS|Android|iOS|browser|Node\\.js)\\b|浏览器|平台限定", local)) result.add(Scope.PLATFORM_SPECIFIC);
        if (has("\\bversion\\s+\\d|\\bv\\d+\\.\\d|版本.{0,8}\\d|\\b(?:Go|Java|Python|Node\\.js|Ruby|PHP|Rust|SDK|firmware)\\s*(?:>=?|v)?\\s*\\d+(?:\\.\\d+)+|版本.{0,30}(?:分支|匹配|对应)|branch.{0,30}version|version.{0,30}branch", local)) result.add(Scope.VERSION_SPECIFIC);
        if (has("non.production|not (?:for|suitable for) production|非生产|不适合生产", local)) result.add(Scope.NON_PRODUCTION);
        return List.copyOf(result);
    }
    static boolean mixedEditions(Block b) {
        // A heading cannot erase an explicit comparison inside the same unsplit block.
        String raw = b.text();
        return has("community edition|社区版", raw) && has("enterprise|企业版|企业功能", raw);
    }
    private static Kind kind(Block b) {
        if(ReadmeSourceSemantics.navigationTable(b))return Kind.SOURCE_POINTER;
        if(ReadmeSourceSemantics.navigationProse(b))return Kind.SOURCE_POINTER;
        if(!b.kind().equals("CODE") && literalPointer(b.text()))return Kind.SOURCE_POINTER;
        if(b.kind().equals("PROSE") && b.knowledgeTypeHints().contains("SOURCE_POINTER") && !literalPointer(b.text()))return Kind.OTHER;
        for (String hint : b.knowledgeTypeHints()) try { return Kind.valueOf(hint); } catch (IllegalArgumentException ignored) { }
        if (b.kind().equals("CODE")) return Kind.USAGE;
        if (b.kind().equals("TABLE")) return Kind.TECHNICAL_TEXT;
        return Kind.OTHER;
    }
    static Map<String,Assessment> ruleAssessments(Preview p) {
        var anchors = anchors(p); boolean pure = pureRepository(p);
        Map<String,Assessment> result = new LinkedHashMap<>();
        p.blocks().forEach(b -> result.put(b.blockId(), ruleAssessment(b, anchors, pure)));
        return result;
    }
    private static Assessment ruleAssessment(Block b, List<Block> anchors, boolean pure) {
        List<Scope> scopes = localScopes(b);
        List<String> evidence = new ArrayList<>(b.headingIds()); evidence.add(b.blockId());
        Applicability app = Applicability.UNKNOWN;
        String reason = "APPLICABILITY_REQUIRES_REVIEW";
        if (has(COURSE, b.text()) && !has(PROJECT, b.text())) { app = Applicability.OUT_OF_SCOPE; reason = "COURSE_ADMINISTRATION"; }
        else if (has(PURE,ReadmeSourceSemantics.visible(b.text())) || pure && !b.kind().equals("CODE") && !has(PROJECT, b.text())) { app = Applicability.OUT_OF_SCOPE; reason = "RESOURCE_COLLECTION"; }
        else if (!b.kind().equals("CODE") && has("curriculum|course (?:outline|overview|catalog)|课程(?:目录|介绍|宣传)|认证考试",String.join(" ",b.sectionPath())) && !has(PROJECT,b.text())) {
            app = Applicability.OUT_OF_SCOPE; reason = "COURSE_MATERIAL";
        }
        else if (!anchors.isEmpty() || has(PROJECT, b.text())) {
            app = Applicability.IN_SCOPE; reason = "PROJECT_SOURCE_EVIDENCE"; anchors.forEach(a -> evidence.add(a.blockId()));
        }
        if (scopes.isEmpty() && app == Applicability.IN_SCOPE) scopes = List.of(Scope.CORE);
        Kind kind = kind(b);
        if(ReadmeSourceSemantics.navigationTable(b)){app=Applicability.UNKNOWN;if(scopes.equals(List.of(Scope.CORE)))scopes=List.of();}
        Disposition destination = b.decision() == Decision.DROP ? Disposition.ARCHIVE_ONLY : Disposition.REVIEW_QUEUE;
        if (heading(b)) destination = b.decision() == Decision.KEEP ? Disposition.CONTEXT_ONLY : destination;
        else if (b.decision() == Decision.KEEP && kind == Kind.SOURCE_POINTER && !pointers(b).isEmpty()) destination = Disposition.POINTER_METADATA_ONLY;
        else if (b.decision() == Decision.KEEP && app == Applicability.OUT_OF_SCOPE) destination = Disposition.ARCHIVE_ONLY;
        else if (b.decision() == Decision.KEEP && app == Applicability.IN_SCOPE && kind != Kind.OTHER) destination = Disposition.KNOWLEDGE_CANDIDATE;
        if (mixedEditions(b) && !heading(b)) { destination = Disposition.REVIEW_QUEUE; reason = "MIXED_EDITION_REQUIRES_SPLIT"; }
        if (kind!=Kind.SOURCE_POINTER&&ReadmeSourceSemantics.mixedProducts(b)) { destination=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;reason="MIXED_PRODUCT_REQUIRES_SPLIT"; }
        if(ReadmeSourceSemantics.guideLeadIn(b)){destination=Disposition.REVIEW_QUEUE;reason="NON_STANDALONE_GUIDE";}
        if(ReadmeSourceSemantics.mixedLicenseModes(b)){destination=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;reason="MIXED_LICENSE_SCOPE_REQUIRES_SPLIT";}
        return new Assessment(b.blockId(), b.decision(), destination == Disposition.REVIEW_QUEUE ? Decision.REVIEW : b.decision(), kind,
                app, scopes, List.copyOf(new LinkedHashSet<>(evidence)), List.of(), destination, reason);
    }
    static boolean needsModel(Block b, Map<String,Assessment> rules) {
        if(ReadmeSourceSemantics.imageOnly(b.text())||Set.of("MIXED_PRODUCT_REQUIRES_SPLIT","MIXED_EDITION_REQUIRES_SPLIT","MIXED_LICENSE_SCOPE_REQUIRES_SPLIT","NON_STANDALONE_GUIDE").contains(rules.get(b.blockId()).reason()))return false;
        if (rules.get(b.blockId()).applicability() == Applicability.OUT_OF_SCOPE) return false;
        if (ReadmeModelReview.eligible(b)) return true;
        return b.decision() == Decision.KEEP && !heading(b)
                && rules.get(b.blockId()).disposition() == Disposition.REVIEW_QUEUE;
    }
    public Output project(Source source, Preview p, List<Selection> selections) {
        return project(source, p, selections, "RULE_ONLY", "RULE_ONLY");
    }
    public Output project(Source source, Preview p, List<Selection> selections, String modelIdentity, String runId) {
        ReadmeKnowledgeFilter.verify(source, p);
        Map<String,Block> blocks = new LinkedHashMap<>(); p.blocks().forEach(b -> blocks.put(b.blockId(), b));
        Map<String,Selection> proposals = new LinkedHashMap<>();
        for (Selection s : selections) {
            if (!blocks.containsKey(s.blockId()) || proposals.put(s.blockId(), s) != null) throw new IllegalArgumentException("Invalid projection selection");
            if (s.decision() == Decision.DROP && blocks.get(s.blockId()).decision() == Decision.KEEP) throw new IllegalArgumentException("Cannot discard fixed source decision");
            if (!s.evidenceBlockIds().contains(s.blockId()) || s.evidenceBlockIds().stream().anyMatch(id -> !blocks.containsKey(id)))
                throw new IllegalArgumentException("Invalid projection evidence");
        }
        Map<String,List<String>> deps = ReadmeKnowledgeFilter.dependencies(p.blocks());
        Map<String,Assessment> rules = ruleAssessments(p);
        Set<String> conflicts=new HashSet<>();p.blocks().stream().filter(b->ReadmeSourceSemantics.declaredListConflict(b,p.blocks())).forEach(b->conflicts.add(b.blockId()));
        boolean qualificationUnknown=anchors(p).isEmpty()&&p.blocks().stream().filter(b->b.startUtf16()<2000)
                .anyMatch(b->has("(?:firmware|固件).{0,25}(?:configurations?|配置)|配置(?:文件)?集合",ReadmeSourceSemantics.visible(b.text())));
        Map<String,Assessment> assessed = new LinkedHashMap<>();
        for (Block b : p.blocks()) {
            Assessment rule = rules.get(b.blockId()); Selection s = proposals.get(b.blockId());
            Kind type = s == null ? rule.kind() : s.kind();
            Applicability app = s == null ? rule.applicability() : s.applicability();
            List<Scope> scopes = new ArrayList<>(rule.scopes());
            if (s != null && s.scope() != Scope.UNKNOWN) scopes.add(s.scope());
            scopes = new ArrayList<>(new LinkedHashSet<>(scopes));
            if (scopes.size() > 1) scopes.remove(Scope.CORE);
            List<String> evidence = new ArrayList<>(rule.evidenceBlockIds()); if (s != null) evidence.addAll(s.evidenceBlockIds());
            Disposition d = rule.disposition(); String why = rule.reason();
            Decision effective = s == null ? rule.effectiveDecision() : s.decision();
            if (s != null && s.decision() == Decision.KEEP) {
                d = type == Kind.SOURCE_POINTER ? (pointers(b).isEmpty()?Disposition.REVIEW_QUEUE:Disposition.POINTER_METADATA_ONLY)
                        : app == Applicability.OUT_OF_SCOPE ? Disposition.ARCHIVE_ONLY
                        : app == Applicability.IN_SCOPE && type != Kind.OTHER && !scopes.isEmpty() ? Disposition.KNOWLEDGE_CANDIDATE : Disposition.REVIEW_QUEUE;
                why = "MODEL_CLASSIFIED_SOURCE";
            }
            if (s != null && s.decision() == Decision.REVIEW) d = Disposition.REVIEW_QUEUE;
            if (s != null && s.decision() == Decision.DROP) d = Disposition.ARCHIVE_ONLY;
            // Explicit course rules, hard source gaps and scope conflicts cannot be overridden by model confidence.
            if (rule.applicability() == Applicability.OUT_OF_SCOPE) { app = Applicability.OUT_OF_SCOPE; d = type == Kind.SOURCE_POINTER && !pointers(b).isEmpty() ? Disposition.POINTER_METADATA_ONLY : Disposition.ARCHIVE_ONLY; why = rule.reason(); }
            if (guard(b) && app == Applicability.OUT_OF_SCOPE && rule.applicability() != Applicability.OUT_OF_SCOPE) { app = Applicability.UNKNOWN; d = Disposition.REVIEW_QUEUE; why = "PROTECTED_CONDITION_SCOPE_CONFLICT"; }
            if (HARD.contains(b.reason()) || mixedEditions(b) || b.decision() == Decision.REVIEW && app != Applicability.OUT_OF_SCOPE) { d = Disposition.REVIEW_QUEUE; why = b.decision() == Decision.REVIEW ? b.reason() : "SOURCE_OR_EDITION_CONFLICT"; }
            if (scopes.contains(Scope.ENTERPRISE) && scopes.contains(Scope.COMMUNITY)) { d = Disposition.REVIEW_QUEUE; why = "EDITION_SCOPE_CONFLICT"; }
            if(b.decision()==Decision.KEEP && literalPointer(b.text()) && !pointers(b).isEmpty()) { type=Kind.SOURCE_POINTER; d=Disposition.POINTER_METADATA_ONLY; why="LITERAL_NAVIGATION_ONLY"; }
            if (s != null && s.scope() == Scope.RELATED_PRODUCT) scopes.remove(Scope.CORE);
            if (heading(b)) d = b.decision() == Decision.KEEP ? Disposition.CONTEXT_ONLY : Disposition.REVIEW_QUEUE;
            if (b.decision() == Decision.DROP) d = Disposition.ARCHIVE_ONLY;
            if(type==Kind.SOURCE_POINTER&&d==Disposition.KNOWLEDGE_CANDIDATE){d=Disposition.REVIEW_QUEUE;why="UNPARSED_NAVIGATION";}
            if(ReadmeSourceSemantics.imageOnly(b.text())){d=b.decision()==Decision.DROP?Disposition.ARCHIVE_ONLY:Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;why="IMAGE_EVIDENCE_NOT_VERIFIED";}
            if(d!=Disposition.POINTER_METADATA_ONLY&&ReadmeSourceSemantics.mixedProducts(b)){d=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;why="MIXED_PRODUCT_REQUIRES_SPLIT";}
            if(conflicts.contains(b.blockId())){d=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;why="DECLARED_LIST_COUNT_CONFLICT";}
            if(qualificationUnknown&&d==Disposition.KNOWLEDGE_CANDIDATE){d=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;why="REPOSITORY_QUALIFICATION_UNKNOWN";}
            if((ReadmeSourceSemantics.literalNavigation(b.text())||ReadmeSourceSemantics.navigationProse(b))&&!pointers(b).isEmpty()
                    &&b.decision()==Decision.KEEP&&!ReadmeSourceSemantics.imageOnly(b.text())) {
                type=Kind.SOURCE_POINTER;d=Disposition.POINTER_METADATA_ONLY;why="SOURCE_NAVIGATION_ONLY";effective=Decision.KEEP;
            }
            if(ReadmeSourceSemantics.navigationTable(b)&&b.decision()==Decision.KEEP){type=Kind.SOURCE_POINTER;d=Disposition.POINTER_METADATA_ONLY;app=Applicability.UNKNOWN;why="LINK_TABLE_NAVIGATION_ONLY";effective=Decision.KEEP;}
            if(ReadmeSourceSemantics.guideLeadIn(b)){d=Disposition.REVIEW_QUEUE;why="NON_STANDALONE_GUIDE";}
            if(ReadmeSourceSemantics.mixedLicenseModes(b)){d=Disposition.REVIEW_QUEUE;app=Applicability.UNKNOWN;why="MIXED_LICENSE_SCOPE_REQUIRES_SPLIT";}
            if(d==Disposition.POINTER_METADATA_ONLY&&b.decision()!=Decision.KEEP){d=Disposition.REVIEW_QUEUE;why="NAVIGATION_REQUIRES_SOURCE_REVIEW";}
            if (d == Disposition.REVIEW_QUEUE) effective = Decision.REVIEW;
            assessed.put(b.blockId(), new Assessment(b.blockId(), s == null ? b.decision() : s.decision(), effective, type, app, List.copyOf(scopes),
                    List.copyOf(new LinkedHashSet<>(evidence)), deps.get(b.blockId()), d, why));
        }
        // Propagate missing/archived/navigation dependencies. A pointer never becomes body context.
        List<Block> conditions=p.blocks().stream().filter(ReadmeSourceSemantics::condition).toList();
        boolean changed;
        do {
            changed = false;
            for (Block b : p.blocks()) {
                Assessment a = assessed.get(b.blockId());
                if (a.disposition() != Disposition.KNOWLEDGE_CANDIDATE) continue;
                boolean missingGuard = conditions.stream().filter(g -> ReadmeSourceSemantics.applies(g,b))
                        .map(g -> assessed.get(g.blockId())).anyMatch(g -> g.applicability() != Applicability.OUT_OF_SCOPE && !bodyContext(g));
                if (missingGuard || a.dependencyBlockIds().stream().anyMatch(id -> !bodyContext(assessed.get(id)))) {
                    assessed.put(b.blockId(), deferred(a, "DEPENDENCY_NOT_ADMITTED")); changed = true;
                }
            }
        } while (changed);
        List<Candidate> knowledge = new ArrayList<>(), navigation = new ArrayList<>();
        List<Packet> bodyPackets = new ArrayList<>(), pointerPackets = new ArrayList<>();
        List<Pointer> pointers = new ArrayList<>();
        // Literal navigation does not inherit the old body-packet size or global guard budget.
        for (Assessment a : assessed.values()) if (a.disposition() == Disposition.POINTER_METADATA_ONLY) {
            Packet raw = new Packet(p.sourceSha256()+":navigation:"+a.blockId(),p.documentId(),p.versionId(),p.sourceRef(),p.sourceSha256(),List.of(a.blockId()),List.of(),"POINTER_METADATA_ONLY");
            Packet packet = packet(raw,List.of(a.blockId()),List.of(),"POINTER_METADATA_ONLY");
            pointerPackets.add(packet); navigation.add(new Candidate(packet,a.scopes(),List.of(a))); pointers.addAll(pointers(blocks.get(a.blockId())));
        }
        for (Packet raw : concat(p.knowledgeCandidates(), p.pointerCandidates())) {
            // Preserve product/edition boundaries even within a single Markdown section.
            Map<List<Scope>,List<String>> groups = new LinkedHashMap<>();
            for (String id : raw.primaryBlockIds()) {
                Assessment a = assessed.get(id);
                if (a.disposition() == Disposition.KNOWLEDGE_CANDIDATE) groups.computeIfAbsent(a.scopes(), k -> new ArrayList<>()).add(id);
            }
            for (List<String> ids : groups.values()) {
                Set<String> refs = new LinkedHashSet<>(); for (String id : ids) ReadmeKnowledgeFilter.addDependencies(id, deps, refs);
                // Preserve applicable explicit guards, including unresolved ones; course administration does not contaminate project examples.
                for (Block b : conditions) if (ids.stream().anyMatch(id->ReadmeSourceSemantics.applies(b,blocks.get(id))) && assessed.get(b.blockId()).applicability() != Applicability.OUT_OF_SCOPE) {
                    refs.add(b.blockId()); ReadmeKnowledgeFilter.addDependencies(b.blockId(), deps, refs);
                }
                refs.removeAll(ids);
                if (refs.stream().anyMatch(id -> !bodyContext(assessed.get(id)))) {
                    ids.forEach(id -> assessed.put(id, deferred(assessed.get(id), "REQUIRED_CONTEXT_NOT_ADMITTED"))); continue;
                }
                Packet packet = packet(raw, ids, List.copyOf(refs), "KNOWLEDGE_CANDIDATE");
                List<Assessment> evidence = concat(ids, List.copyOf(refs)).stream().map(assessed::get).toList();
                bodyPackets.add(packet); knowledge.add(new Candidate(packet, assessed.get(ids.getFirst()).scopes(), evidence));
            }
        }
        Set<String> admittedIds = new HashSet<>();
        for (Packet packet : bodyPackets) { admittedIds.addAll(packet.primaryBlockIds()); admittedIds.addAll(packet.contextBlockIds()); }
        for (Assessment a : new ArrayList<>(assessed.values())) if (a.disposition() == Disposition.KNOWLEDGE_CANDIDATE && !admittedIds.contains(a.blockId()))
            assessed.put(a.blockId(),deferred(a,"NO_BUDGETED_KNOWLEDGE_PACKET"));
        List<String> review = assessed.values().stream().filter(a -> a.disposition() == Disposition.REVIEW_QUEUE).map(Assessment::blockId).toList();
        List<String> flags = new ArrayList<>(p.qualityFlags().stream().filter(f->!f.equals("HAS_REVIEW_BLOCKS")&&!f.equals("NO_APPROVED_BODY_PACKET")).toList());
        if(!review.isEmpty())flags.add("HAS_REVIEW_BLOCKS");
        if(bodyPackets.isEmpty())flags.add("NO_APPROVED_BODY_PACKET");
        flags.add("RECOMMENDATION_ADMISSION:" + POLICY);
        if(p.blocks().stream().anyMatch(b->assessed.get(b.blockId()).disposition()==Disposition.POINTER_METADATA_ONLY&&has("licen[cs]e|许可|requires?|prerequisite|要求|必须|限制",ReadmeSourceSemantics.visible(b.text()))))
            flags.add("UNREAD_CONDITION_POINTERS_NOT_VERIFIED");
        Preview admitted = new Preview(p.policyVersion(), p.parserVersion(), p.documentId(), p.versionId(), p.sourceRef(), p.sourceSha256(), p.repoRoleHint(),
                p.blocks(), List.copyOf(bodyPackets), List.copyOf(pointerPackets), review, List.copyOf(flags));
        Projection projection = new Projection(POLICY, new Versions(p.parserVersion(),p.policyVersion(),ReadmeModelReview.POLICY,ReadmeModelReview.PROMPT,ReadmeModelReview.SCHEMA,modelIdentity,runId),
                List.copyOf(assessed.values()), List.copyOf(knowledge), List.copyOf(navigation), List.copyOf(pointers), review);
        verify(source, admitted, projection);
        return new Output(admitted, projection);
    }
    private static boolean bodyContext(Assessment a) { return a.disposition() == Disposition.CONTEXT_ONLY || a.disposition() == Disposition.KNOWLEDGE_CANDIDATE; }
    private static Assessment deferred(Assessment a, String reason) {
        return new Assessment(a.blockId(), a.proposedDecision(), Decision.REVIEW, a.kind(), a.applicability(), a.scopes(), a.evidenceBlockIds(), a.dependencyBlockIds(), Disposition.REVIEW_QUEUE, reason);
    }
    private static Packet packet(Packet raw, List<String> ids, List<String> refs, String disposition) {
        return new Packet(raw.packetId() + ":admitted:" + sha256(String.join("|", ids)), raw.documentId(), raw.versionId(), raw.sourceRef(), raw.sourceSha256(), List.copyOf(ids), List.copyOf(refs), disposition);
    }
    private static <T> List<T> concat(List<T> a, List<T> b) { var result = new ArrayList<T>(a); result.addAll(b); return result; }
    public static void verify(Source source, Preview preview, Projection projection) {
        ReadmeKnowledgeFilter.verify(source, preview);
        Map<String,Assessment> metadata = new LinkedHashMap<>(); projection.assessments().forEach(a -> metadata.put(a.blockId(), a));
        if (metadata.size() != preview.blocks().size()) throw new IllegalArgumentException("Incomplete candidate metadata");
        for (Assessment a : metadata.values()) if (!a.evidenceBlockIds().contains(a.blockId()) || a.evidenceBlockIds().stream().anyMatch(id -> !metadata.containsKey(id)))
            throw new IllegalArgumentException("Invalid assessment evidence");
        if (!projection.knowledgeCandidates().stream().map(Candidate::packet).toList().equals(preview.knowledgeCandidates())
                || !projection.pointerCandidates().stream().map(Candidate::packet).toList().equals(preview.pointerCandidates()))
            throw new IllegalArgumentException("Candidate metadata mismatch");
        for (Candidate c : concat(projection.knowledgeCandidates(),projection.pointerCandidates())) {
            Set<String> expected = new HashSet<>(concat(c.packet().primaryBlockIds(),c.packet().contextBlockIds()));
            if (!expected.equals(new HashSet<>(c.evidence().stream().map(Assessment::blockId).toList()))
                    || c.evidence().stream().anyMatch(a->!a.equals(metadata.get(a.blockId())))) throw new IllegalArgumentException("Stale candidate metadata");
            if (c.packet().primaryBlockIds().stream().anyMatch(id->!metadata.get(id).scopes().equals(c.subjectScopes())))
                throw new IllegalArgumentException("Mixed candidate subject scopes");
        }
        for (Candidate candidate : projection.knowledgeCandidates()) for (Assessment a : candidate.evidence()) {
            if (!bodyContext(a) || a.kind()==Kind.SOURCE_POINTER || a.disposition() != Disposition.CONTEXT_ONLY && (a.applicability() != Applicability.IN_SCOPE || a.scopes().isEmpty()))
                throw new IllegalArgumentException("Unapproved recommendation context");
        }
        for (Packet packet : preview.knowledgeCandidates()) for (String id : concat(packet.primaryBlockIds(), packet.contextBlockIds()))
            if (!bodyContext(metadata.get(id))) throw new IllegalArgumentException("Recommendation candidate leak");
        for (Packet packet : preview.pointerCandidates()) if (!packet.contextBlockIds().isEmpty()) throw new IllegalArgumentException("Pointer carries body context");
    }
}
