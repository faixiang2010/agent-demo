package com.demo.deepseek.controller;

import com.demo.deepseek.model.ApiDefinition;
import com.demo.deepseek.service.DeepseekProxyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接口定义控制器：向前端返回全部接口的元数据，用于动态渲染参数表单。
 */
@RestController
public class DefinitionController {

    private final DeepseekProxyService service;

    public DefinitionController(DeepseekProxyService service) {
        this.service = service;
    }

    @GetMapping("/api/definitions")
    public List<ApiDefinition> definitions() {
        return service.getDefinitions();
    }
}
