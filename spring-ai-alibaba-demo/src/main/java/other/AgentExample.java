package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class AgentExample {

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
                .name("weather_agent")
                .model(chatModel)
                .systemPrompt("你是一个有用的天气预报助手。")
                .build();

        AssistantMessage response = agent.call("杭州的天气怎么样？");
        System.out.println("智能体回复: " + response.getText());
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