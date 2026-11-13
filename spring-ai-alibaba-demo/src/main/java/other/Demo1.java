package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.function.BiFunction;

public class Demo1 {
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    public static void main(String[] args) throws GraphRunnerException {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .build();

        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .temperature(0.7)           // 控制随机性
                        .maxToken(2000)  // 最大输出长度
                        .topP(0.9)                  // 核采样参数
                        .build())
                .build();

        // 创建工具回调
        ToolCallback searchTool = FunctionToolCallback.builder("search_weather", new SearchTool())
                .description("搜索工具")
                .inputType(SearchToolInput.class)
                .build();

        String instruction = """
        你是一个天气助手，只能查询天气，不能推荐活动。
        你只能使用search_weather工具查询天气。
        """;

        ReactAgent reactAgent = ReactAgent.builder()
                .name("react-agent")
                .model(chatModel)
                .tools(searchTool)
                .interceptors(new ToolErrorInterceptor())
                //.systemPrompt("你是一个天气助手，只能查询天气，不能推荐活动")
                .systemPrompt(instruction)
                .build();

        // 运行 agent
        // AssistantMessage response = reactAgent.call("你好，查询杭州天气并推荐活动");
        // System.out.println(response.getText());

        // UserMessage 输入
        // UserMessage userMessage = new UserMessage("你好，查询杭州天气并推荐活动");
        // AssistantMessage response = reactAgent.call(userMessage);
        // System.out.println(response.getText());

        // 多个消息
        List<Message> messages = List.of(
                new UserMessage("查询杭州天气"),
                new UserMessage("是不是晴朗的")
        );
        AssistantMessage response = reactAgent.call(messages);
        System.out.println(response.getText());
    }

    // 定义工具（示例：仅一个搜索工具）
    public static class SearchToolInput {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }

    // 定义工具（示例：仅一个搜索工具）
    public static class SearchTool implements BiFunction<SearchToolInput, ToolContext, String> {
        @Override
        public String apply(SearchToolInput input, ToolContext context) {
            // 实现搜索逻辑
            return "搜索结果: " + input.getQuery() + "的天气是晴朗的，今天多云22℃";
            // throw new RuntimeException("模拟工具调用失败");
        }
    }
}