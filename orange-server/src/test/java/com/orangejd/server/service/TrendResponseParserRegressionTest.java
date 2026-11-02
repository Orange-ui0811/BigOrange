package com.orangejd.server.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TrendResponseParserRegressionTest {
    @Test void productionAbSamplesRetainThirtyRecommendations() throws Exception {
        int count = 0;
        for (int index = 0; index < 12; index++) {
            try (var stream = getClass().getResourceAsStream("/trend/ab/raw-" + index + ".json")) {
                assertNotNull(stream);
                var result = parser.parse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                assertTrue(parser.complete(result));
                count += ((List<?>) result.get("recommendations")).size();
            }
        }
        assertEquals(30, count);
    }
    private final TrendResponseParser parser = new TrendResponseParser();
    private static final String JSON = """
        {"analysis":{"trend":"增长","prospect":"机会","demand":"需求","summary":"总结"},
         "recommendations":[{"name":"项目","description":"说明","difficulty":"入门",
          "difficultyReason":"原因","vibeCodingReason":"适合","recommendReason":"推荐",
          "prerequisites":"基础","score":88,"references":[
           {"url":"https://github.com/a/b","name":"first"},
           {"name":"second","url":"https://github.com/c/d"}]}],
         "learning":{"steps":["第一步"],"timeline":"一周","tips":"建议"}}
        """;

    @Test void nestedReferencesStayInsideOneRecommendation() {
        Map<String, Object> result = parser.parse(JSON);
        assertTrue(parser.complete(result));
        List<?> items = (List<?>) result.get("recommendations");
        assertEquals(1, items.size());
        assertEquals(2, ((List<?>) ((Map<?, ?>) items.getFirst()).get("references")).size());
    }
    @Test void acceptsOuterFenceAndPreservesEscapedStrings() {
        String escaped = JSON.replace("说明", "包含\\\"引号\\\"和\\n换行");
        Map<String, Object> result = parser.parse("```json\n" + escaped + "\n```");
        assertTrue(parser.complete(result));
        assertEquals("包含\"引号\"和\n换行", ((Map<?, ?>) ((List<?>) result.get("recommendations")).getFirst()).get("description"));
    }
    @Test void rejectsTruncationTrailingObjectsAndIncompleteItems() {
        assertFalse(parser.complete(parser.parse(JSON.substring(0, JSON.length() / 2))));
        assertFalse(parser.complete(parser.parse(JSON + " {}")));
        assertFalse(parser.complete(parser.parse(JSON.replace("\"description\":\"说明\"", "\"description\":null"))));
        assertFalse(parser.complete(parser.parse(JSON.replace("\"score\":88", "\"score\":101"))));
    }
}
