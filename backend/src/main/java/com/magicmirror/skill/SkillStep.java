package com.magicmirror.skill;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Skill 步骤基类
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SkillStep.ToolStep.class, name = "tool"),
    @JsonSubTypes.Type(value = SkillStep.PromptStep.class, name = "prompt"),
    @JsonSubTypes.Type(value = SkillStep.ConditionStep.class, name = "condition"),
})
@Data
public abstract class SkillStep {

    /** 调用工具 */
    @Data
    public static class ToolStep extends SkillStep {
        private String tool;          // 工具名
        private Map<String, Object> params; // 参数（支持 {{var}} 模板）
    }

    /** LLM 追问 */
    @Data
    public static class PromptStep extends SkillStep {
        private String prompt;        // Prompt 模板（支持 {{var}}）
    }

    /** 条件分支 */
    @Data
    public static class ConditionStep extends SkillStep {
        private String field;         // 检查的字段（来自上一步结果）
        private String contains;      // 包含则继续
        private List<SkillStep> then; // 匹配时执行的步骤
    }
}
