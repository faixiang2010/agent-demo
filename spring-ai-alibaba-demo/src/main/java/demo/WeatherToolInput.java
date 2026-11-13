package demo;

/**
 * 天气查询工具的输入参数类
 * <p>
 * 作为 FunctionToolCallback 的 inputType，Spring AI 会自动将模型生成的
 * JSON 参数（如 {"city": "北京"}）反序列化为本类实例。
 */
public class WeatherToolInput {

    /**
     * 要查询天气的城市名称
     */
    private String city;

    /**
     * 获取城市名称
     *
     * @return 城市名称
     */
    public String getCity() {
        return city;
    }

    /**
     * 设置城市名称
     *
     * @param city 城市名称
     */
    public void setCity(String city) {
        this.city = city;
    }
}
