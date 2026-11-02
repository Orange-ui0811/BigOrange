package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.GithubApiClient;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.mapper.RagBMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBGithubReadmeServiceTest {
    @Test
    void importsCompleteReadmeThroughUnifiedSourcePipeline() throws Exception {
        GithubApiClient github = mock(GithubApiClient.class);
        RagBDocumentService documents = mock(RagBDocumentService.class);
        String content = "# Project\n\nfull readme";
        String payload = Base64.getMimeEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        when(github.getJson("https://api.github.com/repos/acme/demo/readme"))
                .thenReturn(new ObjectMapper().readTree("{\"encoding\":\"base64\",\"content\":\""
                        + payload.replace("\r", "\\r").replace("\n", "\\n") + "\",\"sha\":\"abc\"}"));
        when(documents.importSource("GITHUB", "acme/demo", "acme/demo README", content))
                .thenReturn(Map.of("documentId", 1L, "status", "PENDING"));

        Map<String, Object> result = new RagBGithubReadmeService(github, documents,
                mock(RagBMapper.class), new RagBProperties())
                .sync("https://github.com/acme/demo.git");

        assertThat(result).containsEntry("repository", "acme/demo").containsEntry("githubSha", "abc");
        verify(documents).importSource("GITHUB", "acme/demo", "acme/demo README", content);
    }

    @Test
    void rejectsNonGithubRepositoryInputBeforeNetworkCall() {
        RagBGithubReadmeService service = new RagBGithubReadmeService(mock(GithubApiClient.class),
                mock(RagBDocumentService.class), mock(RagBMapper.class), new RagBProperties());
        assertThatThrownBy(() -> service.sync("https://example.com/acme/demo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
