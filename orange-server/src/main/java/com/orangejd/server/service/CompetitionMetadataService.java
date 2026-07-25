package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.util.UrlValidator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CompetitionMetadataService {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 1_048_576;
    private final ObjectMapper objectMapper;

    public CompetitionMetadataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> fetchPreview(String sourceUrl) {
        String current = CompetitionService.normalizeUrl(sourceUrl);
        try {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                requireSafeHttps(current);
                Connection.Response response = Jsoup.connect(current)
                        .userAgent("OrangeVC-Competition-Review/1.0")
                        .timeout(8_000)
                        .maxBodySize(MAX_BODY_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();

                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank() || redirect == MAX_REDIRECTS) {
                        throw new BaseException("网页重定向次数过多或地址无效");
                    }
                    current = CompetitionService.normalizeUrl(URI.create(current).resolve(location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) throw new BaseException("网页返回状态码 " + status);
                String contentType = response.contentType();
                if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
                    throw new BaseException("目标地址不是 HTML 网页");
                }
                return extract(response.parse(), current);
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("网页提取失败：" + safeMessage(e));
        }
        throw new BaseException("网页提取失败");
    }

    Map<String, Object> extract(Document document, String finalUrl) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("sourceUrl", finalUrl);
        preview.put("name", first(
                meta(document, "property", "og:title"),
                meta(document, "name", "twitter:title"),
                document.title()
        ));
        preview.put("summary", first(
                meta(document, "property", "og:description"),
                meta(document, "name", "description"),
                meta(document, "name", "twitter:description")
        ));
        preview.put("organizer", first(
                meta(document, "property", "og:site_name"),
                meta(document, "name", "author")
        ));

        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                JsonNode event = findEvent(root);
                if (event == null) continue;
                putIfBlank(preview, "name", text(event, "name"));
                putIfBlank(preview, "summary", text(event, "description"));
                JsonNode organizer = event.get("organizer");
                if (organizer != null) {
                    putIfBlank(preview, "organizer",
                            organizer.isTextual() ? organizer.asText() : text(organizer, "name"));
                }
                LocalDateTime start = parseDate(text(event, "startDate"));
                LocalDateTime end = parseDate(text(event, "endDate"));
                if (start != null) preview.put("startTime", start);
                if (end != null) preview.put("endTime", end);
                break;
            } catch (Exception ignored) {
                // Invalid JSON-LD is common; other metadata remains useful.
            }
        }
        return preview;
    }

    private JsonNode findEvent(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findEvent(child);
                if (found != null) return found;
            }
        } else if (node.isObject()) {
            String type = text(node, "@type");
            if ("Event".equalsIgnoreCase(type) || "Hackathon".equalsIgnoreCase(type)) return node;
            JsonNode graph = node.get("@graph");
            JsonNode found = findEvent(graph);
            if (found != null) return found;
        }
        return null;
    }

    private void requireSafeHttps(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !UrlValidator.isSafe(url)) {
            throw new BaseException("该网址不允许由服务器访问");
        }
    }

    private static String meta(Document document, String attr, String value) {
        Element element = document.selectFirst("meta[" + attr + "=\"" + value + "\"]");
        return element == null ? null : element.attr("content");
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private static void putIfBlank(Map<String, Object> target, String key, String value) {
        Object current = target.get(key);
        if ((current == null || current.toString().isBlank()) && value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private static LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); } catch (DateTimeParseException ignored) {}
        try { return ZonedDateTime.parse(value).toLocalDateTime(); } catch (DateTimeParseException ignored) {}
        try { return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime(); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(value); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(value).atStartOfDay(); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "无法读取目标网页";
        return message.length() <= 160 ? message : message.substring(0, 160);
    }
}
