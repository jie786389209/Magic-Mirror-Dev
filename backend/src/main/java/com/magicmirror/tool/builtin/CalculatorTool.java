package com.magicmirror.tool.builtin;

import com.magicmirror.tool.api.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "执行数学计算表达式，支持 + - * / % 和括号";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "string");
        expr.put("description", "数学表达式，如 (3 + 5) * 2");
        properties.put("expression", expr);

        schema.put("properties", properties);
        schema.put("required", java.util.List.of("expression"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String expression = (String) arguments.get("expression");
        if (expression == null || expression.isBlank()) {
            return "错误：请提供数学表达式";
        }
        if (!expression.matches("[0-9\\+\\-\\*\\/\\(\\)\\.\\%\\s]+")) {
            return "错误：表达式包含不允许的字符";
        }
        try {
            double result = new ExprParser(expression).parse();
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            log.warn("Calculator error for: {}", expression, e);
            return "计算错误：" + e.getMessage();
        }
    }

    /** 简单递归下降解析器，支持 + - * / % ( ) */
    private static class ExprParser {
        private final String input;
        private int pos;

        ExprParser(String input) {
            this.input = input;
            this.pos = 0;
        }

        double parse() {
            double val = term();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '+') { pos++; val += term(); }
                else if (c == '-') { pos++; val -= term(); }
                else break;
            }
            return val;
        }

        private double term() {
            double val = factor();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '*') { pos++; val *= factor(); }
                else if (c == '/') { pos++; val /= factor(); }
                else if (c == '%') { pos++; val %= factor(); }
                else break;
            }
            return val;
        }

        private double factor() {
            skipWhitespace();
            if (pos >= input.length()) throw new IllegalArgumentException("表达式不完整");
            char c = input.charAt(pos);
            if (c == '(') {
                pos++;
                double val = parse();
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ')') {
                    pos++;
                } else {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return val;
            }
            if (c == '-') {
                pos++;
                return -factor();
            }
            if (c == '+') {
                pos++;
                return factor();
            }
            return number();
        }

        private double number() {
            skipWhitespace();
            int start = pos;
            while (pos < input.length() &&
                    (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("期望数字，位于 " + start);
            return Double.parseDouble(input.substring(start, pos));
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
