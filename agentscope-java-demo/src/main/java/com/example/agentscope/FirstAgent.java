package com.example.agentscope;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AgentScope Java 2.0 Quickstart 示例。
 *
 * <p>在同一会话中依次展示：普通问答与跨实例恢复、流式事件、Web 请求生命周期。
 * 使用 DeepSeek（OpenAI 兼容协议），可选接入 Studio；所有本地数据在项目的 .agentscope 下。
 *
 * <p>大模型相关配置见 {@code src/main/resources/application.properties}；
 * API 密钥请通过环境变量 {@code DEEPSEEK_API_KEY} 注入（优先级高于配置文件）。
 *
 * <p>运行：
 * <pre>
 *   set DEEPSEEK_API_KEY=你的DeepSeek密钥
 *   mvn compile exec:java
 * </pre>
 */
public class FirstAgent {

    /** 大模型 + Studio 配置，从 classpath:application.properties 加载。 */
    private record AppConfig(String apiKey, String baseUrl, String modelName,
                             boolean stream, int triggerMessages, int keepMessages,
                             boolean studioEnabled, String studioUrl,
                             String studioProject, String studioRunName) {
    }

    public static void main(String[] args) {
        String sessionId = args.length == 0 ? "demo-session" : args[0].trim();
        if (sessionId.isBlank() || args.length > 1) {
            throw new IllegalArgumentException("用法：FirstAgent [sessionId]，会话 ID 不能为空");
        }
        AppConfig cfg = loadConfig();
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            System.err.println("缺少 API 密钥：请设置环境变量 DEEPSEEK_API_KEY"
                    + "，或在 application.properties 中配置 llm.api-key");
            System.exit(1);
        }

        // 可选：初始化 Studio 连接（HTTP + WebSocket），失败时降级为不接入而非中断运行
        boolean studioOn = false;
        if (cfg.studioEnabled()) {
            studioOn = initStudio(cfg);
        }

