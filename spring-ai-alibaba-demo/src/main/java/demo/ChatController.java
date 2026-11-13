package demo;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 对话 REST 控制器
 * <p>
 * 提供两个核心接口：
 * <ul>
 *   <li>/ai/chat — 基于 ChatModel 的直接对话（单轮）</li>
 *   <li>/ai/agent — 基于 ReactAgent 的智能体对话（支持工具调用、多轮 ReAct 循环）</li>
 * </ul>
 * 均支持通过 provider 参数在 DashScope 和 DeepSeek 之间切换。
 */
@RestController
@RequestMapping("/ai")
public class ChatController {

    /**
     * DashScope（通义千问）ChatModel 实例
     */
    private final ChatModel dashScopeChatModel;

    /**
     * OpenAI 兼容接口的 ChatModel 实例（用于 DeepSeek）
     */
    private final ChatModel openAiChatModel;

    /**
     * 构造函数注入两个 ChatModel
     *
     * @param dashScopeChatModel DashScope 模型（bean 名称 "dashScopeChatModel"）
     * @param openAiChatModel    OpenAI 兼容模型（bean 名称 "openAiChatModel"）
     */
    public ChatController(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
        this.openAiChatModel = openAiChatModel;
    }

    /**
     * 根据 provider 参数解析对应的 ChatModel
     *
     * @param provider 模型提供者标识，deepseek 返回 OpenAI 兼容模型，其他返回 DashScope
     * @return 对应的 ChatModel 实例
     */
    private ChatModel resolveModel(String provider) {
        if ("deepseek".equalsIgnoreCase(provider)) {
            return openAiChatModel;
        }
        return dashScopeChatModel;
    }

    /**
     * 将 provider 参数解析为可读的模型名称（用于接口返回展示）
     *
     * @param provider 模型提供者标识
     * @return 可读模型名称
     */
    private String resolveModelName(String provider) {
        if ("deepseek".equalsIgnoreCase(provider)) {
            return "DeepSeek (deepseek-chat)";
        }
        return "DashScope (qwen-plus)";
    }

    /**
     * 基础对话接口
     * <p>
     * 直接调用 ChatModel 进行单轮对话，不涉及工具调用或 ReAct 循环。
     *
     * @param message  用户消息，默认"你好，请介绍一下你自己"
     * @param provider 模型提供者，支持 dashscope 或 deepseek，默认 dashscope
     * @return 包含 message、reply、provider 的 JSON 结果
     */
    @GetMapping(value = "/chat", produces = "application/json;charset=UTF-8")
    public Map<String, String> chat(
            @RequestParam(defaultValue = "你好，请介绍一下你自己") String message,
            @RequestParam(defaultValue = "dashscope") String provider) {
        ChatModel model = resolveModel(provider);
        String reply = model.call(message);
        Map<String, String> result = new HashMap<>();
        result.put("message", message);
        result.put("reply", reply);
        result.put("provider", resolveModelName(provider));
        return result;
    }

    /**
     * ReactAgent 智能体对话接口
     * <p>
     * 基于 ReAct（Reasoning + Acting）模式构建智能体，支持工具调用和记忆管理。
     * 智能体会自动进行"思考→调用工具→观察结果→再思考"的循环，直到得出最终答案。
     * 每次执行的详细步骤日志会输出到控制台，体现 ReAct 的推理过程。
     * <p>
     * 工具说明：
     * <ul>
     *   <li>get_weather — 查询指定城市天气（模拟数据）</li>
     * </ul>
     *
     * @param message  用户消息，默认"北京今天天气怎么样"
     * @param provider 模型提供者，支持 dashscope（通义千问）或 deepseek，默认 dashscope
     * @return 包含 message、reply、agent、provider、steps、modelCalls 的结果 Map；异常时返回 error 字段
     */
    @GetMapping(value = "/agent", produces = "application/json;charset=UTF-8")
    public Map<String, Object> agentChat(
            @RequestParam(defaultValue = "北京今天天气怎么样") String message,
            @RequestParam(defaultValue = "dashscope") String provider) {
        ChatModel model = resolveModel(provider);
        String providerName = resolveModelName(provider);

        // 构建天气查询工具回调
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", new WeatherTool())
                .description("查询指定城市的天气信息")
                .inputType(WeatherToolInput.class)
                .build();

        // 创建 ReAct 步骤日志记录器
        ReactStepLogger stepLogger = new ReactStepLogger(providerName);

        // 构建 ReactAgent，注册工具、Hook 和状态保存器
        ReactAgent agent = ReactAgent.builder()
                .name("weather_agent")
                .model(model)
                .tools(weatherTool)
                .systemPrompt("你是一个有用的助手，可以查询天气信息")
                .saver(new MemorySaver())
                .hooks(stepLogger.createModelHook(), stepLogger.createAgentHook())
                .build();

        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("agent", "weather_agent");
        result.put("provider", providerName);
        try {
            AssistantMessage response = agent.call(message);
            result.put("reply", response.getText());
            result.put("steps", stepLogger.getTotalSteps());
            result.put("modelCalls", stepLogger.getTotalModelCalls());
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("steps", stepLogger.getTotalSteps());
            result.put("modelCalls", stepLogger.getTotalModelCalls());
        }
        return result;
    }

    /**
     * 查看可用模型列表
     *
     * @return 包含 current_default、providers 列表和 usage 说明的 JSON
     */
    @GetMapping(value = "/models", produces = "application/json;charset=UTF-8")
    public Map<String, Object> models() {
        Map<String, Object> result = new HashMap<>();
        result.put("current_default", "dashscope");
        Map<String, String> providers = new HashMap<>();
        providers.put("dashscope", "DashScope (通义千问 qwen-plus)");
        providers.put("deepseek", "DeepSeek (deepseek-chat)");
        result.put("providers", providers);
        result.put("usage", "在 /ai/chat 和 /ai/agent 接口添加 provider 参数切换，例如: /ai/chat?provider=deepseek");
        return result;
    }
}
