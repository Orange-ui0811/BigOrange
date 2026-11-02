package com.orangejd.server.ragb.ingest;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.util.regex.*;

/** M3 tokenizer budgets, raw contiguous offsets, fences and chapter-local overlap. */
public final class RagBTokenChunker {
    public static final String POLICY = "STRUCTURE_M3_512_768_64_V1";
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})");
    private static final Pattern ATX = Pattern.compile("^ {0,3}(#{1,6})\\s+(.+)$");
    private static final Pattern SENTENCE = Pattern.compile("[。！？.!?](?:\\s+|(?=[^\\x00-\\x7f]))|\\n");
    private static class Holder {
        static final HuggingFaceTokenizer TOKENIZER = load();
        static HuggingFaceTokenizer load() {
            try (var stream = new ClassPathResource("rag-b/tokenizers/bge-m3.json").getInputStream()) {
                return HuggingFaceTokenizer.newInstance(stream, Map.of("addSpecialTokens", "false", "truncation", "false"));
            } catch (Exception ex) { throw new IllegalStateException("无法加载 BGE-M3 分词器", ex); }
        }
    }
    public int tokens(String text) { return Holder.TOKENIZER.encode(text, false, false).getIds().length; }

    private record Span(int start, int end, String path) {}
    private record Line(int start, int end, String text) {}
    private List<Line> lines(String text, int a, int b) {
        List<Line> result = new ArrayList<>();
        for (int at=a; at<b;) {
            int next=text.indexOf('\n', at); next=next<0 || next>=b ? b : next+1;
            result.add(new Line(at,next,text.substring(at,next).stripTrailing())); at=next;
        }
        return result;
    }
    private List<Span> sections(String text) {
        List<Line> lines=lines(text,0,text.length());
        List<Integer> starts=new ArrayList<>(List.of(0));
        List<String> paths=new ArrayList<>(); paths.add(null);
        String[] path=new String[6]; String fence=null;
        for (int i=0;i<lines.size();i++) {
            Line line=lines.get(i); Matcher f=FENCE.matcher(line.text());
            if(f.find()) { String mark=f.group(1); if(fence==null) fence=mark;
                else if(mark.charAt(0)==fence.charAt(0) && mark.length()>=fence.length()) fence=null;
                continue; }
            if(fence!=null) continue;
            Matcher h=ATX.matcher(line.text()); int level=0; String title=null;
            if(h.matches()) { level=h.group(1).length(); title=h.group(2).replaceAll("\\s+#+\\s*$","").trim(); }
            else if(!line.text().isBlank() && !line.text().matches("^\\s*[-*+].*") && i+1<lines.size() && lines.get(i+1).text().matches("^ {0,3}(=+|-+)\\s*$")) {
                level=lines.get(i+1).text().stripLeading().startsWith("=") ? 1 : 2; title=line.text().trim(); i++;
            }
            if(level==0) continue;
            path[level-1]=title; for(int j=level;j<6;j++) path[j]=null;
            String label=String.join(" > ",Arrays.stream(path).filter(Objects::nonNull).toList());
            if(line.start()==starts.getLast()) paths.set(paths.size()-1,label);
            else { starts.add(line.start()); paths.add(label); }
        }
        List<Span> result=new ArrayList<>();
        for(int i=0;i<starts.size();i++) result.add(new Span(starts.get(i),i+1<starts.size()?starts.get(i+1):text.length(),paths.get(i)));
        return result;
    }
    private NavigableSet<Integer> boundaries(String text, Span section) {
        TreeSet<Integer> points=new TreeSet<>(List.of(section.start(),section.end()));
        String fence=null;
        for(Line line:lines(text,section.start(),section.end())) {
            Matcher f=FENCE.matcher(line.text());
            if(f.find()) { String mark=f.group(1);
                if(fence==null) { points.add(line.start()); fence=mark; }
                else if(mark.charAt(0)==fence.charAt(0) && mark.length()>=fence.length()) { points.add(line.end()); fence=null; }
            } else if(fence==null && line.text().isBlank()) { points.add(line.end()); }
        }
        List<Integer> atoms=new ArrayList<>(points);
        for(int i=0;i+1<atoms.size();i++) {
            int a=atoms.get(i),b=atoms.get(i+1);
            if(tokens(text.substring(a,b))>768) { Matcher m=SENTENCE.matcher(text.substring(a,b));
                while(m.find()) points.add(a+m.end()); }
        }
        return points;
    }
    public List<RagBChunker.ChunkDraft> split(String text, int maxChunks) {
        List<RagBChunker.ChunkDraft> result=new ArrayList<>();
        for(Span section:sections(text)) {
            NavigableSet<Integer> boundaries=boundaries(text,section);
            int pos=section.start();
            while(pos<section.end()) {
                int end=section.end();
                if(tokens(text.substring(pos,end))>512) {
                    int lo=pos+1,hi=end;
                    while(lo<hi) { int mid=(lo+hi+1)/2;
                        if(tokens(text.substring(pos,mid))<=512) lo=mid; else hi=mid-1; }
                    Integer natural=boundaries.floor(lo); end=natural!=null && natural>pos ? natural : lo;
                    Integer following=boundaries.higher(lo);
                    if(following!=null && tokens(text.substring(pos,following))<=768) end=following;
                }
                if(end<text.length() && end>pos && Character.isHighSurrogate(text.charAt(end-1))) end--;
                if(end<=pos || tokens(text.substring(pos,end))>768) throw new IllegalStateException("结构分块无法满足 Token 预算");
                if(result.size()>=maxChunks) throw new IllegalArgumentException("文档分块数超过上限，未创建不完整索引");
                result.add(new RagBChunker.ChunkDraft(result.size(),section.path(),pos,end,text.substring(pos,end)));
                if(end==section.end()) break;
                int next=end;
                for(int p:boundaries.subSet(pos,false,end,false)) if(tokens(text.substring(p,end))<=64) {next=p;break;}
                pos=next;
            }
        }
        int covered=0;
        for(var chunk:result) {if(chunk.startOffset()>covered) throw new IllegalStateException("结构分块有覆盖缺口"); covered=Math.max(covered,chunk.endOffset());}
        if(covered!=text.length()) throw new IllegalStateException("结构分块未完整覆盖文档");
        return List.copyOf(result);
    }
}
