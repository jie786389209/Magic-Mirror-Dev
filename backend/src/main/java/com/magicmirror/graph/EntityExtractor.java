package com.magicmirror.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.config.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class EntityExtractor {

    private static final int MAX_ENTITIES = 20;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties properties;

    public EntityExtractor(ObjectMapper objectMapper, DeepSeekProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /** 从文本中提取实体和关系 */
    public List<GraphService.EntityRel> extract(String text) {
        if (text.length() > 2000) text = text.substring(0, 2000);

        String prompt = """
            从以下文本中提取关键实体及其关系，最多 %d 个实体，每个实体最多 10 条关系。\
            关系名使用英文大写（如 WORKS_FOR、LIVES_IN、DEPENDS_ON、RELATED_TO 等）。\
            严格按 JSON 数组格式返回，不要其他内容：\
            [{"name":"实体名","type":"Person/Tech/Concept/Product/Other","relations":["RELATES_TO->目标实体名"]}]\

            文本：%s""".formatted(MAX_ENTITIES, text);

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你是一个知识图谱实体提取工具。只返回 JSON 数组。"),
                    Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.1);
            body.put("max_tokens", 8000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey());

            String response = restTemplate.postForObject(properties.getApiUrl(),
                    new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.get("choices").get(0).get("message").get("content").asText();

            // 从回复中提取 JSON 数组
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String json = content.substring(start, end + 1);
                List<EntityData> entities = objectMapper.readValue(json, new TypeReference<>() {});
                return entities.stream().map(e -> new GraphService.EntityRel(
                        e.name, e.type != null ? e.type : "Other",
                        e.relations != null ? e.relations : List.of()
                )).limit(MAX_ENTITIES).toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Entity extraction failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static class EntityData {
        public String name;
        public String type;
        public List<String> relations;
    }
}
