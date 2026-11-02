package com.orangejd.server.ragb.ingest;

import java.util.*;
import java.util.regex.Pattern;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;

/** V5 entry: preserve independently approved facts; unresolved source is never promoted. */
public final class ReadmeConservativePolicy {
    public static final String POLICY="README_CONSERVATIVE_ADMISSION_V5";
    public record PendingCondition(String blockId,String kind,List<Scope> scopes,String status,String affectedConclusion,String reason){}
    public record Change(String blockId,String before,String after,String reason){}
    public record Result(Output output,List<PendingCondition> conditions,List<Change> changes){}
    private static final Set<String> PROPAGATED=Set.of("DEPENDENCY_NOT_ADMITTED","REQUIRED_CONTEXT_NOT_ADMITTED","NO_BUDGETED_KNOWLEDGE_PACKET");
    private static boolean matches(String pattern,String text){return Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text).find();}
    private static boolean body(Assessment a){return a!=null&&(a.disposition()==Disposition.CONTEXT_ONLY||a.disposition()==Disposition.KNOWLEDGE_CANDIDATE);}
    private static boolean licence(Block b){return matches("licen[cs]e|copyright|许可证|许可协议|版权",ReadmeSourceSemantics.visible(b.text()));}
    private static Assessment update(Assessment a,Kind kind,Applicability app,List<Scope> scopes,Disposition disposition,String reason,List<String> dependencies){
        return new Assessment(a.blockId(),a.proposedDecision(),disposition==Disposition.REVIEW_QUEUE?Decision.REVIEW:Decision.KEEP,kind,app,scopes,a.evidenceBlockIds(),dependencies,disposition,reason);
    }
    private static boolean explicitDeployment(Block b){
        return b.kind().equals("PROSE")&&!ReadmeSourceSemantics.imageOnly(b.text())&&!ReadmeSourceSemantics.literalNavigation(b.text())
            &&matches("\\binstall\\b[^.\\n]{0,100}\\b(?:on your (?:machine|server)|locally)\\b|可(?:在)?本机安装|本地安装",ReadmeSourceSemantics.visible(b.text()));
    }
    private static Map<String,List<String>> dependencies(List<Block> blocks){
        Map<String,LinkedHashSet<String>> edges=new LinkedHashMap<>();ReadmeKnowledgeFilter.dependencies(blocks).forEach((k,v)->edges.put(k,new LinkedHashSet<>(v)));
        Map<String,Block> byId=new HashMap<>();blocks.forEach(b->byId.put(b.blockId(),b));
        // An explicit installation statement is self-contained; a list-count introduction is not a prerequisite.
        for(var block:blocks)if(explicitDeployment(block))edges.get(block.blockId()).removeIf(id->ReadmeSourceSemantics.guideLeadIn(byId.get(id)));
        // An explicitly alternative example does not require executing the preceding example.
        for(int i=0;i<blocks.size();i++){
            Block lead=blocks.get(i);
            if(!lead.kind().equals("PROSE")||!matches("^(?:或(?:者|使用)?|or\\b|alternatively\\b)",ReadmeSourceSemantics.visible(lead.text())))continue;
            Block previous=null,next=null;
            for(int j=i-1;j>=0;j--){Block b=blocks.get(j);if(!b.sectionPath().equals(lead.sectionPath()))break;if(b.kind().equals("SPACE"))continue;if(ReadmeSourceSemantics.codeLike(b))previous=b;break;}
            for(int j=i+1;j<blocks.size();j++){Block b=blocks.get(j);if(!b.sectionPath().equals(lead.sectionPath()))break;if(b.kind().equals("SPACE"))continue;if(ReadmeSourceSemantics.codeLike(b))next=b;break;}
            if(previous!=null&&next!=null){edges.get(previous.blockId()).remove(next.blockId());edges.get(next.blockId()).remove(previous.blockId());}
        }
        Map<String,List<String>> result=new LinkedHashMap<>();edges.forEach((k,v)->result.put(k,List.copyOf(v)));return result;
    }
    private static boolean reaches(String subject,String target,Map<String,List<String>> edges){
        Set<String> seen=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>(List.of(subject));
        while(!queue.isEmpty()){String id=queue.removeFirst();if(!seen.add(id))continue;if(id.equals(target))return true;queue.addAll(edges.getOrDefault(id,List.of()));}return false;
    }
    private static boolean applies(Block guard,Block subject,Assessment g,Assessment s,List<Block> blocks,Map<String,List<String>> deps){
        if(!ReadmeSourceSemantics.applies(guard,subject))return false;
        if(g.scopes().contains(Scope.RELATED_PRODUCT)&&!s.scopes().contains(Scope.RELATED_PRODUCT))return false;
        String text=ReadmeSourceSemantics.visible(guard.text());
        // Licence conclusions remain separately visible. Explicit runtime/use restrictions still bind.
        if(licence(guard)&&!licence(subject)&&!matches("not (?:for|suitable for) production|非生产|不得|禁止|仅限|\\b(?:must|requires?|only|prohibited|forbidden)\\b",text))return false;
        if(guard.text().stripTrailing().matches("(?s).*[:：]$")&&matches("requires?|需要|必须",text)){
            int at=blocks.indexOf(guard);
            for(int j=at+1;j<blocks.size();j++){Block next=blocks.get(j);if(next.kind().equals("SPACE"))continue;
                if(next.sectionPath().equals(guard.sectionPath())&&ReadmeSourceSemantics.codeLike(next))return reaches(subject.blockId(),guard.blockId(),deps);
                break;
            }
        }
        return true;
    }
    public Result prepare(Source source,ReadmeModelReview.Result classified){
        var base=new ReadmeRecommendationProjection().project(source,classified.preview(),classified.selections(),classified.recommendation().versions().modelIdentity(),classified.recommendation().versions().runId());
        var p=classified.preview();Map<String,Block> blocks=new LinkedHashMap<>();p.blocks().forEach(b->blocks.put(b.blockId(),b));
        var deps=dependencies(p.blocks());Map<String,Assessment> assessed=new LinkedHashMap<>();Map<String,Assessment> before=new LinkedHashMap<>();
        for(var a:base.projection().assessments()){
            before.put(a.blockId(),a);Block b=blocks.get(a.blockId());var current=a;
            if(b.decision()==Decision.KEEP&&a.proposedDecision()==Decision.KEEP){
                if(PROPAGATED.contains(a.reason())&&a.applicability()==Applicability.IN_SCOPE&&a.kind()!=Kind.OTHER&&a.kind()!=Kind.SOURCE_POINTER&&!a.scopes().isEmpty())
                    current=update(a,a.kind(),a.applicability(),a.scopes(),Disposition.KNOWLEDGE_CANDIDATE,"V5_RECHECKED_DEPENDENCIES",deps.get(a.blockId()));
                if(explicitDeployment(b)&&a.disposition()==Disposition.POINTER_METADATA_ONLY)
                    current=update(a,Kind.USAGE,Applicability.IN_SCOPE,List.of(Scope.PLATFORM_SPECIFIC),Disposition.KNOWLEDGE_CANDIDATE,"V5_EXPLICIT_DEPLOYMENT_OPTIONS",deps.get(a.blockId()));
            }
            current=new Assessment(current.blockId(),current.proposedDecision(),current.effectiveDecision(),current.kind(),current.applicability(),current.scopes(),current.evidenceBlockIds(),deps.get(a.blockId()),current.disposition(),current.reason());
            assessed.put(a.blockId(),current);
        }
        List<Block> guards=p.blocks().stream().filter(ReadmeSourceSemantics::condition).toList();
        boolean changed;
        do{changed=false;for(var b:p.blocks()){
            var a=assessed.get(b.blockId());if(a.disposition()!=Disposition.KNOWLEDGE_CANDIDATE)continue;
            boolean missing=deps.get(b.blockId()).stream().anyMatch(id->!body(assessed.get(id)))
                ||guards.stream().filter(g->applies(g,b,assessed.get(g.blockId()),a,p.blocks(),deps)).anyMatch(g->!body(assessed.get(g.blockId()))&&assessed.get(g.blockId()).applicability()!=Applicability.OUT_OF_SCOPE);
            if(missing){assessed.put(b.blockId(),update(a,a.kind(),a.applicability(),a.scopes(),Disposition.REVIEW_QUEUE,"V5_REQUIRED_CONTEXT_UNRESOLVED",a.dependencyBlockIds()));changed=true;}
        }}while(changed);
        List<Candidate> candidates=new ArrayList<>();List<Packet> packets=new ArrayList<>();
        for(var b:p.blocks()){
            var a=assessed.get(b.blockId());if(a.disposition()!=Disposition.KNOWLEDGE_CANDIDATE)continue;
            Set<String> refs=new LinkedHashSet<>();ReadmeKnowledgeFilter.addDependencies(b.blockId(),deps,refs);
            for(var g:guards)if(applies(g,b,assessed.get(g.blockId()),a,p.blocks(),deps)&&assessed.get(g.blockId()).applicability()!=Applicability.OUT_OF_SCOPE){refs.add(g.blockId());ReadmeKnowledgeFilter.addDependencies(g.blockId(),deps,refs);}
            refs.remove(b.blockId());
            if(refs.stream().anyMatch(id->!body(assessed.get(id)))||b.text().length()+refs.stream().mapToInt(id->blocks.get(id).text().length()).sum()>64000){
                assessed.put(b.blockId(),update(a,a.kind(),a.applicability(),a.scopes(),Disposition.REVIEW_QUEUE,"V5_PACKET_CONTEXT_UNRESOLVED_OR_OVERSIZED",a.dependencyBlockIds()));continue;
            }
            List<String> ordered=refs.stream().sorted(Comparator.comparingInt(id->blocks.get(id).startUtf16())).toList();
            var packet=new Packet(p.sourceSha256()+":v5:"+b.blockId(),source.documentId(),source.versionId(),source.sourceRef(),p.sourceSha256(),List.of(b.blockId()),ordered,"KNOWLEDGE_CANDIDATE");
            var evidence=new ArrayList<Assessment>();evidence.add(a);ordered.forEach(id->evidence.add(assessed.get(id)));packets.add(packet);candidates.add(new Candidate(packet,a.scopes(),evidence));
        }
        // An earlier seed may reference a packet that was deferred for size. Recheck all evidence.
        candidates.removeIf(c->c.evidence().stream().anyMatch(a->!body(assessed.get(a.blockId()))));packets=candidates.stream().map(Candidate::packet).toList();
        List<String> review=assessed.values().stream().filter(a->a.disposition()==Disposition.REVIEW_QUEUE).map(Assessment::blockId).toList();
        var flags=new ArrayList<>(p.qualityFlags().stream().filter(f->!f.equals("NO_APPROVED_BODY_PACKET")&&!f.equals("HAS_REVIEW_BLOCKS")).toList());flags.add("RECOMMENDATION_ADMISSION:"+POLICY);
        if(!review.isEmpty())flags.add("HAS_REVIEW_BLOCKS");if(packets.isEmpty())flags.add("NO_APPROVED_BODY_PACKET");
        List<PendingCondition> conditions=new ArrayList<>();
        for(var b:guards)if(licence(b)){var a=assessed.get(b.blockId());conditions.add(new PendingCondition(b.blockId(),"LICENSE",a.scopes(),body(a)?"SOURCE_STATEMENT_ONLY":"UNRESOLVED","LICENSE_AND_COMMERCIAL_ELIGIBILITY",a.reason()));}
        if(conditions.stream().anyMatch(c->c.status().equals("UNRESOLVED")))flags.add("LICENSE_ELIGIBILITY_UNRESOLVED_NOT_A_CAPABILITY_VETO");
        // Keep only navigation that remains navigation under V5; declared facts are not duplicated as pointers.
        var navigation=base.projection().pointerCandidates().stream().filter(c->assessed.get(c.packet().primaryBlockIds().getFirst()).disposition()==Disposition.POINTER_METADATA_ONLY).toList();
        var pointers=base.projection().pointers();
        var preview=new Preview(p.policyVersion(),p.parserVersion(),p.documentId(),p.versionId(),p.sourceRef(),p.sourceSha256(),p.repoRoleHint(),p.blocks(),packets,navigation.stream().map(Candidate::packet).toList(),review,List.copyOf(flags));
        var projection=new Projection(POLICY,base.projection().versions(),List.copyOf(assessed.values()),List.copyOf(candidates),navigation,pointers,review);
        ReadmeRecommendationProjection.verify(source,preview,projection);
        var changes=assessed.values().stream().filter(a->!a.equals(before.get(a.blockId()))).map(a->new Change(a.blockId(),before.get(a.blockId()).disposition().name(),a.disposition().name(),a.reason())).toList();
        return new Result(new Output(preview,projection),List.copyOf(conditions),changes);
    }
}
