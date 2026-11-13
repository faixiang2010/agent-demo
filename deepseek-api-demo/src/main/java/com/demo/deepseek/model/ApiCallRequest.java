package com.demo.deepseek.model;

import java.util.Map;

/**
 * 前端调用后端代理时提交的请求体。
 *
 * @param endpoint 接口唯一标识
 * @param apiKey   DeepSeek API Key
 * @param baseUrl  可选的 Base URL（不传则使用接口定义里的默认值）
 * @param stream   是否流式输出
 * @param body     请求体（JSON 接口使用）
 * @param query    查询参数（GET/DELETE 接口使用）
 * @param path     路径参数（用于替换 path 中的 {xxx} 占位符）
 */
public record ApiCallRequest(
        String endpoint,
        String apiKey,
        String baseUrl,
        boolean stream,
        Object body,
        Map<String, Object> query,
        Map<String, Object> path
) {
}
