package com.orangejd.server.ragb.ingest;

import java.util.*;
import java.util.regex.*;
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;

/** Builds source dependency edges before knowledge admission. */
final class ReadmeDependencyGraph {
    static Map<String, List<String>> dependencies(List<Block> blocks) {
        Map<String, Block> all = byId(blocks);
        Map<String,List<String>> panels=ReadmeSourceSemantics.detailContexts(blocks);
        Set<String> codeFragments=new HashSet<>();ReadmeSourceSemantics.indentedSpans(blocks).forEach(s->s.blocks().forEach(b->codeFragments.add(b.blockId())));
        Map<String, LinkedHashSet<String>> edges = new LinkedHashMap<>();
        for (Block b : blocks) {
            var refs = new LinkedHashSet<>(b.headingIds());
            if (b.knowledgeTypeHints().contains("SOURCE_POINTER"))
                refs.removeIf(id -> all.get(id).decision() == Decision.DROP);
            edges.put(b.blockId(), refs);
            if(ReadmeSourceSemantics.codeLike(b)||codeFragments.contains(b.blockId())){
                panels.get(b.blockId()).stream().filter(id->!ReadmeSourceSemantics.layoutOnly(all.get(id))).forEach(refs::add);refs.remove(b.blockId());
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block code = blocks.get(i);
            if (!ReadmeSourceSemantics.codeLike(code)) continue;
            int included = 0;
            for (int j = i - 1; j >= 0 && included < 2; j--) {
                Block before = blocks.get(j);
                if (!before.sectionPath().equals(code.sectionPath())) break;
                if (before.kind().equals("SPACE")) continue;
                if(ReadmeSourceSemantics.layoutOnly(before)&&!codeFragments.contains(before.blockId()))break;
                if(ReadmeSourceSemantics.guideLeadIn(before))break;
                // Badges, images, layout and headings are boundaries, not code prerequisites.
                if (!Set.of("PROSE", "CODE", "TABLE", "HTML_TABLE", "INDENTED").contains(before.kind())) break;
                edges.get(code.blockId()).add(before.blockId());
                // Immediate explanatory prose belongs to this example even without a trailing colon.
                // Cross-section/shared prerequisites added below remain one-way dependencies.
                if (ReadmeSourceSemantics.codeLike(before) || included == 0 && Set.of("PROSE", "INDENTED").contains(before.kind()))
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
                Matcher manager=Pattern.compile("(?m)^\\s*(pnpm|yarn)\\s+(install|build|start|dev|run|test)\\b").matcher(subject.text());
                if(manager.find()){
                    String required=manager.group(2).equals("build")?"install":Set.of("start","dev","run","test").contains(manager.group(2))?"build":null;
                    if(required!=null)for(int j=i-1;j>=0;j--){Block prior=blocks.get(j);
                        if(subject.headingIds().containsAll(prior.headingIds())&&panels.get(subject.blockId()).containsAll(panels.get(prior.blockId()))
                                &&has("(?m)^\\s*"+manager.group(1)+"\\s+"+required+"\\b",prior.text())){
                            edges.get(subject.blockId()).add(prior.blockId());break;
                        }
                    }
                    for(int j=i-1;j>=0;j--){
                        Block prerequisite=blocks.get(j);
                        if(!Collections.disjoint(subject.headingIds(),prerequisite.headingIds())
                                &&has("(?m)^\\s*(?:npm\\s+(?:i|install)\\s+.*\\b"+manager.group(1)+"\\b|corepack\\s+(?:enable|prepare)\\b)",prerequisite.text())){
                            edges.get(subject.blockId()).add(prerequisite.blockId());break;
                        }
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
    static boolean isLeadIn(Block block) {
        return Set.of("PROSE","INDENTED").contains(block.kind()) && !ReadmeSourceSemantics.codeLike(block)
                && has("[:：](?:\\*\\*)?\\s*$",ReadmeSourceSemantics.visible(block.text()));
    }
}
