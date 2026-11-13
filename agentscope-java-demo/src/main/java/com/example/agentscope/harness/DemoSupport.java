package com.example.agentscope.harness;

import com.example.agentscope.DemoStorage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** 四个新示例的配置和终端辅助代码；业务步骤仍在各自 main 中，没有独立 main。 */
public final class DemoSupport {
    private DemoSupport() { }

    /** 每轮使用独立目录，避免重复演示覆盖之前的教学结果。 */
    public static DemoStorage storage(String topic) {
        return new DemoStorage(Path.of(".agentscope", "harness-" + topic, "run-" + UUID.randomUUID())
                .toAbsolutePath().normalize());
    }

    public static RuntimeContext context() {
        return RuntimeContext.builder().sessionId("demo-" + UUID.randomUUID()).build();
    }

    /** 本机入门默认值。各示例只打开自己的主题能力。 */
    public static HarnessAgent.Builder localBuilder(String name, Model model, DemoStorage storage) throws IOException {
        Files.createDirectories(storage.workspace());
        return HarnessAgent.builder().name(name).agentId(name).model(model)
                .sysPrompt("你是教学助手，中文简短回答，称呼用户为老大。按用户要求真实调用工具，不虚报执行成功。")
                .workspace(storage.workspace())
                .filesystem(new LocalFilesystemSpec().project(storage.workspace()).sharedLocalWorkspace(true))
                .stateStore(new JsonFileAgentStateStore(storage.state(name)))
                .disableMemoryHooks().disableMemoryTools().disableCompaction()
                .disableShellTool().disableWebTools().disableToolsConfig().maxIters(10);
    }

    public static BufferedReader console() {
        return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    public static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** 收集真实事件，检查工具与子 Agent 是否确实被调用，不能只根据最终措辞判断。 */
    public static final class Trace {
        public final Set<String> tools = new LinkedHashSet<>();
        public final Set<String> childSources = new LinkedHashSet<>();
        public final StringBuilder text = new StringBuilder();
        private final Map<String, StringBuilder> childText = new LinkedHashMap<>();
        public RequireUserConfirmEvent pending;

        public synchronized void accept(AgentEvent event) {
            String source = event.getSource();
            if (source != null && !source.isBlank()) childSources.add(source);
            String label = source == null || source.isBlank() ? "主 Agent" : "子 Agent " + source;
            if (event instanceof AgentStartEvent) System.out.println("\n[开始] " + label);
            else if (event instanceof AgentEndEvent) {
                if (source != null && childText.containsKey(source)) {
                    System.out.println("\n[子 Agent 结果 / " + source + "]\n" + childText.remove(source));
                }
                System.out.println("\n[结束] " + label);
            } else if (event instanceof TextBlockDeltaEvent delta) {
                text.append(delta.getDelta());
                // 子任务可能并行；分别缓存，到结束时整段打印，防止中文逐字交错。
                if (source == null || source.isBlank()) System.out.print(delta.getDelta());
                else childText.computeIfAbsent(source, key -> new StringBuilder()).append(delta.getDelta());
            }
            else if (event instanceof ToolCallStartEvent tool) {
                tools.add(tool.getToolCallName());
                System.out.println("\n[工具 / " + label + "] " + tool.getToolCallName());
            } else if (event instanceof RequireUserConfirmEvent confirm) pending = confirm;
        }

        public void requireTool(String name) {
            if (!tools.contains(name)) throw new IllegalStateException("本次未调用 " + name + "，不能算完成对应演示。");
        }
    }

    public static Trace ask(HarnessAgent agent, String question, RuntimeContext context) {
        System.out.println("\n问题：" + question);
        return stream(agent, new UserMessage(question), context);
    }

    public static Trace stream(HarnessAgent agent, Msg message, RuntimeContext context) {
        Trace trace = new Trace();
        agent.streamEvents(message, context).doOnNext(trace::accept).blockLast(Duration.ofMinutes(4));
        System.out.println();
        return trace;
    }

    public static void chat(HarnessAgent agent, RuntimeContext context) throws IOException {
        BufferedReader reader = console();
        System.out.println("\n可以继续提问，exit 或 0 退出。");
        while (true) {
            System.out.print("问题：");
            String input = reader.readLine();
            if (input == null || "exit".equalsIgnoreCase(input.trim()) || "0".equals(input.trim())) return;
            if (!input.isBlank()) ask(agent, input, context);
        }
    }

    public static Model model() throws IOException {
        Properties config = new Properties();
        try (InputStream input = DemoSupport.class.getResourceAsStream("/application.properties")) {
            if (input != null) config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        String key = setting(config, "DEEPSEEK_API_KEY", "llm.api-key", "");
        if (key.isBlank()) throw new IllegalStateException("请配置 DEEPSEEK_API_KEY 或 llm.api-key");
        return OpenAIChatModel.builder().apiKey(key)
                .baseUrl(setting(config, "DEEPSEEK_BASE_URL", "llm.base-url", "https://api.deepseek.com"))
                .modelName(setting(config, "DEEPSEEK_MODEL", "llm.model", "deepseek-chat"))
                .stream(true).build();
    }

    private static String setting(Properties config, String env, String key, String fallback) {
        String value = System.getenv(env);
        return (value == null || value.isBlank() ? config.getProperty(key, fallback) : value).trim();
    }
}
