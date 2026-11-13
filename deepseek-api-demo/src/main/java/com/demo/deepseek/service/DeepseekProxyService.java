package com.demo.deepseek.service;

import com.demo.deepseek.model.ApiCallRequest;
import com.demo.deepseek.model.ApiDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeepSeek API 代理服务：负责加载接口元数据，并将请求转发到 DeepSeek，
 * 支持非流式、SSE 流式透传以及 multipart 文件上传。
 *
 * <p>说明：所有接口的 baseUrl 统一为 {@code https://api.deepseek.com}，
 * beta / anthropic 等前缀通过接口 path 携带（如 {@code /beta/completions}、
 * {@code /anthropic/v1/messages}），避免 WebClient 拼接时替换路径。</p>
 */
@Service
public class DeepseekProxyService {

    private static final Logger log = LoggerFactory.getLogger(DeepseekProxyService.class);

    /** 明确 charset=UTF-8 的 JSON 响应类型，确保中文不乱码。 */
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final List<ApiDefinition> definitions;

    /** 按 baseUrl 缓存的 WebClient 实例，避免每次请求重复创建。 */
    private final Map<String, WebClient> clients = new ConcurrentHashMap<>();

    public DeepseekProxyService(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("api-definitions.json")) {
            if (in == null) {
                throw new IllegalStateException("未找到 api-definitions.json");
            }
            this.definitions = objectMapper.readValue(in, new TypeReference<List<ApiDefinition>>() {
            });
        }
    }

    /** 返回全部接口定义（驱动前端渲染）。 */
    public List<ApiDefinition> getDefinitions() {
        return definitions;
    }

    /** 按 id 查找接口定义。 */
    public ApiDefinition getDefinition(String id) {
        return definitions.stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    /** 非流式调用，透传 DeepSeek 的 JSON 响应。 */
    public Mono<ResponseEntity<String>> call(ApiCallRequest req) {
        ApiDefinition def = getDefinition(req.endpoint());
        if (def == null) {
            return Mono.just(ResponseEntity.badRequest().body("未知接口: " + req.endpoint()));
        }
        if ("multipart/form-data".equals(def.contentType())) {
            return Mono.just(ResponseEntity.badRequest().body("该接口需通过 /api/call/upload 上传文件"));
        }
        log.info("代理调用 {} {} -> {}", def.method(), req.endpoint(), fullUrl(def, req));
        WebClient.RequestBodySpec spec = buildRequest(def, req);
        return spec.exchangeToMono(resp -> resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.info("代理响应 {} status={}", req.endpoint(), resp.statusCode().value());
                    ResponseEntity.BodyBuilder builder = ResponseEntity.status(resp.statusCode())
                            .contentType(JSON_UTF8);
                    copyUpstreamHeader(resp, builder, "x-ds-trace-id");
                    return builder.body(body);
                }));
    }

    /** 流式调用，透传 DeepSeek 的 SSE 事件流。 */
    public Mono<ResponseEntity<Flux<DataBuffer>>> callStream(ApiCallRequest req) {
        ApiDefinition def = getDefinition(req.endpoint());
        if (def == null) {
            return Mono.just(ResponseEntity.badRequest().body(Flux.empty()));
        }
        log.info("代理流式调用 {} {} -> {}", def.method(), req.endpoint(), fullUrl(def, req));
        WebClient.RequestBodySpec spec = buildRequest(def, req);
        // 使用 toEntityFlux 正确透传 SSE 流式响应体（exchangeToMono + bodyToFlux 会导致响应体为空）
        return spec.retrieve()
                .toEntityFlux(DataBuffer.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    String err = e.getResponseBodyAsString();
                    return Mono.just(ResponseEntity.status(e.getStatusCode())
                            .contentType(JSON_UTF8)
                            .body(Flux.just(stringToBuffer(err == null ? "" : err))));
                });
    }

    /** 文件上传（Files API），转发 multipart 请求。 */
    public Mono<ResponseEntity<String>> upload(String endpoint, String apiKey, String baseUrl,
                                              String purpose, FilePart file) {
        ApiDefinition def = getDefinition(endpoint);
        if (def == null) {
            return Mono.just(ResponseEntity.badRequest().body("未知接口: " + endpoint));
        }
        String resolvedBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : def.baseUrl();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file);
        builder.part("purpose", purpose);

        WebClient client = clientFor(resolvedBaseUrl);
        WebClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(def.method()))
                .uri(def.path());

        applyAuthAndHeaders(spec, def, apiKey);
        spec.bodyValue(builder.build());

        log.info("代理上传 {} {} -> {}{}", def.method(), endpoint, resolvedBaseUrl, def.path());
        return spec.exchangeToMono(resp -> resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> ResponseEntity.status(resp.statusCode())
                        .contentType(JSON_UTF8)
                        .body(body)));
    }

    /** 根据接口定义和请求参数构造 WebClient 请求。 */
    private WebClient.RequestBodySpec buildRequest(ApiDefinition def, ApiCallRequest req) {
        String baseUrl = effectiveBaseUrl(def, req);
        String path = resolvePath(def.path(), req.path());
        HttpMethod method = HttpMethod.valueOf(def.method());

        WebClient client = clientFor(baseUrl);
        WebClient.RequestBodySpec spec = client.method(method)
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    if (req.query() != null) {
                        req.query().forEach((k, v) -> {
                            if (v != null) {
                                builder.queryParam(k, v);
                            }
                        });
                    }
                    return builder.build();
                });

        applyAuthAndHeaders(spec, def, req.apiKey());

        if (req.body() != null && method != HttpMethod.GET) {
            spec.contentType(MediaType.APPLICATION_JSON);
            spec.bodyValue(req.body());
        }
        return spec;
    }

    private String effectiveBaseUrl(ApiDefinition def, ApiCallRequest req) {
        return (req.baseUrl() != null && !req.baseUrl().isBlank()) ? req.baseUrl() : def.baseUrl();
    }

    private String fullUrl(ApiDefinition def, ApiCallRequest req) {
        return effectiveBaseUrl(def, req) + resolvePath(def.path(), req.path());
    }

    /** 按 baseUrl 获取（并缓存）WebClient 实例。 */
    private WebClient clientFor(String baseUrl) {
        return clients.computeIfAbsent(baseUrl, WebClient::create);
    }

    /** 设置鉴权头与额外固定请求头。 */
    private void applyAuthAndHeaders(WebClient.RequestBodySpec spec, ApiDefinition def, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            if ("x-api-key".equals(def.authMode())) {
                spec.header("x-api-key", apiKey);
            } else {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }
        if (def.extraHeaders() != null) {
            def.extraHeaders().forEach(spec::header);
        }
    }

    /** 用路径参数替换 path 中的 {xxx} 占位符。 */
    private String resolvePath(String path, Map<String, Object> pathParams) {
        if (pathParams == null || pathParams.isEmpty()) {
            return path;
        }
        String resolved = path;
        for (Map.Entry<String, Object> e : pathParams.entrySet()) {
            resolved = resolved.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return resolved;
    }

    private DataBuffer stringToBuffer(String s) {
        return DefaultDataBufferFactory.sharedInstance.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 透传上游 DeepSeek 的指定响应头（用于排查请求，如 x-request-id）。 */
    private void copyUpstreamHeader(ClientResponse resp, ResponseEntity.BodyBuilder builder, String name) {
        List<String> values = resp.headers().header(name);
        if (values != null && !values.isEmpty()) {
            builder.header(name, values.get(0));
        }
    }
}
