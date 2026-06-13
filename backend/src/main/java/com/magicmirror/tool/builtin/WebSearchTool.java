package com.magicmirror.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.tool.api.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class WebSearchTool implements Tool {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public WebSearchTool(ObjectMapper objectMapper,
                         @Value("${tavily.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override public String getName() { return "web_search"; }
    @Override public String getDescription() {
        return "搜索互联网获取信息，返回标题、URL 和摘要。默认返回 5 条结果。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("type", "string");
        q.put("description", "搜索关键词");
        props.put("query", q);
        Map<String, Object> lim = new LinkedHashMap<>();
        lim.put("type", "integer");
        lim.put("description", "返回结果数量，默认 5，最大 10");
        lim.put("default", 5);
        props.put("limit", lim);
        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.isBlank()) return "错误：请提供搜索关键词";

        int limit = Math.min(arguments.containsKey("limit")
                ? ((Number) arguments.get("limit")).intValue() : 5, 10);

        log.info("Tavily search: query={}, limit={}", query, limit);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("max_results", limit);
            body.put("search_depth", "basic");

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tavily.com/search"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Tavily error: status={}, body={}", response.statusCode(), response.body());
                return String.format("搜索失败：HTTP %d — %s", response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return "未找到与 \"" + query + "\" 相关的结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("搜索 \"%s\" 找到 %d 条结果：\n\n", query, results.size()));

            for (int i = 0; i < results.size(); i++) {
                JsonNode r = results.get(i);
                String title = r.has("title") ? r.get("title").asText() : "无标题";
                String url = r.has("url") ? r.get("url").asText() : "";
                String content = r.has("content") ? r.get("content").asText() : "";

                title = title.replaceAll("<[^>]+>", "").trim();
                content = content.replaceAll("<[^>]+>", "").trim();
                if (content.length() > 300) content = content.substring(0, 300) + "...";

                sb.append(String.format("### %d. %s\n", i + 1, title));
                if (!url.isEmpty()) sb.append(String.format("- URL: %s\n", url));
                if (!content.isEmpty()) sb.append(String.format("- %s\n", content));
                sb.append("\n");
            }

            // 如果有 AI 生成的 answer，也加上
            if (root.has("answer") && !root.get("answer").isNull()) {
                sb.append("---\n**AI 摘要**: ").append(root.get("answer").asText()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Tavily search error: {}", query, e);
            return "搜索失败：" + e.getMessage();
        }
    }
}
