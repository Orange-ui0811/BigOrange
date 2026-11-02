package com.orangejd.server.ragb.retrieval;

import java.util.*;

/** Separate known, explicitly named project identity from lexical content evidence. */
public final class RagBContentQuery {
    public static final String POLICY="PROJECT_NAMES_SEPARATED_V1";
    private RagBContentQuery() {}
    public static List<String> terms(String query, Collection<String> aliases) {
        Set<String> identities=new HashSet<>();
        for(String alias:aliases) {
            if(alias==null)continue;
            String name=alias.split("\\|",2)[0];
            int slash=Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'));
            if(slash>=0)name=name.substring(slash+1);
            if(RagBFusionScorer.exactNameMatch(query,name,""))identities.add(name.toLowerCase(Locale.ROOT));
        }
        return RagBQueryTokenizer.tokenize(query).stream().filter(term->!identities.contains(term.toLowerCase(Locale.ROOT))).toList();
    }
}
