package com.magicmirror.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.config.DeepSeekProperties;
import com.magicmirror.memory.MemoryService;
import com.magicmirror.model.ChatMessage;
import com.magicmirror.rag.DocumentService;
import com.magicmirror.skill.SkillEngine;
import com.magicmirror.tool.api.ToolExecutor;
import com.magicmirror.tool.api.ToolRegistry;
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
public class ChatService {

    private final DeepSeekProperties properties;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final SkillEngine skillEngine;
    private final MemoryService memoryService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ChatService(DeepSeekProperties properties, ToolRegistry toolRegistry,
                       ToolExecutor toolExecutor, SkillEngine skillEngine,
                       MemoryService memoryService, DocumentService documentService,
                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.skillEngine = skillEngine;
        this.memoryService = memoryService;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(5 * 60 * 1000);
        return new RestTemplate(factory);
    }

    /**
     * 流式对话（支持 Function Calling）
     */
    public void chatStream(String userMessage, List<ChatMessage> history, boolean ragEnabled, String sessionId, Consumer<String> onChunk) {
        // 用于缓冲 assistant 纯文本回复（不含标记和工具输出）
        StringBuilder assistantBuffer = new StringBuilder();
        Consumer<String> wrappedOnChunk = chunk -> {
            String c = chunk.trim();
            if (!c.startsWith("[THINK]") && !c.startsWith("[/THINK]")
                    && !c.startsWith("[TOOL_") && !c.startsWith("[SKILL]")
                    && !c.startsWith("[/SKILL]") && !c.startsWith("🔧")
                    && !c.startsWith("结果:") && !c.startsWith("\n🔧")
                    && !c.startsWith("\n结果:") && !c.isEmpty()) {
                assistantBuffer.append(chunk);
            }
            onChunk.accept(chunk);
        };

        // Step 0: 尝试匹配 Skill
        var matchedSkill = skillEngine.match(userMessage);
        if (matchedSkill.isPresent()) {
            var skill = matchedSkill.get();
            log.info("Skill matched: {}", skill.getName());
            wrappedOnChunk.accept("[SKILL]" + skill.getName() + "[/SKILL]");
            var params = skillEngine.extractParams(skill, userMessage);
            skillEngine.execute(skill, params, wrappedOnChunk, null);
            String buf = assistantBuffer.toString().trim();
            if (!buf.isEmpty()) {
                memoryService.saveShortTerm(sessionId,
                        ChatMessage.builder().role("assistant").content(buf).build());
            }
            return;
        }

        // 保存用户消息到短期记忆
        memoryService.saveShortTerm(sessionId,
                ChatMessage.builder().role("user").content(userMessage).build());

        // 自动升级：包含 "记住" / "我是" / "项目" 关键词的消息升级为长期记忆
        boolean shouldRemember = userMessage.contains("记住") || userMessage.contains("我是")
                || userMessage.contains("项目用") || userMessage.contains("偏好");
        if (shouldRemember) {
            memoryService.saveLongTerm(userMessage);
            log.info("Auto-promoted to long-term memory: {}", userMessage.substring(0, Math.min(60, userMessage.length())));
        }

        List<Map<String, Object>> messages = buildMessages(userMessage, history, ragEnabled);

        // Step 1: 首次调用，检测是否需要工具
        var toolCallResult = callWithTools(messages);
        if (!toolCallResult.toolCalls.isEmpty()) {
            log.info("DeepSeek requested {} tool call(s)", toolCallResult.toolCalls.size());
            wrappedOnChunk.accept("[TOOL_START]");

            // 执行工具
            for (var tc : toolCallResult.toolCalls) {
                String toolName = (String) tc.get("name");
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) tc.getOrDefault("arguments", Map.of());
                var result = toolExecutor.execute(toolName, args);
                log.info("Tool result: {} -> {}", toolName, result.result());

                wrappedOnChunk.accept("\n🔧 调用工具: **" + toolName + "**");
                wrappedOnChunk.accept("\n结果: " + result.result() + "\n\n");

                String callId = tc.containsKey("id") ? (String) tc.get("id") : "call_" + System.currentTimeMillis();

                // 保存工具调用到短期记忆
                memoryService.saveShortTerm(sessionId,
                        ChatMessage.builder().role("assistant").content("")
                                .toolCalls(List.of(Map.of("id", callId,
                                        "type", "function",
                                        "function", Map.of("name", toolName))))
                                .build());
                memoryService.saveShortTerm(sessionId,
                        ChatMessage.builder().role("tool").content(result.result())
                                .toolCallId(callId).toolName(toolName).build());

                // 将工具调用和结果加入消息（保留 reasoning_content）
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", "");
                if (toolCallResult.reasoningContent != null) {
                    assistantMsg.put("reasoning_content", toolCallResult.reasoningContent);
                }
                assistantMsg.put("tool_calls", List.of(Map.of(
                        "id", callId,
                        "type", "function",
                        "function", Map.of("name", toolName, "arguments", objectMapper.valueToTree(args).toString())
                )));
                messages.add(assistantMsg);
                messages.add(Map.of("role", "tool", "content", result.result(),
                        "tool_call_id", callId));
            }
            wrappedOnChunk.accept("[TOOL_END]");

            // Step 2: 带上工具结果再次调用（thinking 保持开启）
            streamResponse(messages, wrappedOnChunk, true);
        } else if (toolCallResult.content != null) {
            wrappedOnChunk.accept(toolCallResult.content);
        } else {
            streamResponse(messages, wrappedOnChunk, true);
        }

