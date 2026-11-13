package com.demo.deepseek.controller;

import com.demo.deepseek.model.ApiCallRequest;
import com.demo.deepseek.service.DeepseekProxyService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一代理控制器：前端将请求转发到本控制器，再由服务层代理到 DeepSeek。
 *
 * <ul>
 *   <li>{@code POST /api/call}        非流式调用</li>
 *   <li>{@code POST /api/call/stream} SSE 流式调用</li>
 *   <li>{@code POST /api/call/upload} multipart 文件上传</li>
 * </ul>
 */
@RestController
public class ProxyController {

    private final DeepseekProxyService service;

    public ProxyController(DeepseekProxyService service) {
        this.service = service;
    }

    @PostMapping("/api/call")
    public Mono<ResponseEntity<String>> call(@RequestBody ApiCallRequest req) {
        return service.call(req);
    }

    @PostMapping("/api/call/stream")
    public Mono<ResponseEntity<Flux<DataBuffer>>> callStream(@RequestBody ApiCallRequest req) {
        return service.callStream(req);
    }

    /**
     * 文件上传：WebFlux 中 {@code @RequestParam} 无法读取 multipart 表单字段，
     * 因此统一通过 {@link ServerWebExchange#getMultipartData()} 解析。
     */
    @PostMapping(value = "/api/call/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> upload(ServerWebExchange exchange) {
        return exchange.getMultipartData().flatMap(parts -> {
            Part filePart = parts.getFirst("file");
            if (!(filePart instanceof FilePart file)) {
                return Mono.just(ResponseEntity.badRequest().body("缺少文件字段 file"));
            }
            String endpoint = formValue(parts, "endpoint");
            if (endpoint == null || endpoint.isBlank()) {
                return Mono.just(ResponseEntity.badRequest().body("缺少参数 endpoint"));
            }
            String apiKey = formValue(parts, "apiKey");
            String baseUrl = formValue(parts, "baseUrl");
            String purpose = formValue(parts, "purpose");
            if (purpose == null || purpose.isBlank()) {
                purpose = "user_data";
            }
            return service.upload(endpoint, apiKey, baseUrl, purpose, file);
        });
    }

    /** 读取 multipart 表单字段的文本值，不存在时返回 null。 */
    private String formValue(MultiValueMap<String, Part> parts, String name) {
        Part part = parts.getFirst(name);
        if (part instanceof FormFieldPart field) {
            return field.value();
        }
        return null;
    }
}
