package com.orangejd.server.customer;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CustomerJson {
    private final ObjectMapper mapper;
    public CustomerJson(ObjectMapper mapper) { this.mapper=mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(Exception ex) { throw new IllegalArgumentException("客服数据编码失败", ex); }
    }
    public Map<String,Object> read(Object value) {
        if(value == null) return new LinkedHashMap<>();
        try { return mapper.readValue(value.toString(), new TypeReference<LinkedHashMap<String,Object>>(){}); }
        catch(Exception ex) { throw new IllegalArgumentException("INVALID_MODEL_JSON", ex); }
    }
}