        try {
            Model model = OpenAIChatModel.builder()
                    .apiKey(cfg.apiKey())
                    .baseUrl(cfg.baseUrl())
                    .modelName(cfg.modelName())
                    .stream(cfg.stream())
                    .build();

            DemoStorage storage = DemoStorage.firstAgent();
            Path workspaceDir = storage.workspace();
            Path stateDir = storage.state("note-taker");
            System.out.println("[AgentScope] 工作区: " + workspaceDir);
            System.out.println("[AgentScope] 会话状态: " + stateDir);

            HarnessAgent.Builder agentBuilder = HarnessAgent.builder()
                    .name("note-taker")
                    .agentId("note-taker")
                    .sysPrompt("你是一个帮助用户做笔记的助手。")
                    .model(model)
                    .workspace(workspaceDir)
                    .stateStore(new JsonFileAgentStateStore(stateDir))
                    .compaction(CompactionConfig.builder()
                            .triggerMessages(cfg.triggerMessages())
                            .keepMessages(cfg.keepMessages())
                            .build());

            // Studio 接入成功后挂上消息 Hook，调用过程自动推送到 Studio
            if (studioOn) {
                agentBuilder.hook(new StudioMessageHook(StudioManager.getClient()));
                System.out.printf("[Studio] 已接入 %s（project=%s, run=%s）%n",
                        cfg.studioUrl(), cfg.studioProject(), cfg.studioRunName());
            }
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId("alice")
                    .build();
            System.out.printf("[会话] agent=note-taker, user=alice, session=%s%n", sessionId);
            System.out.println("三段场景按顺序执行，共四次用户请求；每次创建并关闭一个 Agent 实例。");
            System.out.println("框架还可能额外调用模型提取长期记忆。\n");

            // Builder 配置到此结束；各场景只 build()，不再修改共享配置。
            runChatScenario(agentBuilder, ctx);
            runStreamingScenario(agentBuilder, ctx);
            runWebScenario(agentBuilder, ctx);
        } finally {
            // 关闭 Studio 的 HTTP/WebSocket 资源，避免后台线程阻止 JVM 退出
            if (studioOn) {
                StudioManager.shutdown();
            }
        }
    }

    /** 场景一：实例换了，但会话身份和存储位置相同，仍能接着聊。 */
    private static void runChatScenario(HarnessAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("========== 场景一：普通问答 + 跨实例多轮对话 ==========");
        System.out.println("[请求 1 / 实例 A] 告诉助手姓名和今天的任务。");
        try (HarnessAgent agent = builder.build()) {
            Msg reply = agent.call(new UserMessage(
                    "我叫天宇，今天准备一个关于 ReAct 的技术分享。请简短回复。"), ctx).block();
            printReply(reply);
        }
        System.out.println("[实例 A 已关闭] 下面创建实例 B，使用相同 userId + sessionId。");
        try (HarnessAgent agent = builder.build()) {
            Msg reply = agent.call(new UserMessage("我叫什么？我今天要干什么？请简短回复。"), ctx).block();
            printReply(reply);
        }
        System.out.println("[理解要点] call 返回 Mono<Msg>，block 等到完整回复；记忆不依赖实例 A 还活着。\n");
    }

    /** 场景二：每个文本增量立即输出，工具开始执行时打印工具名。 */
    private static void runStreamingScenario(HarnessAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("========== 场景二：流式查看文本与工具事件 ==========");
        System.out.println("[请求 3 / 实例 C] 根据之前的分享主题，逐步输出三条准备工作。");
        try (HarnessAgent agent = builder.build()) {
            agent.streamEvents(new UserMessage("帮我把刚才技术分享的准备工作列成三条，简短一些。"), ctx)
                    .doOnNext(event -> {
                        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                            System.out.print(((TextBlockDeltaEvent) event).getDelta());
                            System.out.flush();
                        } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                            System.out.println("\n[工具开始] " + ((ToolCallStartEvent) event).getToolCallName());
                        }
                    })
                    .blockLast();
        }
        System.out.println("\n[理解要点] streamEvents 返回 Flux<AgentEvent>，blockLast 等到事件流结束。");
        System.out.println("工具事件只在模型实际调用工具时出现；这里没有先 call 再重复执行同一输入。\n");
    }

    /** 场景三：模拟 WebFlux handler；订阅前不创建 Agent，结束或取消时释放。 */
    private static void runWebScenario(HarnessAgent.Builder builder, RuntimeContext ctx) {
        System.out.println("========== 场景三：Web 请求生命周期（模拟 handler） ==========");
        System.out.println("[请求 4 / 实例 D] 把前面的姓名、主题和准备工作总结成一句话。");
        Mono<Msg> response = handleWebRequest(builder, ctx,
                "请把我的姓名、今天分享的主题和刚才的准备工作总结成一句话。");
        System.out.println("[尚未订阅] handler 已返回 Mono，此时没有创建实例 D，也没有发起模型请求。");
        // 命令行用 block 模拟 Web 框架的订阅；真正的 WebFlux handler 直接返回 Mono。
        printReply(response.block());
        System.out.println("[理解要点] Mono.using 把创建和关闭绑定到订阅；完成、异常、取消都会释放实例。");
        System.out.println("本场景没有启动 HTTP 服务；WebFlux 中返回这个 Mono 即可，不在 handler 内 block。\n");
    }

    private static Mono<Msg> handleWebRequest(HarnessAgent.Builder builder,
                                              RuntimeContext ctx, String userInput) {
        return Mono.using(
                () -> {
                    System.out.println("[开始订阅] 创建实例 D。");
                    return builder.build();
                },
                agent -> agent.call(new UserMessage(userInput), ctx),
                agent -> {
                    agent.close();
                    System.out.println("[释放资源] 实例 D 已关闭。");
                });
    }

    private static void printReply(Msg reply) {
        if (reply == null) {
            throw new IllegalStateException("Agent 未返回回复，请检查模型调用日志");
        }
        System.out.println("[助手回复] " + reply.getTextContent());
    }

    /**
     * 初始化 Studio。连接失败（如本地未启动 Studio 服务）时打印告警并返回 false，
     * 程序以不接入 Studio 的方式继续运行。
     */
    private static boolean initStudio(AppConfig cfg) {
        try {
            StudioManager.init()
                    .studioUrl(cfg.studioUrl())
                    .project(cfg.studioProject())
                    .runName(cfg.studioRunName())
                    .initialize()
                    .block();
            return true;
        } catch (Exception e) {
            System.err.printf("[Studio] 连接 %s 失败，已跳过 Studio 接入：%s%n",
                    cfg.studioUrl(), e.getMessage());
            return false;
        }
    }

    /**
     * 加载配置：环境变量优先于 application.properties，缺失项使用默认值兜底。
     */
    private static AppConfig loadConfig() {
        Properties props = new Properties();
        try (InputStream in = FirstAgent.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }

        // 密钥：环境变量优先，避免明文进入代码仓库
        String apiKey = resolve(System.getenv("DEEPSEEK_API_KEY"),
                props.getProperty("llm.api-key"), null);
        String baseUrl = resolve(System.getenv("DEEPSEEK_BASE_URL"),
                props.getProperty("llm.base-url"), "https://api.deepseek.com");
        String modelName = resolve(System.getenv("DEEPSEEK_MODEL"),
                props.getProperty("llm.model"), "deepseek-chat");
        boolean stream = Boolean.parseBoolean(props.getProperty("llm.stream", "true"));
        int triggerMessages = Integer.parseInt(
                props.getProperty("llm.compaction.trigger-messages", "30"));
        int keepMessages = Integer.parseInt(
                props.getProperty("llm.compaction.keep-messages", "10"));

        boolean studioEnabled = Boolean.parseBoolean(
                props.getProperty("studio.enabled", "false"));
        String studioUrl = props.getProperty("studio.url", "http://localhost:8000");
        String studioProject = props.getProperty("studio.project", "agentscope-java-demo");
        String studioRunName = props.getProperty("studio.run-name", "first-agent-run");

        return new AppConfig(apiKey, baseUrl, modelName, stream,
                triggerMessages, keepMessages,
                studioEnabled, studioUrl, studioProject, studioRunName);
    }

    /** 返回第一个非空白值，全部为空时返回默认值。 */
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
