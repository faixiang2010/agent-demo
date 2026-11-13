package com.example.agentscope.reactagent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 简单工具集：演示 @Tool 注解与 Toolkit 的注册方式。
 * <p>每个 @Tool 方法会被 Toolkit 通过反射自动发现并注册为 Agent 可调用的工具。
 */
public class SimpleTools {

    @Tool(
            name = "get_current_time",
            description = "获取当前时间，可指定 IANA 时区",
            readOnly = true,
            concurrencySafe = true
    )
    public String getCurrentTime(
            @ToolParam(name = "zone", description = "IANA 时区ID，如 Asia/Shanghai、UTC，留空则使用系统默认时区") String zone
    ) {
        ZoneId zoneId = (zone == null || zone.isBlank())
                ? ZoneId.systemDefault()
                : ZoneId.of(zone);
        String time = LocalDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "当前时间" + (zone == null || zone.isBlank() ? "" : "（" + zoneId + "）") + "：" + time;
    }

    @Tool(
            name = "calculate",
            description = "计算两个数的四则运算结果",
            readOnly = true,
            concurrencySafe = true
    )
    public String calculate(
            @ToolParam(name = "a", description = "第一个数") double a,
            @ToolParam(name = "operator", description = "运算符：+、-、*、/") String operator,
            @ToolParam(name = "b", description = "第二个数") double b
    ) {
        double result;
        switch (operator) {
            case "+" -> result = a + b;
            case "-" -> result = a - b;
            case "*" -> result = a * b;
            case "/" -> {
                if (b == 0) return "错误：除数不能为零";
                result = a / b;
            }
            default -> {
                return "错误：不支持的运算符 '" + operator + "'，请使用 +、-、*、/";
            }
        }
        return a + " " + operator + " " + b + " = " + result;
    }

    @Tool(
            name = "word_count",
            description = "统计文本的字符数和词数",
            readOnly = true,
            concurrencySafe = true
    )
    public String wordCount(
            @ToolParam(name = "text", description = "要统计的文本内容") String text
    ) {
        int charCount = text.length();
        int wordCount = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        return "字符数：" + charCount + "，词数：" + wordCount;
    }
}