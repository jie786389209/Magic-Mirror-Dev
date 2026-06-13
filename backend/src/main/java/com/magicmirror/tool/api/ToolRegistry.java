package com.magicmirror.tool.api;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具注册中心 —— 管理所有可用工具
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final List<Tool> toolList;

    public ToolRegistry(List<Tool> toolList) {
        this.toolList = toolList;
    }

    @PostConstruct
    public void init() {
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("Registered tool: {}", tool.getName());
        }
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<Tool> getAll() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 生成 DeepSeek Function Calling 格式的工具列表
     */
    public List<Map<String, Object>> toDeepSeekTools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());
            func.put("function", function);
            result.add(func);
        }
        return result;
    }
}
