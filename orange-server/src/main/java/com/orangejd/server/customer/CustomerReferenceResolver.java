package com.orangejd.server.customer;

import java.util.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Executed before intent extraction. Only server-owned list identities may be resolved. */
public final class CustomerReferenceResolver {
    private CustomerReferenceResolver() {}
    public record Resolution(String rawMessage,String resolvedQuery,List<Integer> positions,List<Map<String,Object>> references,boolean ambiguous) {}
    public static Resolution resolve(String raw,Map<String,Object> state,Map<String,Object> binding) {
        var task=task(state);String listId=Objects.toString(binding.get("listId"),Objects.toString(task.get("currentListId"),""));
        var list=rows(task.get("lists")).stream().filter(l->listId.equals(l.get("id"))).findFirst().orElseGet(LinkedHashMap::new);
        var candidates=rows(list.get("projects"));List<Integer> positions=CustomerTurnPlan.explicitPositions(raw);
        boolean pronoun=raw.matches("(?s).*(它|这个项目|该项目|刚才那个).*"),ambiguous=false;
        if(positions.isEmpty()&&raw.matches("(?s).*(这两个|这2个|两个都).*")) {
            if(candidates.size()==2)positions=List.of(1,2);else ambiguous=true;
        }
        if(positions.isEmpty()&&!pronoun) {
            var named=new ArrayList<Integer>();for(int i=0;i<candidates.size();i++){String name=Objects.toString(candidates.get(i).get("name"),"");if(name.length()>1&&raw.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))named.add(i+1);}positions=named;
        }
        // Historical ordinals require an explicit list, not whichever list happens to be current.
        if(raw.matches("(?s).*(上次|上一批|之前|最早).*第[一二三123]个.*")&&!binding.containsKey("listId"))ambiguous=true;
        if(positions.isEmpty()&&pronoun) {
            var focus=map(state.get("focusedProject"));
            for(int i=0;i<candidates.size();i++)if(same(focus,candidates.get(i)))positions=List.of(i+1);
            if(positions.isEmpty()&&candidates.size()==1)positions=List.of(1);
            if(positions.isEmpty())ambiguous=true;
        }
        if(positions.stream().anyMatch(p->p<1||p>candidates.size()))ambiguous=true;
        List<Map<String,Object>> references=new ArrayList<>();
        if(!ambiguous)for(int p:positions)references.add(Map.of("kind","PROJECT","listId",listId,"position",p,"project",candidates.get(p-1),"resolution","RESOLVED","method",binding.containsKey("listId")?"UI":"POSITION","sourceRunId",list.getOrDefault("runId","")));
        String resolved=raw;
        if(!references.isEmpty())resolved=raw+"\n指代对象（仅用于理解，不是新要求）："+String.join("、",references.stream().map(r->Objects.toString(map(r.get("project")).get("name"),"项目")).toList());
        return new Resolution(raw,resolved,ambiguous?List.of():positions,references,ambiguous);
    }
    private static boolean same(Map<String,Object> a,Map<String,Object> b){return !a.isEmpty()&&Objects.toString(a.get("type"),"").equals(Objects.toString(b.get("type"),""))&&Objects.toString(a.get("id"),"").equals(Objects.toString(b.get("id"),""));}
}
