package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Alibaba 演示应用主入口
 * <p>
 * 演示以下核心能力：
 * <ul>
 *   <li>基于 ChatModel 的单轮对话（/ai/chat）</li>
 *   <li>基于 ReactAgent 的智能体对话，支持工具调用和 ReAct 循环（/ai/agent）</li>
 *   <li>双模型提供者切换（DashScope 通义千问 / DeepSeek）</li>
 *   <li>ReAct 执行步骤的 Hook 日志追踪</li>
 * </ul>
 * <p>
 * 相关类说明：
 * <ul>
 *   <li>{@link ChatController} — REST 接口层，处理 chat/agent/models 请求</li>
 *   <li>{@link IndexController} — 首页导航</li>
 *   <li>{@link ReactStepLogger} — ReAct 步骤日志记录（Hook 实现）</li>
 *   <li>{@link WeatherTool} — 天气查询工具</li>
 *   <li>{@link WeatherToolInput} — 天气工具输入参数</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"demo", "chatclient"})
public class SpringAiAlibabaDemoApplication {

    /**
     * 应用主入口，启动 Spring Boot 容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringAiAlibabaDemoApplication.class, args);
    }
}