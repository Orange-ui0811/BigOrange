package com.orangejd.server.foundation.external;

import java.util.*;

/** Describe field values explicitly so multi-topic output agrees with server validation. */
final class CustomerActionSchema {
    private CustomerActionSchema() {}
    static Map<String,Object> changes() {
        Map<String,Object> fields=new LinkedHashMap<>();
        Map<String,Object> text=Map.of("type","string","minLength",1,"maxLength",120);
        Map<String,Object> topics=Map.of("type","array","minItems",1,"maxItems",6,"items",Map.of("type","string","minLength",1,"maxLength",80));
        for(String name:List.of("experience","language","difficulty","sourceType")) fields.put(name,change(text));
        fields.put("topic",change(topics));
        fields.put("goal",change(Map.of("anyOf",List.of(text,topics))));
        for(String name:List.of("allowDocker","allowDatabase","allowSecrets","allowHardware")) fields.put(name,change(Map.of("type","boolean")));
        fields.put("verificationMode",change(Map.of("type","string","enum",List.of("STRICT","EXPLORE"))));
        Map<String,Object> duration=Map.of("type","object","properties",Map.of("quantity",Map.of("type","number","exclusiveMinimum",0),"unit",Map.of("type","string","enum",List.of("DAY","WEEK","MONTH","YEAR")),"relation",Map.of("type","string","enum",List.of("AT_MOST","AT_LEAST"))),"required",List.of("quantity","unit","relation"),"additionalProperties",false);
        fields.put("maxDurationDays",change(duration));fields.put("minDurationDays",change(duration));
        return Map.of("type","object","properties",fields,"additionalProperties",false);
    }
    private static Map<String,Object> change(Map<String,Object> value) {
        return Map.of("type","object","properties",Map.of("value",Map.of("anyOf",List.of(value,Map.of("type","null"))),"quote",Map.of("type","string","minLength",1),"operation",Map.of("type","string","enum",List.of("SET","ADD","REMOVE","CLEAR"))),"required",List.of("quote"),"additionalProperties",false);
    }
}
