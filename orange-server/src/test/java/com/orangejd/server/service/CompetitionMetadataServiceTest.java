package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompetitionMetadataServiceTest {

    private final CompetitionMetadataService service =
            new CompetitionMetadataService(new ObjectMapper());

    @Test
    void extractsOpenGraphAndStructuredEventData() {
        Document document = Jsoup.parse("""
                <html><head>
                  <meta property="og:title" content="Orange AI Challenge">
                  <meta property="og:description" content="Build a useful AI agent">
                  <script type="application/ld+json">
                    {
                      "@context": "https://schema.org",
                      "@type": "Event",
                      "name": "Structured name",
                      "organizer": {"@type": "Organization", "name": "Orange Team"},
                      "startDate": "2026-08-01T09:00:00",
                      "endDate": "2026-08-31T18:00:00"
                    }
                  </script>
                </head></html>
                """);

        Map<String, Object> result = service.extract(document, "https://example.com/challenge");

        assertEquals("Orange AI Challenge", result.get("name"));
        assertEquals("Build a useful AI agent", result.get("summary"));
        assertEquals("Orange Team", result.get("organizer"));
        assertEquals(LocalDateTime.of(2026, 8, 1, 9, 0), result.get("startTime"));
        assertEquals(LocalDateTime.of(2026, 8, 31, 18, 0), result.get("endTime"));
    }

    @Test
    void refusesNonHttpsBeforeMakingNetworkRequest() {
        assertThrows(BaseException.class,
                () -> service.fetchPreview("http://127.0.0.1/internal"));
    }
}
