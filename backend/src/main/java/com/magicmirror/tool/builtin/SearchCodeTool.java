package com.magicmirror.tool.builtin;

import com.magicmirror.tool.api.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SearchCodeTool implements Tool {

    @Override
    public String getName() {
        return "search_code";
    }

    @Override
    public String getDescription() {
        return "在项目代码中搜索关键词，返回匹配的文件路径和行号。（当前为演示版本）";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> kw = new LinkedHashMap<>();
        kw.put("type", "string");
        kw.put("description", "搜索关键词");
        properties.put("keyword", kw);

        schema.put("properties", properties);
        schema.put("required", java.util.List.of("keyword"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        // 演示版本，后续对接 Elasticsearch
        return String.format("""
                [演示模式] 搜索关键词: "%s"
                搜索范围: 项目代码
                找到 0 个结果。代码搜索功能将在 Elasticsearch 集成后上线。""", keyword);
    }
}
