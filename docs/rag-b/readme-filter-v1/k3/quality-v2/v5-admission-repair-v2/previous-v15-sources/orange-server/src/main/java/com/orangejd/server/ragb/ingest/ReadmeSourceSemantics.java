package com.orangejd.server.ragb.ingest;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;

/** Local markup/condition inspection only. Never fetches URLs or rewrites stored source blocks. */
final class ReadmeSourceSemantics {
    private ReadmeSourceSemantics(){}
    private static final Pattern MD_LINK=Pattern.compile("(?<!!)\\[([^]\\r\\n]+)]\\(([^\\s()]+)\\)");
    private static final Pattern IMAGE=Pattern.compile("!\\[[^]\\r\\n]*]\\([^\\r\\n]*?\\)");
    private static final Pattern ANCHOR=Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern HREF=Pattern.compile("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static boolean has(String pattern,String text){return Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text).find();}

    static String visible(String raw){
        String text=IMAGE.matcher(raw).replaceAll("");
        text=MD_LINK.matcher(text).replaceAll("$1");
        var html=Jsoup.parseBodyFragment(text);
        html.select("img,picture,svg,script,style").remove();
        return html.body().text().replaceAll("https?://\\S+","").strip();
    }
    static boolean hasImage(String raw){return has("!\\[|<img\\b|<picture\\b|<svg\\b",raw);}
    static boolean imageOnly(String raw){
        if(!hasImage(raw))return false;
        String text=visible(raw).replaceAll("[\\s|*·—-]","");
        // An image's Markdown outer link can remain after the image itself was removed.
        text=text.replaceAll("\\[\\]\\([^)]*\\)","");
        return text.isBlank();
    }
    static boolean safeHref(String href){
        String decoded=Parser.unescapeEntities(href,false);
        if(decoded.isBlank()||decoded.startsWith("//")||decoded.contains("\\")||decoded.codePoints().anyMatch(c->c<=32||c==127))return false;
        try{
            var uri=URI.create(decoded);String scheme=uri.getScheme();
            return scheme==null || Set.of("http","https").contains(scheme.toLowerCase(Locale.ROOT)) && uri.getHost()!=null;
        }catch(IllegalArgumentException ex){return false;}
    }
    static List<ReadmeRecommendationProjection.Pointer> pointers(Block block){
        if(Set.of("CODE","SPACE","HEADING","FIGURE").contains(block.kind()))return List.of();
        var result=new ArrayList<ReadmeRecommendationProjection.Pointer>();
        Matcher md=MD_LINK.matcher(block.text());
        while(md.find())if(!md.group(1).contains("![")&&safeHref(md.group(2)))
            result.add(new ReadmeRecommendationProjection.Pointer(md.group(1),md.group(2),block.blockId(),pointerHints(block.text(),md.end())));
        Matcher html=ANCHOR.matcher(block.text());
        while(html.find()){
            Matcher attr=HREF.matcher(html.group(1));if(!attr.find()||hasImage(html.group(2)))continue;
            String href=attr.group(1)!=null?attr.group(1):attr.group(2)!=null?attr.group(2):attr.group(3);
            String label=visible(html.group(2));
            if(!label.isBlank()&&safeHref(href))result.add(new ReadmeRecommendationProjection.Pointer(label,href,block.blockId(),pointerHints(block.text(),html.end())));
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }
    private static List<String> pointerHints(String raw,int end){
        int newline=raw.indexOf('\n',end);String after=raw.substring(end,newline<0?raw.length():newline);
        Matcher hint=Pattern.compile("(?i)^\\s*(?:\\(\\s*)?\\*{0,2}(Recommended|Optional|推荐|可选)(?=\\*|\\)|\\s|\\||$)\\*{0,2}\\s*\\)?").matcher(after);
        return hint.find()?List.of(hint.group(1)):List.of();
    }
    static boolean literalNavigation(String raw){
        if(hasImage(raw))return false;
        String value=raw.strip().replaceAll("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)","");
        boolean link=MD_LINK.matcher(value).find()||ANCHOR.matcher(value).find();
        value=MD_LINK.matcher(value).replaceAll("");value=ANCHOR.matcher(value).replaceAll("");
        return link&&visible(value).replaceAll("[\\s|,;:·—-]","").isBlank();
    }
    static boolean navigationProse(Block block){
        if(pointers(block).isEmpty())return false;
        String text=visible(block.text());
        if(has("supports?|provides?|includes?|contains?|implements?|requires?|\\bmust\\b|may not|cannot|prohibit|forbid|permitted|allowed|unless|except|commercial|not for production|not supported|unsupported|no auth|支持|提供|包含|必须|需要|实现|不得|禁止|仅限|例外|限制|允许|商用|不支持|非生产|无认证|configure.{0,20}cache|配置.{0,20}缓存",text))return false;
        return has("^(?:please |请)?(?:see|visit|refer to|check out|查看|参阅|访问)",text)
                ||has("^(?:for (?:more )?(?:details|information)|有关.{0,40}(?:更多信息|更多详情)|关于.{0,40}(?:更多信息|详情)).{0,60}(?:refer|see|visit|参阅|查看|访问)",text)
                ||has("^(?:[-*+]\\s*)?(?:sign\\s?up|注册).{0,80}(?:cloud|云)",text)
                || has("发现.{0,12}bug.{0,30}(?:贡献|指南)|贡献指南.{0,30}(?:设置|最佳实践)",text);
    }
    static boolean originStory(Block block){
        return has("why.{0,25}(?:name|called)|name.{0,15}(?:origin|meaning)|命名|名称由来|名字由来|为什么.{0,12}(?:叫|名)",String.join(" ",block.sectionPath()))
                && has("name|called|名字|名称|命名",visible(block.text()));
    }
    static boolean condition(Block block){
        if(Set.of("SPACE","HEADING","CODE").contains(block.kind())||literalNavigation(block.text())||navigationProse(block)
                ||navigationTable(block)||imageOnly(block.text())||originStory(block))return false;
        String text=visible(block.text());
        return has("licen[cs]e|copyright|许可|版权|警告|不支持|不兼容|必须|仅限|建议|requires?|prerequisit|not (?:for|suitable for) production|no auth|非生产|并非为生产|\\b(?:only|must|GPU|RAM|CPU)\\b|[<>]=|\\d+\\s*(?:GiB|MiB|GB|MB)",text)||maintenance(text);
    }
    private static boolean maintenance(String text){return has("maintainers?.{0,20}wanted|development.{0,30}(?:moved|ended|stopped)|\\b(?:archived|deprecated)\\b|developer preview|急需维护者|开发已迁移|已归档|不稳定|维护至",text);}
    static boolean applies(Block guard,Block subject){
        if(!condition(guard))return false;
        String path=String.join(" ",guard.sectionPath());String raw=visible(guard.text());
        String other=String.join(" ",subject.sectionPath());
        if(has("enterprise|企业版",path)&&!has("enterprise|企业版",other))return false;
        if(has("community edition|社区版",path)&&!has("community edition|社区版",other))return false;
        if(guard.headingIds().size()<=1||maintenance(raw)||has("licen[cs]e|copyright|许可|版权|not (?:for|suitable for) production|非生产|并非为生产",raw))return true;
        if(has("^(?:.* )?(?:requirements?|prerequisites?|disclaimer|browser support|system requirements|环境要求|运行要求|前置条件|先决条件|免责声明)$",path))
            return subject.headingIds().containsAll(guard.headingIds().subList(0,guard.headingIds().size()-1));
        return subject.headingIds().containsAll(guard.headingIds());
    }
    static boolean layoutOnly(Block block){
        return !block.kind().equals("CODE")&&has("^\\s*(?:</(?:details|summary|div|p|span)>|<(?:details|div|p|span)>)\\s*$",block.text());
    }
    static boolean guideLeadIn(Block block){
        return block.kind().equals("PROSE")&&has("^(?:There (?:are|is) .{0,30}ways? to .{0,80}|(?:有|以下有).{0,30}(?:使用方式|方法|方式))[:：]$",visible(block.text()));
    }
    static boolean navigationTable(Block block){
        if(!block.kind().equals("TABLE"))return false;
        String[] lines=block.text().strip().split("\\R");if(lines.length<3||!has("documentation|links?|resources|文档|链接|指南",lines[0]))return false;
        for(int i=2;i<lines.length;i++){
            String row=IMAGE.matcher(lines[i]).replaceAll("");row=row.replaceAll("\\[\\]\\([^)]*\\)","");
            if(!MD_LINK.matcher(row).find()&&!ANCHOR.matcher(row).find())return false;
            row=MD_LINK.matcher(row).replaceAll("");row=ANCHOR.matcher(row).replaceAll("");
            row=visible(row).replaceAll("(?i)recommended|optional|推荐|可选","").replaceAll("[\\s|*()_—-]","");
            if(!row.isBlank())return false;
        }
        return !pointers(block).isEmpty();
    }
    static boolean mixedLicenseModes(Block block){
        if(navigationProse(block)||navigationTable(block))return false;
        Matcher links=MD_LINK.matcher(block.text());boolean enterprise=false,other=false;
        while(links.find())if(has("licen[cs]e|许可",links.group(1))){
            if(has("enterprise|企业",links.group(1)))enterprise=true;else other=true;
        }
        return enterprise&&other;
    }
    static boolean mixedProducts(Block block){
        Matcher links=MD_LINK.matcher(block.text());
        while(links.find()){
            String label=links.group(1);
            Matcher variant=Pattern.compile("(?i)^([\\p{L}][\\p{L}\\p{N}._-]{2,})\\s+(?:Lite|Cloud|Desktop|SDK)$").matcher(label);
            if(variant.matches()){
                String base=Pattern.quote(variant.group(1));
                String remaining=block.text().replaceAll("(?i)"+base+"\\s+(?:Lite|Cloud|Desktop|SDK)","");
                if(has("(?<![\\p{L}\\p{N}])"+base+"(?![\\p{L}\\p{N}])",remaining))return true;
            }
        }
        return false;
    }
    static boolean incompleteTail(String raw,boolean endOfSource){
        String text=visible(raw);
        return endOfSource&&!raw.endsWith("\n")&&has("(?:\\b(?:to|an?|create|using)\\b|使用|创建|一个).{0,80}\\b[a-z]{1,7}$",text)
                && !has("[.!?。！？:：;；]$",text);
    }
    static boolean codeLike(Block block){return block.kind().equals("CODE")||block.kind().equals("INDENTED")&&has("^\\s*(?:`{3,}|~{3,})",block.text());}
    /** Original HTML panel ownership, excluding literal code payloads. */
    static Map<String,List<String>> detailContexts(List<Block> blocks){
        Set<String> code=new HashSet<>();indentedSpans(blocks).forEach(s->s.blocks().forEach(b->code.add(b.blockId())));
        Map<String,List<String>> result=new LinkedHashMap<>();List<String> open=new ArrayList<>();
        for(Block b:blocks){
            result.put(b.blockId(),List.copyOf(open));if(b.kind().equals("CODE")||code.contains(b.blockId()))continue;
            Matcher tags=Pattern.compile("(?im)^\\s*(</?details\\b[^>]*>)").matcher(b.text());
            while(tags.find())if(tags.group(1).toLowerCase(Locale.ROOT).startsWith("</")){
                if(!open.isEmpty())open.removeLast();
            }else{open.add(b.blockId());result.put(b.blockId(),List.copyOf(open));}
        }
        return result;
    }
    record IndentedSpan(List<Block> blocks,boolean complete){}
    static List<IndentedSpan> indentedSpans(List<Block> blocks){
        var result=new ArrayList<IndentedSpan>();
        for(int i=0;i<blocks.size();i++){
            Block first=blocks.get(i);if(!first.kind().equals("INDENTED"))continue;
            Matcher opening=Pattern.compile("^\\s*(`{3,}|~{3,})[^\\r\\n]*\\r?\\n").matcher(first.text());
            if(!opening.find())continue;
            String fence=opening.group(1);String closing="(?m)^\\s*"+Pattern.quote(fence.substring(0,1))+"{"+fence.length()+",}\\s*$";
            var members=new ArrayList<Block>();boolean complete=false;int end=i;
            for(int j=i;j<blocks.size();j++){
                Block b=blocks.get(j);if(!b.sectionPath().equals(first.sectionPath())||!Set.of("INDENTED","SPACE").contains(b.kind()))break;
                if(b.kind().equals("SPACE"))continue;
                members.add(b);end=j;
                String scan=j==i?b.text().substring(opening.end()):b.text();
                if(has(closing,scan)){complete=true;break;}
            }
            result.add(new IndentedSpan(List.copyOf(members),complete));i=end;
        }
        return List.copyOf(result);
    }
    static boolean declaredListConflict(Block subject,List<Block> blocks){
        Matcher stated=Pattern.compile("(?i)(?:contains?|包含)\\s*(\\d{1,2})\\s*(?:(?:different|个不同的|个)\\s*)?(?:modules?|components?|packages?|模块|组件)").matcher(visible(subject.text()));
        if(!stated.find())return false;
        int expected=Integer.parseInt(stated.group(1));if(expected<1)return false;
        int found=0;
        for(int i=blocks.indexOf(subject)+1;i<blocks.size();i++){
            Block b=blocks.get(i);if(!b.sectionPath().equals(subject.sectionPath())||b.kind().equals("HEADING"))break;
            if(b.kind().equals("SPACE"))continue;
            Matcher items=Pattern.compile("(?m)^[-*+]\\s+\\S").matcher(b.text());int count=0;while(items.find())count++;
            if(count==0)break;found+=count;
        }
        return found>0&&expected!=found;
    }
}
