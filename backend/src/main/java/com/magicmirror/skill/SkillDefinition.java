package com.magicmirror.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Skill 定义模型
 */
@Data
public class SkillDefinition {
    private String name;
    private String description;
    private List<String> triggers;       // 触发关键词
    private boolean enabled = true;
    private List<SkillStep> steps;
    private Map<String, String> params;  // 参数声明 { "file": "要审查的文件路径" }
}
