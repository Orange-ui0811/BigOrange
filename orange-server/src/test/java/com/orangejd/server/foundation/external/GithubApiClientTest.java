package com.orangejd.server.foundation.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubApiClientTest {
    @Test
    void parsesSearchResponseAsJsonInsteadOfTreatingItAsPlainText() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String url = "https://api.github.test/search?q=stars%3A%3E30+language%3ATypeScript";
        when(restTemplate.exchange(eq(URI.create(url)), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"total_count":1,"items":[{"full_name":"owner/project"}]}
                        """));
        GithubApiClient client = new GithubApiClient(new ObjectMapper(), restTemplate);
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 0L);

        assertEquals(1, client.getJson(url).path("total_count").asInt());
        assertEquals("owner/project", client.getJson(url)
                .path("items").get(0).path("full_name").asText());
    }

    @Test
    void exposesMapResponseForLegacyLeaderboardPipeline() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(eq(URI.create("https://api.github.test/search")), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"total_count\":2,\"items\":[]}"));
        GithubApiClient client = new GithubApiClient(new ObjectMapper(), restTemplate);
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 0L);

        Map<String, Object> value = client.getMap("https://api.github.test/search");

        assertEquals(2, ((Number) value.get("total_count")).intValue());
    }

    @Test
    void preservesRequestFailuresInsteadOfMisreportingThemAsInvalidJson() {
        GithubApiClient client = new GithubApiClient(new ObjectMapper(), mock(RestTemplate.class));
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 0L);

        BaseException error = assertThrows(BaseException.class,
                () -> client.getMap("https://api.github.test/search?q=stars:>1 invalid"));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().startsWith("GitHub API request failed:"));
    }

    @Test
    void reportsInvalidJsonOnlyAfterARequestReturnsAResponseBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String url = "https://api.github.test/search";
        when(restTemplate.exchange(eq(URI.create(url)), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("not-json"));
        GithubApiClient client = new GithubApiClient(new ObjectMapper(), restTemplate);
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 0L);

        BaseException error = assertThrows(BaseException.class, () -> client.getMap(url));

        assertEquals("GitHub API returned invalid JSON", error.getMessage());
    }
}
