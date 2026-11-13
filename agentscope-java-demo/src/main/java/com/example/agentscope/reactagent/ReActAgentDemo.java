package com.example.agentscope.reactagent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.List;

/**
 * ReActAgent 快速上手示例：演示推理-行动循环与工具调用。
 *
 * <p>ReActAgent 是 AgentScope 的核心智能体，采用 ReAct（Reasoning + Acting）范式：
 * 模型先推理下一步该做什么，然后执行工具，再把工具结果反馈给模型继续推理，
 * 直到得出最终回复。{@link Toolkit} 负责管理和注册工具。
 *
 * <p>本示例注册了三个自定义工具（获取时间、计算器、字数统计），
 * 并依次演示：普通调用、流式输出、多轮对话。
 *
 * <p>大模型配置见 {@code src/main/resources/application.properties}；
 * API 密钥请通过环境变量 {@code DEEPSEEK_API_KEY} 注入（优先级高于配置文件）。
 *
 * <p>运行：
 * <pre>
 *   set DEEPSEEK_API_KEY=你的DeepSeek密钥
 *   mvn compile exec:java -Dexec.mainClass=com.example.agentscope.reactagent.ReActAgentDemo
 * </pre>
 */
public class ReActAgentDemo {

    private record AppConfig(String apiKey, String baseUrl, String modelName, boolean stream) {
    }

    public static void main(String[] args) {
        AppConfig cfg = loadConfig();
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            System.err.println("缺少 API 密钥：请设置环境变量 DEEPSEEK_API_KEY"
                    + "，或在 application.properties 中配置 llm.api-key");
            System.exit(1);
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(cfg.apiKey())
                .baseUrl(cfg.baseUrl())
                .modelName(cfg.modelName())
                .stream(cfg.stream())
                .build();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name("react-assistant")
                .sysPrompt("你是一个有帮助的助手，可以使用工具来辅助回答。"
                        + "当用户的问题需要计算、查询时间或统计文本时，请主动调用相应工具。"
                        + "回答要简洁明了。")
                .model(model)
                .toolkit(toolkit);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("react-demo-session")
                .userId("demo-user")
                .build();

        System.out.println("========== ReActAgent Demo ==========");
        System.out.printf("[模型] %s @ %s（流式=%s）%n", cfg.modelName(), cfg.baseUrl(), cfg.stream());
        System.out.printf("[工具] %s%n", toolkit.getToolNames());
        System.out.printf("[会话] agent=react-assistant, user=demo-user, session=react-demo-session%n%n");

        runCallScenario(agentBuilder, ctx);
        runStreamingScenario(agentBuilder, ctx);
        runMultiTurnScenario(agentBuilder, ctx);

        System.out.println("\n========== 全部演示完成 ==========");
    }

    /**
     * 场景一：普通调用 —— Agent 推理并调用工具后返回完整回复。
     * <p>call() 返回 Mono&lt;Msg&gt;，block() 阻塞等待完整结果。
     */
    private static void runCallScenario(ReActAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("---------- 场景一：普通调用（call） ----------");
        try (ReActAgent agent = builder.build()) {
            Msg reply = agent.call(
                    List.of(new UserMessage("现在几点了？上海时间。")),
                    ctx
            ).block();
            printReply(reply);
        }
        System.out.println("[理解要点] call 返回 Mono<Msg>，block 等到完整回复；"
                + "工具调用在内部自动完成，最终只返回文本结果。\n");
    }

    /**
     * 场景二：流式输出 —— 逐字输出文本，工具开始时打印工具名。
     * <p>streamEvents() 返回 Flux&lt;AgentEvent&gt;，可实时观察推理和工具执行过程。
     */
    private static void runStreamingScenario(ReActAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("---------- 场景二：流式输出（streamEvents） ----------");
        try (ReActAgent agent = builder.build()) {
            agent.streamEvents(
                    new UserMessage("帮我算一下 3.14 * 100 等于多少？"),
                    ctx
            ).doOnNext(event -> {
                if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                    System.out.print(((TextBlockDeltaEvent) event).getDelta());
                    System.out.flush();
                } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                    System.out.println("\n[工具调用] " + ((ToolCallStartEvent) event).getToolCallName());
                }
            }).blockLast();
        }
        System.out.println("\n[理解要点] streamEvents 返回 Flux<AgentEvent>，"
                + "TEXT_BLOCK_DELTA 是文本增量，TOOL_CALL_START 标记工具开始执行。\n");
    }

    /**
     * 场景三：多轮对话 —— 同一会话中连续提问，Agent 能记住上下文。
     * <p>每次创建新实例但使用相同 RuntimeContext（userId + sessionId），
     * 框架会自动恢复会话状态，实现跨实例的连续对话。
     */
    private static void runMultiTurnScenario(ReActAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("---------- 场景三：多轮对话 ----------");
        try (ReActAgent agent = builder.build()) {
            Msg reply1 = agent.call(
                    List.of(new UserMessage("请统计一下 \"Hello AgentScope Java ReActAgent\" 这句话的字符数和词数。")),
                    ctx
            ).block();
            System.out.println("[第1轮回复] " + reply1.getTextContent());

            Msg reply2 = agent.call(
                    List.of(new UserMessage("刚才统计的是哪句话？结果是什么？")),
                    ctx
            ).block();
            System.out.println("[第2轮回复] " + reply2.getTextContent());
        }
        System.out.println("[理解要点] 同一实例内连续 call，Agent 自动维护对话历史，"
                + "第2轮能回忆起第1轮的内容。\n");
    }

    private static void printReply(Msg reply) {
        if (reply == null) {
            throw new IllegalStateException("Agent 未返回回复，请检查模型调用日志");
        }
        System.out.println("[助手回复] " + reply.getTextContent());
    }

    private static AppConfig loadConfig() {
        Properties props = new Properties();
        try (InputStream in = ReActAgentDemo.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }

        String apiKey = resolve(System.getenv("DEEPSEEK_API_KEY"),
                props.getProperty("llm.api-key"), null);
        String baseUrl = resolve(System.getenv("DEEPSEEK_BASE_URL"),
                props.getProperty("llm.base-url"), "https://api.deepseek.com");
        String modelName = resolve(System.getenv("DEEPSEEK_MODEL"),
                props.getProperty("llm.model"), "deepseek-chat");
        boolean stream = Boolean.parseBoolean(props.getProperty("llm.stream", "true"));

        return new AppConfig(apiKey, baseUrl, modelName, stream);
    }

    private static String resolve(String envValue, String fileValue, String defaultValue) {
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }
        return defaultValue;
    }
}
