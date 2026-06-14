package com.magicmirror.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.graph.EntityExtractor;
import com.magicmirror.graph.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class DocumentService {

    private static final String CHROMA_BASE = "/api/v2/tenants/default/databases/default";
    private static final String COLLECTION_NAME = "magic_mirror_docs";
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EntityExtractor entityExtractor;
    private final GraphService graphService;
    private final String chromaUrl;
    private final String embeddingApiKey;
    private final String embeddingApiUrl;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private String collectionId;

    public DocumentService(ObjectMapper objectMapper,
                           EntityExtractor entityExtractor,
                           GraphService graphService,
                           @Value("${chroma.url:http://localhost:8000}") String chromaUrl,
                           @Value("${embedding.api-key:XQ7CFI58PHGIYNTNVEHUPRDRE9CQLQDPYN7F8W15}") String embeddingApiKey,
                           @Value("${embedding.api-url:https://ai.gitee.com/v1/embeddings}") String embeddingApiUrl,
                           @Value("${embedding.model:Qwen3-Embedding-8B}") String embeddingModel,
                           @Value("${embedding.dimensions:1024}") int embeddingDimensions) {
        this.objectMapper = objectMapper;
        this.entityExtractor = entityExtractor;
        this.graphService = graphService;
        this.chromaUrl = chromaUrl;
        this.embeddingApiKey = embeddingApiKey;
        this.embeddingApiUrl = embeddingApiUrl;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        initCollection();
    }

    private void initCollection() {
        try {
            // ensure db
            var dbReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + "/api/v2/tenants/default/databases"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"default\"}"))
                    .build();
            try { httpClient.send(dbReq, HttpResponse.BodyHandlers.ofString()); } catch (Exception ignored) {}

            // list collections
            var listReq = HttpRequest.newBuilder().uri(URI.create(chromaUrl + CHROMA_BASE + "/collections")).GET().build();
            var listRes = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            for (JsonNode c : objectMapper.readTree(listRes.body())) {
                if (COLLECTION_NAME.equals(c.get("name").asText())) {
                    collectionId = c.get("id").asText();
                    log.info("RAG collection found: {}", collectionId);
                    return;
                }
            }
            // create
            var body = objectMapper.writeValueAsString(Map.of("name", COLLECTION_NAME));
            var createReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            collectionId = objectMapper.readTree(createRes.body()).get("id").asText();
            log.info("RAG collection created: {}", collectionId);
        } catch (Exception e) {
            log.warn("RAG collection init failed: {}", e.getMessage());
        }
    }

    /** 上传并索引文档 */
    public String upload(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";
        String content = parseFile(file);

        // 保存原始文件
        Path uploadsDir = Paths.get("uploads");
        Files.createDirectories(uploadsDir);
        Files.write(uploadsDir.resolve(filename), file.getBytes());

        // 分段
        List<String> chunks = splitChunks(content);

        // Embedding
        float[][] embeddings = batchEmbed(chunks);

        // 存入 Chroma
        List<String> ids = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ids.add(UUID.randomUUID().toString());
            metadatas.add(Map.of("filename", filename != null ? filename : "unknown",
                    "chunk_index", String.valueOf(i),
                    "timestamp", Instant.now().toString()));
        }

        var body = objectMapper.writeValueAsString(Map.of(
                "ids", ids,
                "documents", chunks,
                "embeddings", embeddings,
                "metadatas", metadatas
        ));

        var req = HttpRequest.newBuilder()
                .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/add"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        log.info("Document indexed: {} ({} chunks)", filename, chunks.size());

        // 异步抽取实体 → Neo4j
        String docId = filename != null ? filename : UUID.randomUUID().toString();
        try {
            var entities = entityExtractor.extract(content);
            var stats = graphService.storeEntities(docId, entities);
            log.info("Graph entities: {} entities, {} relations", stats.get("entities"), stats.get("relations"));
        } catch (Exception e) { log.warn("Entity extraction failed: {}", e.getMessage()); }

        return String.format("已索引 %s，共 %d 个片段", filename, chunks.size());
    }

    /** RAG 检索 */
    public List<Map<String, Object>> search(String query, int topK) {
        if (collectionId == null) return List.of();
        try {
            float[] emb = singleEmbed(query);
            if (emb == null) return List.of();

            var body = objectMapper.writeValueAsString(Map.of(
                    "query_embeddings", List.of(emb),
                    "n_results", topK
            ));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(res.body());

            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode docs = root.get("documents");
            JsonNode dists = root.get("distances");
            JsonNode metas = root.get("metadatas");
            if (docs != null && docs.isArray() && !docs.isEmpty()) {
                JsonNode d0 = docs.get(0), dist0 = dists != null ? dists.get(0) : null;
                JsonNode m0 = metas != null ? metas.get(0) : null;
                for (int i = 0; i < d0.size(); i++) {
                    double sim = dist0 != null && i < dist0.size() ? Math.max(0, 1.0 - dist0.get(i).asDouble()) : 0;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content", d0.get(i).asText());
                    item.put("score", sim);
                    if (m0 != null && i < m0.size()) {
                        item.put("filename", m0.get(i).has("filename") ? m0.get(i).get("filename").asText() : "");
                    }
                    results.add(item);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("RAG search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long countDocs() {
        if (collectionId == null) return 0;
        try {
            var body = objectMapper.writeValueAsString(Map.of("include", List.of("documents"), "limit", 1));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/get"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode docs = objectMapper.readTree(res.body()).get("documents");
            return docs != null ? docs.size() : 0;
        } catch (Exception e) { return 0; }
    }

    public void clear() {
        if (collectionId == null) return;
        try {
            // Chroma v2 删全部文档：where 匹配所有
            var delBody = objectMapper.writeValueAsString(Map.of(
                    "where", Map.of("filename", Map.of("$ne", "__none__"))
            ));
            var delReq = HttpRequest.newBuilder()
                    .uri(URI.create(chromaUrl + CHROMA_BASE + "/collections/" + collectionId + "/delete"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(delBody)).build();
            httpClient.send(delReq, HttpResponse.BodyHandlers.ofString());

            // 删除上传文件
            Path uploads = Paths.get("uploads");
            if (Files.exists(uploads)) {
                try (var files = Files.list(uploads)) {
                    files.forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} });
                }
            }
        } catch (Exception ignored) {}
    }

    // ── 文件解析 ──

    private String parseFile(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".pdf")) {
            try (var doc = Loader.loadPDF(file.getBytes())) {
                var stripper = new PDFTextStripper();
                return stripper.getText(doc);
            }
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    // ── 文本分段 ──

    private List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<>();
        // 先按段落分割
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (buf.length() + trimmed.length() > CHUNK_SIZE && buf.length() > 0) {
                chunks.add(buf.toString().trim());
                // overlap: 保留末尾
                String overlap = buf.length() > CHUNK_OVERLAP ? buf.substring(buf.length() - CHUNK_OVERLAP) : buf.toString();
                buf = new StringBuilder(overlap);
            }
            if (buf.length() > 0) buf.append("\n\n");
            buf.append(trimmed);

            // 如果单个段落超长，按句子切
            while (buf.length() > CHUNK_SIZE * 2) {
                int split = findSplitPoint(buf.toString(), CHUNK_SIZE);
                chunks.add(buf.substring(0, split).trim());
                buf = new StringBuilder(buf.substring(Math.max(0, split - CHUNK_OVERLAP)));
            }
        }

        if (buf.length() > 0) chunks.add(buf.toString().trim());
        return chunks;
    }

    private int findSplitPoint(String text, int target) {
        // 优先在句号、换行处切
        for (char sep : new char[]{'\n', '。', '.', '！', ';', '；'}) {
            int pos = text.lastIndexOf(sep, target);
            if (pos > target / 2) return pos + 1;
        }
        return target;
    }

    // ── Embedding ──

    private float[] singleEmbed(String text) {
        float[][] batch = batchEmbed(List.of(text));
        return batch != null && batch.length > 0 ? batch[0] : null;
    }

    private float[][] batchEmbed(List<String> texts) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "model", embeddingModel, "input", texts, "dimensions", embeddingDimensions));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + embeddingApiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) { log.warn("Embed error: {}", res.body()); return null; }

            JsonNode data = objectMapper.readTree(res.body()).get("data");
            float[][] result = new float[data.size()][];
            for (int i = 0; i < data.size(); i++) {
                JsonNode emb = data.get(i).get("embedding");
                float[] vec = new float[emb.size()];
                for (int j = 0; j < emb.size(); j++) vec[j] = (float) emb.get(j).asDouble();
                result[i] = vec;
            }
            return result;
        } catch (Exception e) { log.warn("Batch embed failed: {}", e.getMessage()); return null; }
    }
}
