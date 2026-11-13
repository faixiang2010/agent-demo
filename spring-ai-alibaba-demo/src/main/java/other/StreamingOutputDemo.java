package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

public class StreamingOutputDemo {

    private static final String DASHSCOPE_API_KEY = System.getenv("AI_DASHSCOPE_API_KEY");
    private static final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");
    private static final String DEEPSEEK_BASE_URL = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com");

    public static void main(String[] args) throws Exception {
        String provider = args.length > 0 ? args[0].toLowerCase() : "dashscope";
        ChatModel chatModel = switch (provider) {
            case "deepseek" -> createDeepSeekModel();
            case "dashscope" -> createDashScopeModel();
            default -> throw new IllegalArgumentException("不支持的模型提供商: " + provider + "，请使用 dashscope 或 deepseek");
        };

        System.out.println("当前使用模型提供商: " + provider);

        ReactAgent agent = ReactAgent.builder()
                .name("streaming_agent")
                .model(chatModel)
                .systemPrompt("你是一个有用的助手，请详细回答用户的问题。")
                .build();

        String userMessage = "请介绍一下杭州的历史和文化特色";
        System.out.println("用户提问: " + userMessage);
        System.out.println("--- 流式输出开始 ---");

        Flux<NodeOutput> stream = agent.stream(userMessage);
        stream.subscribe(
                output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();

                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            System.out.print(streamingOutput.message().getText());
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            System.out.println("\n[模型输出完成]");
                        }

                        if (type == OutputType.AGENT_TOOL_FINISHED) {
                            System.out.println("[工具调用完成] 节点: " + output.node());
                        }

                        if (type == OutputType.AGENT_HOOK_FINISHED) {
                            System.out.println("[Hook 执行完成] 节点: " + output.node());
                        }
                    }
                },
                error -> System.err.println("[错误] " + error.getMessage()),
                () -> System.out.println("--- Agent 执行完成 ---")
        );

        Thread.sleep(60_000);
    }

    private static ChatModel createDashScopeModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen-plus")
                .temperature(0.7)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    private static ChatModel createDeepSeekModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(DEEPSEEK_BASE_URL)
                .apiKey(DEEPSEEK_API_KEY)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("deepseek-chat")
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}