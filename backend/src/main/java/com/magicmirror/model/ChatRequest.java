package com.magicmirror.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    @Builder.Default
    private List<ChatMessage> history = new ArrayList<>();

    private String model;

    private Double temperature;

    private Integer maxTokens;

    @Builder.Default
    private boolean ragEnabled = false; // 是否启用知识库检索
}
