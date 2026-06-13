package com.magicmirror.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    private String apiKey;
    private String apiUrl = "https://api.deepseek.com/chat/completions";
    private String model = "deepseek-v4-pro";
    private String thinkingType = "enabled";
    private String reasoningEffort = "high";
    private Double temperature = 0.7;
}
