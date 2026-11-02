package com.orangejd.server.ragb.ingest;

import java.util.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeChunker.Span;

/** Pack approved prose using the existing structural splitter; map every cited piece to original source. */
public final class ReadmeApprovedStructuralChunker {
    public static final String POLICY="APPROVED_STRUCT_WITH_SOURCE_HEADINGS_V1";
    public record Draft(int index,String text,int tokens,List<Span> sourceSpans){}
    public record Deferred(int index,String reason,List<Span> sourceSpans){}
    public record Preview(String policyVersion,List<Draft> chunks,List<Deferred> deferred){}
    private record Mapping(int from,int to,int sourceStart){}
    private final RagBTokenChunker tokenizer=new RagBTokenChunker();
    public Preview split(Source source,List<Block> blocks,List<Span> approved){
        Map<String,Block> byId=new LinkedHashMap<>();blocks.forEach(b->byId.put(b.blockId(),b));
        Map<String,List<ReadmeModelReview.Scope>> scopes=new HashMap<>();
        for(var s:approved){
            Block b=Objects.requireNonNull(byId.get(s.blockId()));
            if(b.decision()!=Decision.KEEP||s.startUtf16()<b.startUtf16()||s.endUtf16()>b.endUtf16()||!source.text().substring(s.startUtf16(),s.endUtf16()).equals(s.text())||!sha256(s.text()).equals(s.sha256()))throw new IllegalArgumentException("Unapproved structural source");
            scopes.putIfAbsent(s.blockId(),s.scopes());
        }
        List<int[]> ranges=new ArrayList<>();
        for(var s:approved.stream().sorted(Comparator.comparingInt(Span::startUtf16)).toList()){
            if(!ranges.isEmpty()&&s.startUtf16()<=ranges.getLast()[1])ranges.getLast()[1]=Math.max(ranges.getLast()[1],s.endUtf16());
            else ranges.add(new int[]{s.startUtf16(),s.endUtf16()});
        }
        StringBuilder virtual=new StringBuilder();List<Mapping> mappings=new ArrayList<>();
        for(var range:ranges){
            if(!virtual.isEmpty())virtual.append("\n\n"); // Derived separators never receive a source location.
            int at=virtual.length();virtual.append(source.text(),range[0],range[1]);mappings.add(new Mapping(at,virtual.length(),range[0]));
        }
        List<Draft> chunks=new ArrayList<>();List<Deferred> deferred=new ArrayList<>();
        for(var raw:tokenizer.split(virtual.toString(),4096)){
            List<Span> body=new ArrayList<>();
            for(var m:mappings){int a=Math.max(raw.startOffset(),m.from()),b=Math.min(raw.endOffset(),m.to());if(a>=b)continue;
                int sourceA=m.sourceStart()+a-m.from(),sourceB=m.sourceStart()+b-m.from();
                for(var block:blocks){int x=Math.max(sourceA,block.startUtf16()),y=Math.min(sourceB,block.endUtf16());if(x<y)body.add(span(source,block,x,y,scopes.get(block.blockId())));}
            }
            if(body.stream().noneMatch(s->!s.blockKind().equals("HEADING")))continue;
            Set<String> headings=new LinkedHashSet<>();for(var s:body)headings.addAll(byId.get(s.blockId()).headingIds());
            List<Span> prefix=new ArrayList<>();
            for(String id:headings){Block b=byId.get(id);if(!scopes.containsKey(id)||body.stream().anyMatch(s->s.blockId().equals(id)&&s.startUtf16()<=b.startUtf16()&&s.endUtf16()>=b.endUtf16()))continue;prefix.add(span(source,b,b.startUtf16(),b.endUtf16(),scopes.get(id)));}
            prefix.sort(Comparator.comparingInt(Span::startUtf16));
            String text=String.join("\n",prefix.stream().map(Span::text).toList())+(prefix.isEmpty()?"":"\n")+raw.content();
            List<Span> refs=new ArrayList<>(prefix);refs.addAll(body);int tokens=tokenizer.tokens(text);
            if(tokens>768){deferred.add(new Deferred(raw.index(),"SOURCE_CONTEXT_EXCEEDS_768",List.copyOf(refs)));continue;}
            chunks.add(new Draft(chunks.size(),text,tokens,List.copyOf(refs)));
        }
        return new Preview(POLICY,List.copyOf(chunks),List.copyOf(deferred));
    }
    private Span span(Source source,Block b,int a,int end,List<ReadmeModelReview.Scope> scopes){
        if(scopes==null)throw new IllegalArgumentException("Nonapproved source leaked into structural output");
        String text=source.text().substring(a,end);return new Span(b.blockId(),a,end,text,sha256(text),b.kind().equals("HEADING")?"HEADING":"PRIMARY",scopes,b.kind());
    }
}