        // 保存 assistant 完整回复到短期记忆
        String fullReply = assistantBuffer.toString().trim();
        if (!fullReply.isEmpty()) {
            memoryService.saveShortTerm(sessionId,
                    ChatMessage.builder().role("assistant").content(fullReply).build());
        }
    }

    /**
     * 调用 DeepSeek（非流式），检测 tool_calls
     */
    private ToolCallResult callWithTools(List<Map<String, Object>> messages) {
        Map<String, Object> body = buildRequestBody(messages, false, true);
        body.put("tools", toolRegistry.toDeepSeekTools());
        body.put("tool_choice", "auto");
        log.info("DeepSeek tool-detect request body:\n{}", toPrettyJson(body));

        try {
            String response = restTemplate.postForObject(properties.getApiUrl(),
                    createHttpEntity(body), String.class);
            log.info("DeepSeek tool-detect response:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                            objectMapper.readTree(response)));
            JsonNode node = objectMapper.readTree(response);
            JsonNode choice = node.get("choices").get(0);
            JsonNode msg = choice.get("message");

            // 捕获 reasoning_content（thinking 模式必须回传）
            JsonNode reasoningNode = msg.get("reasoning_content");
            String reasoningContent = (reasoningNode != null && !reasoningNode.isNull())
                    ? reasoningNode.asText() : null;

            // 检查 tool_calls
            JsonNode toolCallsNode = msg.get("tool_calls");
            if (toolCallsNode != null && toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (JsonNode tc : toolCallsNode) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.get("id").asText());
                    tcMap.put("name", tc.get("function").get("name").asText());
                    tcMap.put("arguments", parseFunctionArguments(
                            tc.get("function").get("arguments").asText()));
                    toolCalls.add(tcMap);
                }
                return new ToolCallResult(toolCalls, null, reasoningContent);
            }

            // 普通文本回复
            JsonNode content = msg.get("content");
            return new ToolCallResult(List.of(),
                    content != null ? content.asText() : "",
                    reasoningContent);

        } catch (Exception e) {
            log.error("Tool call request failed", e);
            return new ToolCallResult(List.of(), "[错误] " + e.getMessage(), null);
        }
    }

    /**
     * 流式调用 DeepSeek
     */
    private void streamResponse(List<Map<String, Object>> messages, Consumer<String> onChunk, boolean enableThinking) {
        Map<String, Object> body = buildRequestBody(messages, true, enableThinking);
        log.info("DeepSeek stream request: {}", toPrettyJson(body));

        StringBuilder streamLog = new StringBuilder();
        StringBuilder thinkBuf = new StringBuilder();
        try {
            restTemplate.execute(properties.getApiUrl(), HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(properties.getApiKey());
                        objectMapper.writeValue(request.getBody(), body);
                    },
                    response -> {
                        try (var reader = new BufferedReader(
                                new InputStreamReader(response.getBody()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if (streamLog.length() < 20_000) {
                                        streamLog.append(data).append("\n");
                                    }
                                    if ("[DONE]".equals(data)) break;
                                    try {
                                        JsonNode node = objectMapper.readTree(data);
                                        JsonNode choices = node.get("choices");
                                        if (choices != null && choices.size() > 0) {
                                            JsonNode delta = choices.get(0).get("delta");
                                            if (delta != null) {
                                                JsonNode content = delta.get("content");
                                                if (content != null && !content.isNull()) {
                                                    // 先刷出缓存的 thinking（一次性）
                                                    if (!thinkBuf.isEmpty()) {
                                                        onChunk.accept("[THINK]" + thinkBuf + "[/THINK]");
                                                        thinkBuf.setLength(0);
                                                    }
                                                    onChunk.accept(content.asText());
                                                }
                                                JsonNode reasoning = delta.get("reasoning_content");
                                                if (reasoning != null && !reasoning.isNull()) {
                                                    thinkBuf.append(reasoning.asText());
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Parse error: {}", data, e);
                                    }
                                }
                            }
                        }
                        return null;
                    });
            // 兜底：flush 剩余的 thinking
            if (!thinkBuf.isEmpty()) {
                onChunk.accept("[THINK]" + thinkBuf + "[/THINK]");
            }
            log.info("DeepSeek stream response ({} chars):\n{}", streamLog.length(), streamLog);
        } catch (Exception e) {
            log.error("Stream error", e);
            onChunk.accept("\n[流式调用失败: " + e.getMessage() + "]");
        }
    }

    private List<Map<String, Object>> buildMessages(String userMessage, List<ChatMessage> history, boolean ragEnabled) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 构建系统提示（含记忆上下文）
        String memoryCtx = memoryService.buildMemoryContext("default", userMessage);
        String systemPrompt = """
            你是一个专业的 AI 开发助手，具备工具调用能力。
            当用户的问题需要计算、查询时间或搜索代码时，请主动调用对应工具。
            工具调用后，基于工具返回的结果给出清晰的回答。
            请严格遵守 Markdown 格式规范：
            - 列表项独占一行、标题前后有空行、表格每行独占一行、代码块前后有空行。
            请用中文回答。""";

        if (!memoryCtx.isEmpty()) {
            systemPrompt = memoryCtx + "\n---\n" + systemPrompt;
        }

        // RAG：仅在开关开启时检索
        if (ragEnabled) {
            var docResults = documentService.search(userMessage, 5);
            docResults = docResults.stream().filter(d -> {
                Object score = d.get("score");
                return score instanceof Number && ((Number) score).doubleValue() >= 0.7;
            }).limit(3).toList();
            if (!docResults.isEmpty()) {
            StringBuilder ragCtx = new StringBuilder("## 参考文档\n");
            for (var doc : docResults) {
                ragCtx.append(String.format("- [%s] %s\n",
                        doc.getOrDefault("filename", ""),
                        doc.get("content")));
            }
                systemPrompt = ragCtx + "\n---\n" + systemPrompt;
            }
        }

        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatMessage msg : history) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages, boolean stream, boolean enableThinking) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("temperature", properties.getTemperature());

        if (enableThinking) {
            Map<String, String> thinking = new LinkedHashMap<>();
            thinking.put("type", properties.getThinkingType());
            body.put("thinking", thinking);
            body.put("reasoning_effort", properties.getReasoningEffort());
        }
        return body;
    }

    private HttpEntity<Map<String, Object>> createHttpEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private String toPrettyJson(Map<String, Object> body) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
        } catch (JsonProcessingException e) {
            return body.toString();
        }
    }

    private Map<String, Object> parseFunctionArguments(String args) {
        try {
            return objectMapper.readValue(args, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record ToolCallResult(List<Map<String, Object>> toolCalls, String content, String reasoningContent) {}
}
