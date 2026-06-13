package com.magicmirror.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magicmirror.skill.SkillStep.*;
import com.magicmirror.tool.api.ToolExecutor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 执行引擎：加载、匹配、执行
 */
@Slf4j
@Component
public class SkillEngine {

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;
    private final ToolExecutor toolExecutor;

    public SkillEngine(ObjectMapper objectMapper, ToolExecutor toolExecutor) {
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    @PostConstruct
    public void loadSkills() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.json");
            for (Resource r : resources) {
                try (InputStream is = r.getInputStream()) {
                    SkillDefinition skill = objectMapper.readValue(is, SkillDefinition.class);
                    if (skill.isEnabled()) {
                        skills.put(skill.getName(), skill);
                        log.info("Loaded skill: {} (triggers: {})", skill.getName(), skill.getTriggers());
                    }
                }
            }
            log.info("Loaded {} skills total", skills.size());
        } catch (Exception e) {
            log.warn("Failed to load skills: {}", e.getMessage());
        }
    }

    /** 从用户消息中匹配 Skill */
    public Optional<SkillDefinition> match(String userMessage) {
        String msg = userMessage.toLowerCase();
        return skills.values().stream()
                .filter(s -> s.getTriggers().stream().anyMatch(t -> msg.contains(t.toLowerCase())))
                .findFirst();
    }

    /** 按名称获取 Skill */
    public Optional<SkillDefinition> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    /** 获取所有 Skills */
    public Collection<SkillDefinition> getAll() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /** 执行 Skill，结果通过 onChunk 流式输出 */
    public void execute(SkillDefinition skill, Map<String, String> inputParams,
                        Consumer<String> onChunk, Consumer<Map<String, Object>> onToolCall) {
        log.info("Executing skill: {} with params: {}", skill.getName(), inputParams);

        Map<String, String> context = new LinkedHashMap<>(inputParams);
        StringBuilder lastResult = new StringBuilder();

        for (SkillStep step : skill.getSteps()) {
            if (step instanceof ToolStep toolStep) {
                String toolName = resolveTemplate(toolStep.getTool(), context);
                Map<String, Object> resolvedParams = new LinkedHashMap<>();
                if (toolStep.getParams() != null) {
                    for (var entry : toolStep.getParams().entrySet()) {
                        Object val = entry.getValue();
                        if (val instanceof String s) {
                            resolvedParams.put(entry.getKey(), resolveTemplate(s, context));
                        } else {
                            resolvedParams.put(entry.getKey(), val);
                        }
                    }
                }
                onChunk.accept("\n🔧 调用工具: **" + toolName + "**\n");
                var result = toolExecutor.execute(toolName, resolvedParams);
                lastResult = new StringBuilder(result.result());
                context.put("_last_result", result.result());
                onChunk.accept("结果: " + result.result() + "\n\n");

            } else if (step instanceof PromptStep promptStep) {
                String prompt = resolveTemplate(promptStep.getPrompt(), context);
                // 将上一步结果拼入 prompt
                if (!lastResult.isEmpty()) {
                    prompt = "上一步结果：\n" + lastResult + "\n\n" + prompt;
                }
                onChunk.accept(prompt); // 将 prompt 作为 chunk 传给 ChatService 处理

            } else if (step instanceof ConditionStep condStep) {
                String fieldValue = context.getOrDefault(condStep.getField(), lastResult.toString());
                if (condStep.getContains() != null && fieldValue.contains(condStep.getContains())) {
                    if (condStep.getThen() != null) {
                        for (SkillStep sub : condStep.getThen()) {
                            // 递归处理子步骤（简化版）
                            executeSubStep(sub, context, onChunk, onToolCall);
                        }
                    }
                }
            }
        }
    }

    private void executeSubStep(SkillStep step, Map<String, String> context,
                                Consumer<String> onChunk, Consumer<Map<String, Object>> onToolCall) {
        if (step instanceof ToolStep toolStep) {
            String toolName = resolveTemplate(toolStep.getTool(), context);
            Map<String, Object> params = new LinkedHashMap<>();
            if (toolStep.getParams() != null) {
                for (var e : toolStep.getParams().entrySet()) {
                    params.put(e.getKey(), e.getValue() instanceof String s ? resolveTemplate(s, context) : e.getValue());
                }
            }
            onChunk.accept("\n🔧 调用工具: **" + toolName + "**\n");
            var result = toolExecutor.execute(toolName, params);
            context.put("_last_result", result.result());
            onChunk.accept("结果: " + result.result() + "\n\n");
        }
    }

    private String resolveTemplate(String template, Map<String, String> vars) {
        if (template == null) return "";
        Matcher m = Pattern.compile("\\{\\{(\\w+)}}").matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(key, m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 提取用户消息中的参数 */
    public Map<String, String> extractParams(SkillDefinition skill, String userMessage) {
        Map<String, String> params = new LinkedHashMap<>();
        if (skill.getParams() == null) return params;

        for (String paramName : skill.getParams().keySet()) {
            // 简单提取：匹配 "参数名：值" 或 "参数名: 值"
            Pattern p = Pattern.compile(paramName + "[：:]\\s*(\\S+)");
            Matcher m = p.matcher(userMessage);
            if (m.find()) {
                params.put(paramName, m.group(1));
            }
        }
        // 未匹配的参数：遍历所有触发词，取关键词后的剩余文本
        if (params.isEmpty() && !skill.getTriggers().isEmpty()) {
            for (String trigger : skill.getTriggers()) {
                int idx = userMessage.indexOf(trigger);
                if (idx >= 0) {
                    String rest = userMessage.substring(idx + trigger.length()).trim();
                    if (!rest.isEmpty() && skill.getParams().size() == 1) {
                        String key = skill.getParams().keySet().iterator().next();
                        params.put(key, rest);
                        break;
                    }
                }
            }
        }
        return params;
    }
}
