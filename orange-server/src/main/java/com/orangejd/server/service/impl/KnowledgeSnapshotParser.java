package com.orangejd.server.service.impl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class KnowledgeSnapshotParser {
List<Map<String, Object>> parseRepos(String dataJson) {
        List<Map<String, Object>> repos = new ArrayList<>();
        if (dataJson == null || dataJson.isEmpty()) return repos;
        try {
            // 提取 records 数组
            int arrStart = dataJson.indexOf("\"records\":[") + 11;
            if (arrStart < 11) return repos;
            int arrEnd = dataJson.lastIndexOf("]}");
            if (arrEnd < 0) arrEnd = dataJson.length() - 2;
            String arrStr = dataJson.substring(arrStart, arrEnd);

            // 解析 JSON 对象数组
            int depth = 0, objStart = -1;
            for (int i = 0; i < arrStr.length(); i++) {
                char c = arrStr.charAt(i);
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        String objStr = arrStr.substring(objStart + 1, i);
                        Map<String, Object> repo = parseJsonObj(objStr);
                        if (!repo.isEmpty()) repos.add(repo);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep the legacy behavior: malformed snapshots produce an empty repository list.
        }
        return repos;
    }

Map<String, Object> parseJsonObj(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        // 简单解析 key:value 对
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            int colon = pair.indexOf(":");
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
            String val = pair.substring(colon + 1).trim();
            if (val.startsWith("\"")) {
                val = val.substring(1, val.lastIndexOf("\""));
                map.put(key, val.replace("\\\"", "\"").replace("\\n", "\n"));
            } else if ("null".equals(val)) {
                map.put(key, null);
            } else if (val.contains(".")) {
                try { map.put(key, Double.parseDouble(val)); } catch (Exception e) { map.put(key, val); }
            } else {
                try { map.put(key, Long.parseLong(val)); } catch (Exception e) { map.put(key, val); }
            }
        }
        return map;
    }
}
