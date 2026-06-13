package com.magicmirror.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    private String id;
    private String content;       // 记忆内容
    private String category;      // 分类：preference / project / fact
    private Instant createdAt;
    private double similarity;    // 向量相似度（检索时用）
}
