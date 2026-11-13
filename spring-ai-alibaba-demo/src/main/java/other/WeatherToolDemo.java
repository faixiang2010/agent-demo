package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.toolcalling.common.JsonParseTool;
import com.alibaba.cloud.ai.toolcalling.common.WebClientTool;
import com.alibaba.cloud.ai.toolcalling.weather.WeatherProperties;
import com.alibaba.cloud.ai.toolcalling.weather.WeatherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * Weather 工具调用 Demo（B 方案：真实调用 weatherapi.com）。
 *
 * <p>对应文档：<a href="https://java2ai.com/integration/toolcalls/tool-calls">Tool Calling 使用指南</a>。
 * 纯 API 调用，不依赖 Spring 容器；运行方式：IDE 中右键运行 main 方法。</p>
 *
 * <p><b>前置要求</b>：
 * <ol>
 *   <li>到 <a href="https://www.weatherapi.com/">weatherapi.com</a> 注册免费账号，获取 API Key
 *       （Free Plan：100 万次/月、1 万次/天，无需绑卡）。</li>
 *   <li>将下方 {@link #WEATHER_API_KEY} 常量替换为你的真实 Key 后再运行。</li>
 * </ol></p>
 *
 * <p><b>实现说明</b>：直接复用 starter 1.1.2.1 中的 {@link WeatherService}
 * （内部用 WebClient 调 {@code https://api.weatherapi.com/v1/forecast.json}，
 * 鉴权用 HTTP header {@code key}，查询参数 {@code q=城市&days=天数}）。
 * 手动 new 出来绕过 Spring 容器；通过
 * {@link FunctionToolCallback#builder(String, Function)} 包装为 {@link ToolCallback}，
 * 注册到 {@link ChatClient}，由模型自主决定何时调用。</p>
 *
 * <p><b>与 ToolCallDemo 对照</b>：ToolCallDemo 中的 GetWeatherTool 是模拟实现（返回硬编码字符串），
 * 这里是真实 HTTP 调用。Tool Name 用 {@code "getWeatherService"}
 * （与 starter 中 {@code WeatherConstants.TOOL_NAME} 一致）。</p>
 */
public class WeatherToolDemo {

    /** DashScope API Key（与 Demo1 / ToolCallDemo 一致，硬编码便于独立运行）。 */
    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";

    /**
     * weatherapi.com 的 API Key。
     * 申请方式：访问 https://www.weatherapi.com/ → Sign up（免费，无需绑卡）→
     *          登录后控制台首页复制 API Key。
     * 替换下方占位值为你的真实 Key 后再运行，否则会返回 401 Unauthorized。
     */
    private static final String WEATHER_API_KEY = "6ac139636dba4d69a9732109260810";

    public static void main(String[] args) {
        // 1. 构建 DashScopeChatModel（与 Demo1 / ToolCallDemo 一致）
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

        // 2. 手动构造 WeatherService（绕过 Spring 容器，复刻 WeatherAutoConfiguration 的装配逻辑）
        //    (a) JsonParseTool：包级构造不可用，用 public 构造传 ObjectMapper
        JsonParseTool jsonParseTool = new JsonParseTool(new ObjectMapper());

        //    (b) WeatherProperties：设置 apiKey 与 baseUrl
        //        path 在 WeatherService.apply 内部硬编码为 "v1/forecast.json"（相对 baseUrl）
        WeatherProperties props = new WeatherProperties();
        props.setApiKey(WEATHER_API_KEY);
        props.setBaseUrl("https://api.weatherapi.com");

        //    (c) WebClientTool：apiKey 放在 header "key"
        //        （与 WeatherAutoConfiguration.lambda$getWeatherService$0 字节码一致）
        WebClientTool webClientTool = WebClientTool.builder(jsonParseTool, props)
                .httpHeadersConsumer(headers -> headers.add("key", props.getApiKey()))
                .build();

        //    (d) WeatherService：实现 Function<Request, Response>，apply 时调 weatherapi.com
        WeatherService weatherService = new WeatherService(webClientTool, jsonParseTool);

        // 3. 包装为 ToolCallback（Spring AI 1.1.2 支持单参 Function 重载；
        //    Tool Name = "getWeatherService"，与 WeatherConstants.TOOL_NAME 一致；
        //    Response 是 record，由 DEFAULT_RESULT_CONVERTER 序列化为 JSON 字符串）
        ToolCallback weatherTool = FunctionToolCallback
                .builder("getWeatherService",
                        (Function<WeatherService.Request, WeatherService.Response>) weatherService)
                .description("查询指定城市的天气。当用户询问某地天气、温度、是否下雨、未来几天预报等问题时调用。"
                        + "入参 city（城市名，中文或英文），days（预报天数，1-10）。")
                .inputType(WeatherService.Request.class)
                .build();

        // 4. 构建 ChatClient 并注册工具
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(weatherTool)
                .build();

        // 5. 提问，模型会自主决定调用 getWeatherService 工具
        String question = "杭州今天和未来 3 天的天气怎么样？需要带伞吗？";
        System.out.println("用户提问：" + question);
        System.out.println("────────────────────────────────────");

        String response = chatClient.prompt(question)
                .call()
                .content();

        System.out.println("模型回复：");
        System.out.println(response);
    }
}
