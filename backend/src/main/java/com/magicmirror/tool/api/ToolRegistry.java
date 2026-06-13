package com.magicmirror.tool.api;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Set<String> disabledTools = new LinkedHashSet<>();
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

    public boolean isEnabled(String name) {
        return !disabledTools.contains(name);
    }

    public void enable(String name) {
        disabledTools.remove(name);
        log.info("Tool enabled: {}", name);
    }

    public void disable(String name) {
        disabledTools.add(name);
        log.info("Tool disabled: {}", name);
    }

    public List<Map<String, Object>> getToolStates() {
        List<Map<String, Object>> states = new ArrayList<>();
        for (Tool tool : tools.values()) {
            states.add(Map.of(
                    "name", tool.getName(),
                    "description", tool.getDescription(),
                    "enabled", isEnabled(tool.getName())
            ));
        }
        return states;
    }

    /** 生成 DeepSeek Function Calling 格式的工具列表（仅启用工具） */
    public List<Map<String, Object>> toDeepSeekTools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (!isEnabled(tool.getName())) continue;
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
