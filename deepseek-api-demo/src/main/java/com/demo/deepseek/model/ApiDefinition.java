package com.demo.deepseek.model;

import java.util.List;
import java.util.Map;

/**
 * 单个 API 场景的定义（一个物理接口可对应多个使用场景，如"基础对话""思考模式""视觉理解"）。
 *
 * <p>由 resources 下的 api-definitions.json 反序列化得到，驱动前端动态渲染参数表单，
 * 并指导后端如何代理转发请求到 DeepSeek。</p>
 *
 * @param id           场景唯一标识
 * @param group        场景分类（基础信息 / 对话补全 / 多模态 / 补全 / 网页检索 / Responses / 文件管理 / Anthropic 兼容）
 * @param name         场景显示名称
 * @param description  场景说明
 * @param method       HTTP 方法（GET/POST/DELETE）
 * @param path         相对路径（含 /beta、/anthropic 等前缀），可用 {xxx} 占位路径参数
 * @param baseUrl      默认 Base URL
 * @param streamable   是否支持流式输出
 * @param contentType  请求体类型（application/json 或 multipart/form-data）
 * @param authMode     鉴权方式（bearer 或 x-api-key）
 * @param extraHeaders 额外固定请求头（如 anthropic-version）
 * @param params       参数定义列表（驱动表单）
 * @param example      示例请求体（用于预填 JSON 编辑器，__BUILTIN_IMAGE__ 为内置示例图占位符）
 * @param imageKind    图片输入类型（chat / responses / fileRef，为空表示该场景不涉及图片）
 * @param notes        使用提示（如模型限制、Beta 前缀要求）
 * @param plain        大白话场景说明（一句话说清作用、效果与适用场景）
 * @param outputType   默认结果视图：chat（对话气泡）或 json（JSON 高亮）
 */
public record ApiDefinition(
        String id,
        String group,
        String name,
        String description,
        String method,
        String path,
        String baseUrl,
        boolean streamable,
        String contentType,
        String authMode,
        Map<String, String> extraHeaders,
        List<ParamDef> params,
        Object example,
        String imageKind,
        List<String> notes,
        String plain,
        String outputType
) {
}
