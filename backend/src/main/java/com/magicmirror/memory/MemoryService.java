package com.magicmirror.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆服务：Redis 短期 + Chroma 长期向量记忆
 */
@Slf4j
@Service
public class MemoryService {

    private static final String REDIS_PREFIX = "memory:";
    private static final String CHROMA_COLLECTION = "magic_mirror_memories";
    private static final String CHROMA_BASE = "/api/v2/tenants/default/databases/default";
    private static final int MAX_SHORT_TERM = 20;
    private static final int MAX_LONG_TERM_RESULTS = 5;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String chromaUrl;
    private final String embeddingApiKey;
    private final String embeddingApiUrl;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private String collectionId;

    public MemoryService(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         @Value("${chroma.url:http://localhost:8000}") String chromaUrl,
                         @Value("${embedding.api-key:}") String embeddingApiKey,
                         @Value("${embedding.api-url:https://api.siliconflow.cn/v1/embeddings}") String embeddingApiUrl,
                         @Value("${embedding.model:BAAI/bge-large-zh-v1.5}") String embeddingModel,
                         @Value("${embedding.dimensions:1024}") int embeddingDimensions) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.chromaUrl = chromaUrl;
        this.embeddingApiKey = embeddingApiKey;
        this.embeddingApiUrl = embeddingApiUrl;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        initChromaCollection();
    }

    /** 初始化 Chroma Collection */
    private void initChromaCollection() {
        try {
            // 确保数据库存在
            var dbReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + "/api/v2/tenants/default/databases"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"default\"}"))
                    .header("Content-Type", "application/json")
                    .build();
            try { httpClient.send(dbReq, HttpResponse.BodyHandlers.ofString()); } catch (Exception ignored) {}

            // 先查 collection 是否存在
            var listReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections"))
                    .GET().build();
            var listRes = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            JsonNode collections = objectMapper.readTree(listRes.body());

            for (JsonNode col : collections) {
                if (CHROMA_COLLECTION.equals(col.get("name").asText())) {
                    collectionId = col.get("id").asText();
                    log.info("Chroma collection found: {} (id={})", CHROMA_COLLECTION, collectionId);
                    return;
                }
            }

            // 创建新 collection
            var createBody = objectMapper.writeValueAsString(Map.of(
                    "name", CHROMA_COLLECTION,
                    "metadata", Map.of("description", "Magic Mirror 长期记忆")
            ));
            var createReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(createBody))
                    .build();
            var createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            JsonNode created = objectMapper.readTree(createRes.body());
            collectionId = created.get("id").asText();
            log.info("Chroma collection created: {} (id={})", CHROMA_COLLECTION, collectionId);
        } catch (Exception e) {
            log.warn("Chroma init failed, long-term memory disabled: {}", e.getMessage());
        }
    }

    // ─── 短期记忆（Redis，完整对话转录）───

    public void saveShortTerm(String sessionId, ChatMessage message) {
        message.setTimestamp(Instant.now().toString());
        String key = REDIS_PREFIX + "short:" + sessionId;
        try {
            String entry = objectMapper.writeValueAsString(message);
            redis.opsForList().leftPush(key, entry);
            redis.opsForList().trim(key, 0, MAX_SHORT_TERM - 1);
            redis.expire(key, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis save failed: {}", e.getMessage());
        }
    }

    public List<ChatMessage> getShortTerm(String sessionId, int limit) {
        String key = REDIS_PREFIX + "short:" + sessionId;
        try {
            var list = redis.opsForList().range(key, 0, limit - 1);
            if (list == null || list.isEmpty()) return List.of();
            return list.stream().map(json -> {
                try {
                    return objectMapper.readValue(json, ChatMessage.class);
                } catch (Exception e) { return null; }
            }).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 获取所有 session 的短期记忆 */
    public Map<String, List<ChatMessage>> getAllShortTerm() {
        Map<String, List<ChatMessage>> result = new LinkedHashMap<>();
        try {
            var keys = redis.keys(REDIS_PREFIX + "short:*");
            if (keys != null) {
                for (String key : keys) {
                    String sid = key.substring((REDIS_PREFIX + "short:").length());
                    var msgs = getShortTerm(sid, 50);
                    if (!msgs.isEmpty()) result.put(sid, msgs);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public void clearAllShortTerm() {
        try {
            var keys = redis.keys(REDIS_PREFIX + "short:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }

    public void clearShortTerm(String sessionId) {
        String key = REDIS_PREFIX + "short:" + sessionId;
        redis.opsForList().trim(key, 1, 0);
    }

    // ─── 长期记忆（Chroma 向量）───

    public long countLongTerm() {
        if (collectionId == null) return 0;
        try {
            var body = objectMapper.writeValueAsString(Map.of("include", List.of("documents"), "limit", 1));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/get"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(res.body());
            JsonNode docs = node.get("documents");
            return docs != null ? docs.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void clearLongTerm() {
        if (collectionId == null) return;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId))
                    .DELETE().build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            collectionId = null;
            initChromaCollection();
            log.info("Long-term memory cleared");
        } catch (Exception e) {
            log.warn("Chroma clear failed: {}", e.getMessage());
        }
    }

    public void saveLongTerm(String content) {
        if (collectionId == null || embeddingApiKey.isEmpty()) {
            log.debug("Long-term memory unavailable");
            return;
        }
        try {
            float[] embedding = getEmbedding(content);
            if (embedding == null) return;

            var body = objectMapper.writeValueAsString(Map.of(
                    "ids", List.of(UUID.randomUUID().toString()),
                    "documents", List.of(content),
                    "embeddings", List.of(embedding),
                    "metadatas", List.of(Map.of("timestamp", Instant.now().toString()))
            ));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/add"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Long-term memory saved: {}", content.substring(0, Math.min(50, content.length())));
        } catch (Exception e) {
            log.warn("Chroma save failed: {}", e.getMessage());
        }
    }

    public List<MemoryEntry> searchLongTerm(String query, int limit) {
        if (collectionId == null) return List.of();

        // 混合检索：关键字 + 向量
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryEntry> results = new ArrayList<>();

        // 1. 关键字检索（精确匹配，从 Redis + Chroma）
        try {
            var keys = redis.keys(REDIS_PREFIX + "long:*");
            if (keys != null) {
                for (String key : keys) {
                    String val = redis.opsForValue().get(key);
                    if (val != null && val.contains(query)) {
                        results.add(MemoryEntry.builder().content(val).similarity(1.0).build());
                        seen.add(val);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. 向量检索（需手动 embedding，Chroma v2 不支持 query_texts）
        try {
            float[] embedding = getEmbedding(query);
            if (embedding == null) return results;

            var body = objectMapper.writeValueAsString(Map.of(
                    "query_embeddings", List.of(embedding),
                    "n_results", limit
            ));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(res.body());

            JsonNode docs = root.get("documents");
            JsonNode dists = root.get("distances");
            if (docs != null && docs.isArray() && !docs.isEmpty()) {
                JsonNode docs0 = docs.get(0);
                JsonNode dists0 = dists != null ? dists.get(0) : null;
                for (int i = 0; i < docs0.size() && results.size() < limit + 5; i++) {
                    String content = docs0.get(i).asText();
                    if (seen.contains(content)) continue;
                    double sim = dists0 != null && i < dists0.size()
                            ? Math.max(0, 1.0 - dists0.get(i).asDouble()) : 0;
                    results.add(MemoryEntry.builder().content(content).similarity(sim).build());
                    seen.add(content);
                }
            }
        } catch (Exception e) {
            log.warn("Chroma search failed: {}", e.getMessage());
        }

        return results.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    // ─── Embedding（硅基流动）───

    private float[] getEmbedding(String text) {
        try {
            var bodyMap = new LinkedHashMap<String, Object>();
            bodyMap.put("model", embeddingModel);
            bodyMap.put("input", text);
            bodyMap.put("dimensions", embeddingDimensions);
            var body = objectMapper.writeValueAsString(bodyMap);

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + embeddingApiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                log.warn("Embedding API error: {} {}", res.statusCode(), res.body());
                return null;
            }
            JsonNode root = objectMapper.readTree(res.body());
            JsonNode emb = root.get("data").get(0).get("embedding");
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) vec[i] = (float) emb.get(i).asDouble();
            return vec;
        } catch (Exception e) {
            log.warn("Embedding failed: {}", e.getMessage());
            return null;
        }
    }

    /** 获取相关记忆拼接为上下文 */
    public String buildMemoryContext(String sessionId, String userMessage) {
        StringBuilder ctx = new StringBuilder();

        var shortMem = getShortTerm(sessionId, 10);
        if (!shortMem.isEmpty()) {
            ctx.append("## 近期对话记录\n");
            for (var msg : shortMem) {
                String roleLabel = switch (msg.getRole()) {
                    case "user" -> "👤 用户";
                    case "assistant" -> "🤖 助手";
                    case "tool" -> "🔧 工具 [" + msg.getToolName() + "]";
                    default -> msg.getRole();
                };
                ctx.append(roleLabel).append(": ");
                // 截断过长内容
                String content = msg.getContent() != null ? msg.getContent() : "";
                if (content.length() > 300) content = content.substring(0, 300) + "...";
                ctx.append(content).append("\n");
            }
        }

        var longMem = searchLongTerm(userMessage, MAX_LONG_TERM_RESULTS);
        var relevant = longMem.stream().filter(m -> m.getSimilarity() >= 0.9).toList();
        if (!relevant.isEmpty()) {
            ctx.append("\n## 相关长期记忆\n");
            relevant.forEach(m -> ctx.append("- ").append(m.getContent())
                    .append(String.format(" (%.0f%%)\n", m.getSimilarity() * 100)));
        }
        return ctx.toString();
    }
}
