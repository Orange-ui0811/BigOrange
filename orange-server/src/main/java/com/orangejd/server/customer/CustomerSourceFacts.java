package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.Pattern;

/** Only an explicit first-person repository introduction can establish this limited fact. */
final class CustomerSourceFacts {
    private CustomerSourceFacts() {}
    private static final Pattern LANGUAGE=Pattern.compile("(?i)^(?:A|An|The)\\s+(?:(?:concurrent|lightweight|open-source|simple|fast|modern|small|cross-platform|pure)\\s+){0,4}(JavaScript|TypeScript|Java|Python|Golang|Go|Rust|C\\+\\+|C#)\\s+(?:CLI|library|application|framework|implementation|package)\\b");
    static Map<String,Object> explicitLanguage(String content) {
        if(content==null || content.isBlank())return Map.of();
        int start=CustomerEvidence.meaningfulStart(content);
        String line=content.substring(start).split("\\R",2)[0];
        var match=LANGUAGE.matcher(line);
        if(!match.find())return Map.of();
        String language=switch(match.group(1).toLowerCase(Locale.ROOT)) {
            case "go","golang"->"Go";case "java"->"Java";case "javascript"->"JavaScript";case "typescript"->"TypeScript";
            case "python"->"Python";case "rust"->"Rust";case "c++"->"C++";default->"C#";
        };
        return Map.of("language",language,"quote",line,"startInDocument",start);
    }
}
