package com.magicmirror.tool.api;

import java.util.Map;

/**
 * 工具接口 —— 所有工具必须实现此接口
 */
public interface Tool {

    /** 工具名称（唯一标识，DeepSeek function_call 用） */
    String getName();

    /** 工具描述 */
    String getDescription();

    /** 参数 JSON Schema */
    Map<String, Object> getParametersSchema();

    /** 执行工具，返回结果文本 */
    String execute(Map<String, Object> arguments);
}
