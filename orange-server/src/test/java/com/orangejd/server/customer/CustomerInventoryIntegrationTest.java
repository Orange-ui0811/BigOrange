package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.*;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_INVENTORY",matches="true")
class CustomerInventoryIntegrationTest {
    @Test void productionFiltersAndCountsOnReadOnlyLocalSnapshot() throws Exception {
        var json=new ObjectMapper();var snapshot=json.readTree(Path.of(System.getenv("CUSTOMER_INVENTORY_SNAPSHOT")).toFile());
        var beginner=mock(BeginnerProjectMapper.class);var career=mock(CareerProjectMapper.class);
        when(beginner.selectAllPublished()).thenReturn(Arrays.asList(json.treeToValue(snapshot.get("beginner"),BeginnerProject[].class)));
        when(career.selectAllPublished()).thenReturn(Arrays.asList(json.treeToValue(snapshot.get("career"),CareerProjectProfile[].class)));
        var catalog=mock(CustomerCatalogMapper.class);
        var beginnerRows=beginner.selectAllPublished();var careerRows=career.selectAllPublished();
        when(catalog.beginners()).thenReturn(beginnerRows);when(catalog.careers()).thenReturn(careerRows);
        when(catalog.externalDocuments()).thenReturn(json.convertValue(snapshot.get("external"),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){}));
        var projects=new CustomerProjects(beginner,career,new RagBConstraintMatcher(),catalog);
        Map<String,Map<String,Object>> cases=new LinkedHashMap<>();
        cases.put("JAVA_LEARN_60",Map.of("goal","学习","experience","刚学","language","Java","maxDurationDays",60));
        cases.put("JAVA_LEARN_180",Map.of("goal","学习","experience","刚学","language","Java","maxDurationDays",180));
        cases.put("JAVA_STRICT_LEARNING_SOURCE",Map.of("sourceType","BEGINNER_PROJECT","language","Java","maxDurationDays",180));
        cases.put("JAVA_7_DAYS",Map.of("language","Java","maxDurationDays",7));
        cases.put("JAVA_NO_DOCKER",Map.of("language","Java","allowDocker",false,"maxDurationDays",180));
        List<Map<String,Object>> results=new ArrayList<>();
        for(var c:cases.entrySet()) {
            var diagnosis=projects.diagnose(c.getValue());var cards=projects.search(c.getValue());
            assertThat(cards).allMatch(p->projects.explorable(p,c.getValue()));
            assertThat(((Number)((Map<?,?>)diagnosis.get("current")).get("total")).intValue()).isGreaterThanOrEqualTo(cards.size());
            results.add(Map.of("case",c.getKey(),"conditions",c.getValue(),"diagnostics",diagnosis,"displayed",cards.size(),"cards",cards,"answer",CustomerDiagnostics.describe(diagnosis)));
        }
        Path root=Files.isDirectory(Path.of("docs/customer"))?Path.of("."):Path.of("..");
        json.writerWithDefaultPrettyPrinter().writeValue(root.resolve("docs/customer/acceptance/"+System.getenv().getOrDefault("CUSTOMER_INVENTORY_REPORT","customer-v42-live-counts")+".json").toFile(),Map.of("scope","Local read-only DB snapshot; published, candidate and crawled repositories; production Java filters; no model call","inventory",projects.diagnose(Map.of()),"rows",results));
    }
}
