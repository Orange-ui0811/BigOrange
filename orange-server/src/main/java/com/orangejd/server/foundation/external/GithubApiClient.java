package com.orangejd.server.foundation.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class GithubApiClient {
    private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ReentrantLock requestLock = new ReentrantLock(true);
    private long nextRequestAt;

    @Value("${orange.leaderboard.github-token:}")
    private String githubToken;

    @Value("${orange.github.min-request-interval-ms:450}")
    private long minRequestIntervalMs;

    @Autowired
    public GithubApiClient(ObjectMapper objectMapper) {
        this(objectMapper, createRestTemplate());
    }

    GithubApiClient(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    public JsonNode getJson(String url) {
        String body = get(url);
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new BaseException("GitHub API returned invalid JSON");
        }
    }

    public Map<String, Object> getMap(String url) {
        String body = get(url);
        try {
            return objectMapper.readValue(body, new TypeReference<>() { });
        } catch (Exception ex) {
            throw new BaseException("GitHub API returned invalid JSON");
        }
    }

    private String get(String url) {
        boolean authenticated = githubToken != null && !githubToken.isBlank();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return execute(url, authenticated).getBody();
            } catch (HttpClientErrorException.Unauthorized unauthorized) {
                if (!authenticated) throw githubError(unauthorized);
                authenticated = false;
                log.warn("GitHub token was rejected; retrying this request anonymously");
            } catch (HttpClientErrorException.TooManyRequests limited) {
                if (attempt == MAX_ATTEMPTS) throw githubError(limited);
                sleep(backoffMs(attempt, limited.getResponseHeaders()));
            } catch (HttpClientErrorException.Forbidden forbidden) {
                if (attempt == MAX_ATTEMPTS || !rateLimitExhausted(forbidden.getResponseHeaders())) {
                    throw githubError(forbidden);
                }
                sleep(backoffMs(attempt, forbidden.getResponseHeaders()));
            } catch (HttpServerErrorException serverError) {
                if (attempt == MAX_ATTEMPTS) throw githubError(serverError);
                sleep(500L * attempt);
            } catch (BaseException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new BaseException("GitHub API request failed: " + safeMessage(ex));
                }
                sleep(500L * attempt);
            }
        }
        throw new BaseException("GitHub API request failed");
    }

    private ResponseEntity<String> execute(String url, boolean authenticated) {
        requestLock.lock();
        try {
            long wait = nextRequestAt - System.currentTimeMillis();
            if (wait > 0) sleep(wait);
            nextRequestAt = System.currentTimeMillis() + Math.max(100, minRequestIntervalMs);
            return restTemplate.exchange(URI.create(url), HttpMethod.GET,
                    new HttpEntity<>(headers(authenticated)), String.class);
        } finally {
            requestLock.unlock();
        }
    }

    private HttpHeaders headers(boolean authenticated) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set("User-Agent", "OrangeVC-GitHub-Client");
        if (authenticated) headers.setBearerAuth(githubToken.trim());
        return headers;
    }

    private boolean rateLimitExhausted(HttpHeaders headers) {
        return headers != null && "0".equals(headers.getFirst("X-RateLimit-Remaining"));
    }

    private long backoffMs(int attempt, HttpHeaders headers) {
        if (headers != null) {
            String retryAfter = headers.getFirst("Retry-After");
            if (retryAfter != null) {
                try { return Math.min(30_000, Long.parseLong(retryAfter) * 1000L); }
                catch (NumberFormatException ignored) { }
            }
        }
        return Math.min(5_000, 750L * attempt);
    }

    private BaseException githubError(HttpClientErrorException ex) {
        return new BaseException("GitHub API returned HTTP " + ex.getStatusCode().value());
    }

    private BaseException githubError(HttpServerErrorException ex) {
        return new BaseException("GitHub API returned HTTP " + ex.getStatusCode().value());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BaseException("GitHub API request was interrupted");
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
