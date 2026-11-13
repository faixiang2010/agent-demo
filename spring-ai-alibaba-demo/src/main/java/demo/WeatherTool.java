package demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

/**
 * 天气查询工具实现
 * <p>
 * 模拟查询指定城市的天气信息，返回固定的演示数据（晴，28°C，45%）。
 * 实际项目中可替换为真实的天气 API 调用（如高德天气、和风天气等）。
 * <p>
 * 实现 BiFunction&lt;WeatherToolInput, ToolContext, String&gt; 接口，
 * 以便通过 FunctionToolCallback 注册为 Spring AI 可调用工具。
 */
public class WeatherTool implements BiFunction<WeatherToolInput, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    /**
     * 执行天气查询
     *
     * @param input      包含城市名称的输入参数
     * @param toolContext Spring AI 工具上下文（当前未使用，保留扩展性）
     * @return 天气查询结果字符串
     */
    @Override
    public String apply(WeatherToolInput input, ToolContext toolContext) {
        String result = input.getCity() + "当前天气：晴，温度28°C，湿度45%";
        log.info("  [工具执行] get_weather(city={}) => {}", input.getCity(), result);
        return result;
    }
}
