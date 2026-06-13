package com.magicmirror.tool.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具执行器 —— 接收 function_call 并执行对应工具
 */
@Slf4j
@Component
public class ToolExecutor {

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 执行单个 function_call
     * @return { name, result }
     */
    public ToolResult execute(String functionName, Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        log.info("Executing tool: {} with args: {}", functionName, arguments);

        var tool = registry.get(functionName);
        if (tool.isEmpty()) {
            log.warn("Tool not found: {}", functionName);
            return new ToolResult(functionName, "错误：未找到工具 " + functionName, System.currentTimeMillis() - start);
        }

        try {
            String result = tool.get().execute(arguments != null ? arguments : Map.of());
            long duration = System.currentTimeMillis() - start;
            log.info("Tool {} completed in {}ms: {}", functionName, duration, result);
            return new ToolResult(functionName, result, duration);
        } catch (Exception e) {
            log.error("Tool {} execution failed", functionName, e);
            return new ToolResult(functionName, "工具执行异常：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    public record ToolResult(String name, String result, long durationMs) {}
}
