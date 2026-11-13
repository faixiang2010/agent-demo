package com.demo.deepseek.model;

import java.util.List;

/**
 * 接口参数的元数据定义，用于前端动态渲染表单控件。
 *
 * @param name         参数名
 * @param label        显示标签
 * @param description  参数说明
 * @param type         控件类型（text/number/select/switch/json/textarea/file）
 * @param in           参数归属（body/query/path/form/file）
 * @param required     是否必填
 * @param defaultValue 默认值
 * @param options      select 控件的可选值
 */
public record ParamDef(
        String name,
        String label,
        String description,
        String type,
        String in,
        boolean required,
        Object defaultValue,
        List<String> options
) {
}
