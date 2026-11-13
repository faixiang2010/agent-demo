package chatclient;

import demo.WeatherTool;
import demo.WeatherToolInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatClient 完整功能演示控制器
 * <p>
 * 演示 Spring AI Alibaba ChatClient 的核心用法，包括：
 * <ul>
 *   <li>基础对话（/basic）— 最简调用方式</li>
 *   <li>流式输出（/stream）— 实时逐字返回</li>
 *   <li>系统提示词（/system-prompt）— 设定 AI 角色</li>
 *   <li>完整响应（/full-response）— 获取元数据（Token 用量等）</li>
 *   <li>工具调用（/tool）— 注册外部工具</li>
 *   <li>多轮对话（/multi-turn）— 带上下文连续对话</li>
 *   <li>模型切换（/info）— 使用 DashScope 或 DeepSeek</li>
 * </ul>
 * <p>
 * ChatClient 通过 Spring Boot 自动配置注入，底层使用 application.yml 中配置的
 * DashScope（通义千问）作为默认模型。
 */
@RestController
@RequestMapping("/ai/chat-client")
public class MyController {

    private static final Logger log = LoggerFactory.getLogger(MyController.class);

    private final ChatClient chatClient;

    /**
     * 构造函数注入 DashScope ChatModel，构建 ChatClient
     * <p>
     * 使用与 ChatController 相同的 @Qualifier 方式注入 ChatModel，
     * 然后用 ChatClient.builder(chatModel) 创建 ChatClient 实例，
     * 通过 defaultAdvisors 在构建阶段设定全局默认值。
     *
     * @param chatModel DashScope ChatModel 实例（bean 名称 "dashScopeChatModel"）
     */
    public MyController(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    // ======================== 1. 基础对话 ========================

    /**
     * ChatClient 基础对话接口
     * <p>
     * 这是 ChatClient 最简调用方式：prompt().user().call().content()。
     * 适合单轮问答场景，无需额外配置。
     * <p>
     * 调用链说明：
     * <ul>
     *   <li>{@code .prompt()} — 创建 Prompt 构建器</li>
     *   <li>{@code .user(userInput)} — 设置用户消息</li>
     *   <li>{@code .call()} — 同步调用模型</li>
     *   <li>{@code .content()} — 提取回复文本</li>
     * </ul>
     *
     * @param userInput 用户输入，默认 "你好，请介绍一下你自己"
     * @return AI 回复文本
     */
    @GetMapping(value = "/basic", produces = "application/json;charset=UTF-8")
    public Map<String, String> basic(
            @RequestParam(defaultValue = "你好，请介绍一下你自己") String userInput) {
        String reply = this.chatClient.prompt()
                .user(userInput)
                .call()
                .content();
        Map<String, String> result = new HashMap<>();
        result.put("input", userInput);
        result.put("reply", reply);
        return result;
    }

    // ======================== 2. 流式输出 ========================

    /**
     * ChatClient 流式输出接口
     * <p>
     * 使用 {@code .stream().content()} 获取实时逐字返回的 Flux&lt;String&gt;。
     * 适用于需要实时展示生成内容的场景（如聊天 UI 的打字机效果）。
     * <p>
     * 注意：此接口为同步收集模式（collectList().block()），适合演示。
     * 生产环境中应直接返回 Flux&lt;String&gt; 或使用 SSE（Server-Sent Events）
     * 实现真正的流式传输。
     * <p>
     * 调用链说明：
     * <ul>
     *   <li>{@code .prompt().user(userInput)} — 构建 Prompt</li>
     *   <li>{@code .stream()} — 切换为流式调用</li>
     *   <li>{@code .content()} — 返回 Flux&lt;String&gt; 流</li>
     * </ul>
     *
     * @param userInput 用户输入，默认 "用三句话介绍杭州西湖"
     * @return 包含流式输出全文的结果 Map
     */
    @GetMapping(value = "/stream", produces = "application/json;charset=UTF-8")
    public Map<String, Object> stream(
            @RequestParam(defaultValue = "用三句话介绍杭州西湖") String userInput) {
        Flux<String> streamFlux = this.chatClient.prompt()
                .user(userInput)
                .stream()
                .content();

        List<String> chunks = streamFlux.collectList().block();
        String fullContent = chunks != null ? String.join("", chunks) : "";

        Map<String, Object> result = new HashMap<>();
        result.put("input", userInput);
        result.put("reply", fullContent);
        result.put("chunkCount", chunks != null ? chunks.size() : 0);
        return result;
    }

    // ======================== 3. 系统提示词 ========================

    /**
     * ChatClient 系统提示词接口
     * <p>
     * 通过 {@code .system(systemPrompt)} 为当前请求设定角色和行为约束。
     * 系统提示词在每次请求中动态指定，优先级高于 Builder 级别的 defaultSystem。
     * <p>
     * 常用场景：
     * <ul>
     *   <li>设定 AI 身份（如"你是一位资深 Java 工程师"）</li>
     *   <li>限制回答风格（如"用简洁的语言回答"）</li>
     *   <li>指定输出格式（如"以 JSON 格式返回"）</li>
     * </ul>
     *
     * @param userInput   用户输入，默认 "什么是 Spring AI"
     * @param systemPrompt 系统提示词，默认 "你是一位精通 Spring 框架的技术专家，请以专业且易懂的方式回答"
     * @return AI 回复文本
     */
    @GetMapping(value = "/system-prompt", produces = "application/json;charset=UTF-8")
    public Map<String, String> systemPrompt(
            @RequestParam(defaultValue = "什么是 Spring AI") String userInput,
            @RequestParam(defaultValue = "你是一位精通 Spring 框架的技术专家，请以专业且易懂的方式回答") String systemPrompt) {
        String reply = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .call()
                .content();
        Map<String, String> result = new HashMap<>();
        result.put("input", userInput);
        result.put("systemPrompt", systemPrompt);
        result.put("reply", reply);
        return result;
    }

    // ======================== 4. 完整响应（含元数据） ========================

    /**
     * ChatClient 完整响应接口
     * <p>
     * 使用 {@code .call().chatResponse()} 获取完整的 ChatResponse 对象，
     * 而非仅提取文本。ChatResponse 包含丰富的元数据，如：
     * <ul>
     *   <li>Token 用量（输入/输出 Token 数、总 Token 数）</li>
     *   <li>模型名称</li>
     *   <li>结束原因（stop、length、tool_calls 等）</li>
     *   <li>多个候选项（Generations）</li>
     * </ul>
     * <p>
     * 适用于需要监控 Token 消耗、记录日志或进行后处理的场景。
     *
     * @param userInput 用户输入，默认 "用一句话介绍微服务架构"
     * @return 包含回复文本和元数据的完整结果 Map
     */
    @GetMapping(value = "/full-response", produces = "application/json;charset=UTF-8")
    public Map<String, Object> fullResponse(
            @RequestParam(defaultValue = "用一句话介绍微服务架构") String userInput) {
        ChatResponse chatResponse = this.chatClient.prompt()
                .user(userInput)
                .call()
                .chatResponse();

        Map<String, Object> result = new HashMap<>();
        result.put("input", userInput);
        result.put("reply", chatResponse.getResult().getOutput().getText());
        result.put("model", chatResponse.getMetadata().getModel());

        Map<String, Object> usage = new HashMap<>();
        if (chatResponse.getMetadata().getUsage() != null) {
            usage.put("promptTokens", chatResponse.getMetadata().getUsage().getPromptTokens());
            usage.put("completionTokens", chatResponse.getMetadata().getUsage().getCompletionTokens());
            usage.put("totalTokens", chatResponse.getMetadata().getUsage().getTotalTokens());
        }
        result.put("usage", usage);
        result.put("finishReason", chatResponse.getResult().getMetadata() != null
                ? chatResponse.getResult().getMetadata().getFinishReason() : "N/A");

        return result;
    }

    // ======================== 5. 工具调用 ========================

    /**
     * ChatClient 工具调用接口
     * <p>
     * 通过 {@code .tools(toolCallback)} 为当前请求注册外部工具，
     * AI 模型会根据对话内容自动判断是否需要调用工具。
     * <p>
     * 工具调用流程：
     * <ol>
     *   <li>用户提问 → 模型判断需要工具 → 返回工具调用请求</li>
     *   <li>框架执行工具 → 将执行结果返回给模型</li>
     *   <li>模型结合工具结果 → 生成最终回复</li>
     * </ol>
     * <p>
     * 注意：ChatClient 的工具调用是单轮的（与 ReactAgent 的多轮 ReAct 循环不同），
     * 模型会直接返回最终结果。如需多轮工具调用推理，请使用 ReactAgent。
     *
     * @param userInput 用户输入，默认 "北京今天天气怎么样"
     * @return 包含工具调用结果的回复
     */
    @GetMapping(value = "/tool", produces = "application/json;charset=UTF-8")
    public Map<String, Object> tool(
            @RequestParam(defaultValue = "北京今天天气怎么样") String userInput) {
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", new WeatherTool())
                .description("查询指定城市的天气信息")
                .inputType(WeatherToolInput.class)
                .build();

        String reply = this.chatClient.prompt()
                .user(userInput)
                .toolCallbacks(weatherTool)
                .call()
                .content();

        Map<String, Object> result = new HashMap<>();
        result.put("input", userInput);
        result.put("reply", reply);
        result.put("tool", "get_weather (天气查询)");
        return result;
    }

    // ======================== 6. 多轮对话 ========================

    /**
     * ChatClient 多轮对话接口
     * <p>
     * 通过 {@code .messages(messages)} 将历史消息列表传入，实现带上下文的连续对话。
     * 每次请求时可传入之前的 UserMessage 和 AssistantMessage，
     * 模型会基于完整上下文理解当前问题。
     * <p>
     * 本接口模拟一个两轮对话场景：
     * <ol>
     *   <li>第一轮：用户问"什么是 Java 中的 Stream API"</li>
     *   <li>第二轮：用户追问"那它和 for 循环有什么区别"</li>
     * </ol>
     * 通过传入第一轮的对话历史，模型能理解第二轮中"它"指代的是 Stream API。
     * <p>
     * 实际项目中，通常配合数据库/缓存来持久化对话历史。
     *
     * @param userInput 第二轮用户输入，默认 "那它和 for 循环有什么区别"
     * @return AI 回复（基于前一轮上下文）
     */
    @GetMapping(value = "/multi-turn", produces = "application/json;charset=UTF-8")
    public Map<String, Object> multiTurn(
            @RequestParam(defaultValue = "那它和 for 循环有什么区别") String userInput) {
        List<Message> conversationHistory = new ArrayList<>();
        conversationHistory.add(new UserMessage("什么是 Java 中的 Stream API"));
        conversationHistory.add(new UserMessage(userInput));

        String reply = this.chatClient.prompt()
                .messages(conversationHistory)
                .call()
                .content();

        Map<String, Object> result = new HashMap<>();
        result.put("firstQuestion", "什么是 Java 中的 Stream API");
        result.put("currentInput", userInput);
        result.put("reply", reply);
        result.put("messageCount", conversationHistory.size());
        return result;
    }

    // ======================== 7. 接口说明 ========================

    /**
     * ChatClient 演示接口汇总
     * <p>
     * 列出本控制器所有可用的 ChatClient 演示接口及其说明。
     *
     * @return 接口列表和说明的 JSON
     */
    @GetMapping(value = "/info", produces = "application/json;charset=UTF-8")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("description", "ChatClient 完整功能演示");

        List<Map<String, String>> endpoints = new ArrayList<>();
        endpoints.add(createEndpointEntry("/ai/chat-client/basic", "基础对话 — prompt().user().call().content()"));
        endpoints.add(createEndpointEntry("/ai/chat-client/stream", "流式输出 — prompt().user().stream().content()"));
        endpoints.add(createEndpointEntry("/ai/chat-client/system-prompt", "系统提示词 — prompt().system().user()"));
        endpoints.add(createEndpointEntry("/ai/chat-client/full-response", "完整响应 — call().chatResponse() 含 Token 用量"));
        endpoints.add(createEndpointEntry("/ai/chat-client/tool", "工具调用 — prompt().user().tools()"));
        endpoints.add(createEndpointEntry("/ai/chat-client/multi-turn", "多轮对话 — prompt().messages()"));
        endpoints.add(createEndpointEntry("/ai/chat-client/info", "本页面 — 接口说明"));
        result.put("endpoints", endpoints);

        return result;
    }

    private Map<String, String> createEndpointEntry(String path, String description) {
        Map<String, String> entry = new HashMap<>();
        entry.put("path", path);
        entry.put("description", description);
        return entry;
    }
}