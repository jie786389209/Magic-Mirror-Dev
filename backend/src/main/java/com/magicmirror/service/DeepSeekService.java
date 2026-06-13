package com.magicmirror.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.config.DeepSeekProperties;
import com.magicmirror.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeepSeekService {

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public DeepSeekService(DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = createStreamingRestTemplate();
    }

    /**
     * 创建支持流式读取的 RestTemplate（无超时限制）
     */
    private RestTemplate createStreamingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(5 * 60 * 1000); // 5 分钟流式读取
        return new RestTemplate(factory);
    }

    /**
     * 流式调用 DeepSeek API
     *
     * @param message   用户当前消息
     * @param history   历史对话
     * @param onChunk   每收到一个文本块的回调
     * @return 完整回复内容
     */
    public String chatStream(String message, List<ChatMessage> history, Consumer<String> onChunk) {
        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", """
    你是一个专业的 AI 开发助手，擅长代码分析与技术问题解答。

    请严格遵守以下 Markdown 格式规范：
    1. 每个段落之间用空行分隔
    2. 列表项（- 或 1.）必须独占一行，前面必须有换行
    3. 标题（## 等）前后必须有空行
    4. 代码块（```）前后必须有空行
    5. 粗体标题（**xxx**）作为段落标题时独占一行、前后换行
    6. 禁止将多个列表项挤在同一行

    请用中文回答。"""));
        for (ChatMessage msg : history) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));

        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", properties.getTemperature());

        // thinking 配置
        Map<String, String> thinking = new LinkedHashMap<>();
        thinking.put("type", properties.getThinkingType());
        body.put("thinking", thinking);
        body.put("reasoning_effort", properties.getReasoningEffort());

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("Calling DeepSeek API: model={}, message length={}", properties.getModel(), message.length());

        StringBuilder fullContent = new StringBuilder();

        try {
            restTemplate.execute(
                    properties.getApiUrl(),
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(properties.getApiKey());
                        objectMapper.writeValue(request.getBody(), body);
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // DeepSeek SSE 格式: "data: {...}"
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    try {
                                        JsonNode node = objectMapper.readTree(data);
                                        JsonNode choices = node.get("choices");
                                        if (choices != null && choices.isArray() && choices.size() > 0) {
                                            JsonNode delta = choices.get(0).get("delta");
                                            if (delta != null) {
                                                // 普通回复内容
                                                JsonNode contentNode = delta.get("content");
                                                if (contentNode != null && !contentNode.isNull()) {
                                                    String text = contentNode.asText();
                                                    fullContent.append(text);
                                                    onChunk.accept(text);
                                                }
                                                // 推理过程（thinking enabled 时返回）
                                                JsonNode reasoningNode = delta.get("reasoning_content");
                                                if (reasoningNode != null && !reasoningNode.isNull()) {
                                                    String reasoning = reasoningNode.asText();
                                                    onChunk.accept("[THINK]" + reasoning + "[/THINK]");
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Failed to parse SSE chunk: {}", data, e);
                                    }
                                }
                            }
                        }
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new RuntimeException("调用 DeepSeek API 失败: " + e.getMessage(), e);
        }

        log.info("DeepSeek response completed, total length={}", fullContent.length());
        return fullContent.toString();
    }
}
