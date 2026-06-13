package com.magicmirror.tool.builtin;

import com.magicmirror.tool.api.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetCurrentTimeTool implements Tool {

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "获取当前日期和时间，支持指定时区";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> tz = new LinkedHashMap<>();
        tz.put("type", "string");
        tz.put("description", "时区，如 Asia/Shanghai、America/New_York，默认 Asia/Shanghai");
        properties.put("timezone", tz);

        schema.put("properties", properties);
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String timezone = (String) arguments.getOrDefault("timezone", "Asia/Shanghai");
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        } catch (Exception e) {
            return "时区错误: " + timezone + "，请使用标准时区格式（如 Asia/Shanghai）";
        }
    }
}
