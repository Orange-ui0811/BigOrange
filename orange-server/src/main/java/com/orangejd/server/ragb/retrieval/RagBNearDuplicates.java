package com.orangejd.server.ragb.retrieval;

import java.util.*;

/** Only discard highly redundant evidence within the same source. */
public final class RagBNearDuplicates {
    private RagBNearDuplicates() {}
    public static List<RagBSearchHit> distinct(List<RagBSearchHit> ranked) {
        List<RagBSearchHit> result=new ArrayList<>();
        for(var hit:ranked) {
            if(result.stream().noneMatch(old->Objects.equals(old.sourceType(),hit.sourceType()) && Objects.equals(old.sourceRef(),hit.sourceRef()) && similar(old.content(),hit.content()))) result.add(hit);
        }
        return List.copyOf(result);
    }
    static boolean similar(String a,String b) {
        if(a==null || b==null) return false;
        a=a.replaceAll("\\s+",""); b=b.replaceAll("\\s+","");
        if(a.equals(b)) return true;
        if(Math.min(a.length(),b.length())<120) return false;
        Set<String> left=shingles(a),right=shingles(b); int same=0;
        for(String value:left) if(right.contains(value)) same++;
        return same/(double)(left.size()+right.size()-same)>=0.9;
    }
    private static Set<String> shingles(String value) {
        Set<String> result=new HashSet<>(); for(int i=0;i+5<=value.length();i++) result.add(value.substring(i,i+5)); return result;
    }
}
