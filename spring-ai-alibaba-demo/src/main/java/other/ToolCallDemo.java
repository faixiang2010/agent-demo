package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;

/**
 * Tool Calling（工具调用）Demo。
 *
 * <p>对应文档：<a href="https://java2ai.com/integration/toolcalls/tool-calls">Tool Calling 使用指南</a>。
 * 纯 API 调用，不依赖 Spring 容器；运行方式：IDE 中右键运行 main 方法。</p>
 *
 * <p><b>与 Demo1 的区别</b>：Demo1 用 {@code ReactAgent}（基于 graph 的 ReAct 推理框架），
 * 这里用 Spring AI 标准的 {@link ChatClient} + {@code defaultToolCallbacks(...)}，
 * 是 Spring AI 推荐的工具调用入口。模型会自主决定是否/何时/调用哪个工具。</p>
 *
 * <p><b>工具实现模式</b>：按文档"通用接口"约定，所有工具实现
 * {@code BiFunction<Request, ToolContext, String>}，再用
 * {@link FunctionToolCallback#builder} 包装为 {@link ToolCallback}。</p>
 */
public class ToolCallDemo {

    /** DashScope API Key（与 Demo1 一致，硬编码便于独立运行）。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    public static void main(String[] args) {
        // 1. 构建 DashScopeChatModel（与 Demo1 一致）
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .build();
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .temperature(0.7)
                        .build())
                .build();

        // 2. 包装两个自定义工具为 ToolCallback
        ToolCallback currentTimeTool = FunctionToolCallback
                .builder("get_current_time", new GetCurrentTimeTool())
                .description("获取当前系统时间，返回 ISO 格式字符串。当用户询问现在几点、今天日期等问题时调用。")
                .inputType(GetCurrentTimeTool.Request.class)
                .build();
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", new GetWeatherTool())
                .description("查询指定城市的天气。当用户询问某地天气、是否下雨、温度如何等问题时调用。")
                .inputType(GetWeatherTool.Request.class)
                .build();

        // 3. 构建 ChatClient 并注册工具（与 Demo1 的 ReactAgent.builder().tools(...) 对照）
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(currentTimeTool, weatherTool)
                .build();

        // 4. 提问，模型会自主决定调用哪个工具（可能同时调用两个）
        String question = "现在几点？杭州今天天气怎么样？";
        System.out.println("用户提问：" + question);
        System.out.println("────────────────────────────────────");

        String response = chatClient.prompt(question)
                .call()
                .content();

        System.out.println("模型回复：");
        System.out.println(response);
    }

    // ======================== 工具 1：获取当前时间 ========================

    /**
     * 获取当前系统时间的工具。模拟实现，不依赖外部 API。
     */
    public static class GetCurrentTimeTool
            implements BiFunction<GetCurrentTimeTool.Request, org.springframework.ai.chat.model.ToolContext, String> {

        /** 工具入参：留空字段以便 SDK 反序列化（即使无参也需有 Request 类）。 */
        public static class Request {
            // 无字段，仅占位
        }

        @Override
        public String apply(Request request, org.springframework.ai.chat.model.ToolContext context) {
            return "当前时间：" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    // ======================== 工具 2：查询天气 ========================

    /**
     * 查询城市天气的工具。模拟实现，不调用真实天气 API。
     */
    public static class GetWeatherTool
            implements BiFunction<GetWeatherTool.Request, org.springframework.ai.chat.model.ToolContext, String> {

        /** 工具入参：城市名称。 */
        public static class Request {
            private String city;

            public String getCity() {
                return city;
            }

            public void setCity(String city) {
                this.city = city;
            }
        }

        @Override
        public String apply(Request request, org.springframework.ai.chat.model.ToolContext context) {
            String city = request.getCity() == null ? "未知城市" : request.getCity();
            // 模拟天气查询结果
            return String.format("%s 当前天气：晴朗，气温 22℃，湿度 55%%，东风 3 级。", city);
        }
    }
}
