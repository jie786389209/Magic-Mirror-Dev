package com.magicmirror.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;       // "user" | "assistant" | "tool"
    private String content;

    // tool 角色专用
    private String toolCallId;
    private String toolName;

    // assistant 角色的工具调用
    @Builder.Default
    private List<Map<String, Object>> toolCalls = List.of();

    private String timestamp;
}
