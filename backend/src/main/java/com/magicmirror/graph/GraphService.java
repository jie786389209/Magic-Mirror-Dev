package com.magicmirror.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class GraphService {

    private static final int MAX_ENTITIES_PER_DOC = 20;
    private static final int MAX_RELATIONS_PER_ENTITY = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String neo4jUrl;
    private final String neo4jAuth;

    public GraphService(ObjectMapper objectMapper,
                        @Value("${neo4j.url:http://localhost:7474}") String neo4jUrl,
                        @Value("${neo4j.auth:neo4j:neo4j12345}") String neo4jAuth) {
        this.objectMapper = objectMapper;
        this.neo4jUrl = neo4jUrl;
        this.neo4jAuth = neo4jAuth;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        initConstraints();
    }

    private void initConstraints() {
        try {
            cypher("CREATE CONSTRAINT entity_name IF NOT EXISTS FOR (e:Entity) REQUIRE e.name IS UNIQUE");
        } catch (Exception ignored) {}
    }

    // ─── 存储实体和关系 ───

    public Map<String, Integer> storeEntities(String docId, List<EntityRel> entities) {
        int eCount = 0, rCount = 0;
        int maxE = Math.min(entities.size(), MAX_ENTITIES_PER_DOC);

        for (int i = 0; i < maxE; i++) {
            EntityRel e = entities.get(i);
            try {
                cypher("MERGE (e:Entity {name: $name}) SET e.type = $type, e.doc_id = $docId",
                        Map.of("name", e.name, "type", e.type, "docId", docId));
                eCount++;

                // 连接文档
                cypher("MATCH (e:Entity {name: $name}) MERGE (d:Document {id: $docId}) MERGE (d)-[:CONTAINS]->(e)",
                        Map.of("name", e.name, "docId", docId));

                // 关系（限制数量）
                int maxR = Math.min(e.relations.size(), MAX_RELATIONS_PER_ENTITY);
                for (int j = 0; j < maxR; j++) {
                    String rel = e.relations.get(j);
                    String[] parts = rel.split("->");
                    if (parts.length == 2) {
                        String target = parts[1].trim();
                        String relType = parts[0].trim().replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
                        if (relType.length() > 30) relType = relType.substring(0, 30);
                        cypher("MERGE (t:Entity {name: $target}) MERGE (e:Entity {name: $name}) MERGE (e)-[:" + relType + "]->(t)",
                                Map.of("name", e.name, "target", target));
                        rCount++;
                    }
                }
            } catch (Exception ex) { log.warn("Entity store failed: {}", e.name, ex); }
        }
        return Map.of("entities", eCount, "relations", rCount);
    }

    // ─── 图查询 ───

    /** 根据关键词查询实体，支持 2-hop 遍历和关系类型过滤 */
    public List<Map<String, Object>> searchGraph(String keyword, String relFilter, int limit) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("kwRegex", "(?i).*" + keyword + ".*");
            params.put("limit", limit);
            if (relFilter != null && !relFilter.isBlank()) {
                params.put("relRegex", "(?i).*" + relFilter + ".*");
            }

            boolean hasRel = relFilter != null && !relFilter.isBlank();
            String query = hasRel ?
                "MATCH (e:Entity) WHERE e.name =~ $kwRegex OR e.type =~ $kwRegex WITH e LIMIT $limit " +
                "OPTIONAL MATCH (e)-[r]->(t:Entity) WHERE type(r) =~ $relRegex " +
                "WITH e, collect(DISTINCT {rel: type(r), target: t.name}) AS oneHop, collect(DISTINCT t) AS neighbors " +
                "UNWIND neighbors AS n1 OPTIONAL MATCH (n1)-[r2]->(t2:Entity) WHERE type(r2) =~ $relRegex AND t2.name <> e.name " +
                "WITH e, oneHop, collect(DISTINCT {rel: type(r2), target: t2.name, from: n1.name}) AS twoHop " +
                "RETURN e.name AS entity, e.type AS type, oneHop, [h IN twoHop WHERE h.target IS NOT NULL] AS twoHopRelations" :
                "MATCH (e:Entity) WHERE e.name =~ $kwRegex OR e.type =~ $kwRegex WITH e LIMIT $limit " +
                "OPTIONAL MATCH (e)-[r]->(t:Entity) " +
                "WITH e, collect(DISTINCT {rel: type(r), target: t.name}) AS oneHop, collect(DISTINCT t) AS neighbors " +
                "UNWIND neighbors AS n1 OPTIONAL MATCH (n1)-[r2]->(t2:Entity) WHERE t2.name <> e.name " +
                "WITH e, oneHop, collect(DISTINCT {rel: type(r2), target: t2.name, from: n1.name}) AS twoHop " +
                "RETURN e.name AS entity, e.type AS type, oneHop, [h IN twoHop WHERE h.target IS NOT NULL] AS twoHopRelations";

            JsonNode node = cypher(query, params);

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode row : node.get("results").get(0).get("data")) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entity", row.get("row").get(0).asText());
                item.put("type", row.get("row").get(1).asText());

                // 1-hop
                List<Map<String, String>> oneHop = new ArrayList<>();
                JsonNode r1 = row.get("row").get(2);
                if (r1.isArray()) {
                    for (JsonNode r : r1) {
                        if (!r.get("target").isNull()) {
                            oneHop.add(Map.of("rel", r.get("rel").asText(), "target", r.get("target").asText()));
                        }
                    }
                }
                item.put("relations", oneHop);

                // 2-hop
                List<Map<String, String>> twoHop = new ArrayList<>();
                JsonNode r2 = row.get("row").get(3);
                if (r2.isArray()) {
                    for (JsonNode r : r2) {
                        if (!r.get("target").isNull()) {
                            twoHop.add(Map.of("rel", r.get("rel").asText(),
                                    "target", r.get("target").asText(),
                                    "from", r.get("from") != null ? r.get("from").asText() : ""));
                        }
                    }
                }
                item.put("twoHop", twoHop);
                results.add(item);
            }
            return results;
        } catch (Exception e) { log.warn("Graph search failed: {}", e.getMessage()); return List.of(); }
    }

    /** 检索与用户问题相关的实体 */
    public List<String> findRelevantEntities(String query, int limit) {
        try {
            // 提取关键词
            String[] keywords = query.split("[\\s，。！？]+");
            Set<String> words = new LinkedHashSet<>();
            for (String kw : keywords) {
                if (kw.length() >= 2) words.add(kw);
            }

            Set<String> entities = new LinkedHashSet<>();
            for (String kw : words) {
                JsonNode node = cypher("""
                    MATCH (e:Entity)
                    WHERE toLower(e.name) CONTAINS toLower($kw)
                    OPTIONAL MATCH (e)-[r]->(neighbor)
                    RETURN e.name AS entity, e.type AS type,
                           collect(DISTINCT neighbor.name)[0..5] AS neighbors
                    LIMIT 3
                    """, Map.of("kw", kw));

                for (JsonNode row : node.get("results").get(0).get("data")) {
                    entities.add(row.get("row").get(0).asText());
                    JsonNode neighbors = row.get("row").get(2);
                    if (neighbors.isArray()) {
                        for (JsonNode n : neighbors) {
                            if (!n.isNull()) entities.add(n.asText());
                        }
                    }
                }
            }
            return entities.stream().limit(limit).toList();
        } catch (Exception e) { return List.of(); }
    }

    public long countEntities() {
        try {
            JsonNode node = cypher("MATCH (e:Entity) RETURN count(e) AS c", Map.of());
            return node.get("results").get(0).get("data").get(0).get("row").get(0).asLong();
        } catch (Exception e) { return 0; }
    }

    public long countRelations() {
        try {
            JsonNode node = cypher("MATCH ()-[r]->() RETURN count(r) AS c", Map.of());
            return node.get("results").get(0).get("data").get(0).get("row").get(0).asLong();
        } catch (Exception e) { return 0; }
    }

    public void clear() {
        try { cypher("MATCH (n) DETACH DELETE n"); } catch (Exception ignored) {}
    }

    // ─── Cypher 执行 ───

    private JsonNode cypher(String statement) {
        return cypher(statement, Map.of());
    }

    private JsonNode cypher(String statement, Map<String, Object> params) {
        try {
            Map<String, Object> body = Map.of("statements", List.of(Map.of(
                    "statement", statement,
                    "parameters", params
            )));
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(neo4jUrl + "/db/neo4j/tx/commit"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(neo4jAuth.getBytes()))
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(res.body());
        } catch (Exception e) {
            throw new RuntimeException("Cypher failed", e);
        }
    }

    // ─── 实体关系模型 ───

    public record EntityRel(String name, String type, List<String> relations) {}
}
