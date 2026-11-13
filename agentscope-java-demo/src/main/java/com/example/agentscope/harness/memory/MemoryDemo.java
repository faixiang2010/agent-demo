package com.example.agentscope.harness.memory;

import com.example.agentscope.DemoStorage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConsolidator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * 阅读路线：main -> saveMemory -> consolidateMemory -> recallMemory -> searchMemory。
 * 同一用户换新 session，聊天历史为空，仍可从工作区读取长期记忆。
 * 使用项目当前 2.0.4 的原生工具和整理组件，没有用 Java 直接写入示例记忆。
 */
public class MemoryDemo {
    private static final DemoStorage STORAGE = DemoStorage.harnessMemory();
    private static final String AGENT_ID = "memory-demo";
    private static final String USER_ID = "learner";

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        if (args.length > 1 || !Set.of("", "--auto", "--recall").contains(mode)) {
            throw new IllegalArgumentException("用法：MemoryDemo [--auto | --recall]");
        }
        Files.createDirectories(STORAGE.workspace());
        Model model = loadModel();
        System.out.println("老大，长期记忆是换了新会话后仍能使用的偏好和事实。");
        System.out.println("数据目录：" + STORAGE.root());

        if (!"--recall".equals(mode)) {
            RuntimeContext firstSession = newSession();
            try (HarnessAgent firstAgent = createAgent(model)) {
                saveMemory(firstAgent, firstSession);
                consolidateMemory(firstAgent, firstSession, model);
            } // 关闭旧实例，新实例也不恢复这个 session 的历史。
        }

        RuntimeContext newSession = newSession();
        System.out.println("\n创建新会话：" + newSession.getSessionId() + "；用户仍为 " + USER_ID);
        try (HarnessAgent newAgent = createAgent(model)) {
            recallMemory(newAgent, newSession);
            searchMemory(newAgent, newSession);
            if (!"--auto".equals(mode)) chat(newAgent, newSession);
        }
        System.out.println("已退出。下次传 --recall 可直接读取已保存的记忆，无需重跑保存和整理。");
    }

    /** 1. 由 LLM 调用框架的 memory_save，同时写入 MEMORY.md 和当日台账。 */
    private static void saveMemory(HarnessAgent agent, RuntimeContext context) {
        System.out.println("\n========== 1. 显式保存长期偏好 ==========");
        ask(agent, "请调用 memory_save 保存以下长期偏好：我喜欢中文、简短的回答；"
                + "Java 示例使用 JDK17；学习时希望先看简单示例，再看原理。不要只口头说记住了。", context);
        showMemoryFiles(agent, context);
    }

    /** 2. 调用原生整理组件，让 LLM 合并台账并重写 MEMORY.md。台账仍然保留。 */
    private static void consolidateMemory(HarnessAgent agent, RuntimeContext context, Model model) {
        System.out.println("\n========== 2. 整理长期记忆 ==========");
        System.out.println("MemoryConsolidator 读取日台账，调用模型合并、去重；这是一项额外模型调用。");
        new MemoryConsolidator(agent.getWorkspaceManager(), model)
                .consolidate(context).block(Duration.ofMinutes(3));
        showMemoryFiles(agent, context);
    }

    /** 3. 使用新的 sessionId，问题不再包含原来的偏好，靠长期记忆回答。 */
    private static void recallMemory(HarnessAgent agent, RuntimeContext context) {
        System.out.println("\n========== 3. 新会话回忆偏好 ==========");
        ask(agent, "根据已保存的长期记忆，我喜欢什么回答语言和风格？"
                + "Java 示例用哪个版本？学习时先看什么？没有记忆就说不知道，不要猜。", context);
    }

    /** 4. 显式要求检索与读取，终端可以看到真实的工具调用。 */
    private static void searchMemory(HarnessAgent agent, RuntimeContext context) {
        System.out.println("\n========== 4. 检索并读取记忆原文 ==========");
        ask(agent, "请先调用 memory_search，query 使用单个关键词 Java；"
                + "再调用 memory_get 读取命中文件的相关行。简短说明找到的偏好和来源路径。", context);
    }

    private static HarnessAgent createAgent(Model model) {
        return HarnessAgent.builder().name(AGENT_ID).agentId(AGENT_ID)
                .model(model)
                .sysPrompt("你是记忆教学助手，中文简短回答，称呼用户为老大。"
                        + "用户要求记住时使用 memory_save；要求检索时使用 memory_search 和 memory_get。"
                        + "只根据已有记忆回答，不编造偏好。")
                .workspace(STORAGE.workspace())
                .stateStore(new JsonFileAgentStateStore(STORAGE.state(AGENT_ID)))
                // 教学中手动执行整理，关闭自动提取和后台维护，避免输出和文件相互竞争。
                // 保留默认记忆工具！只关闭 Hooks 时，MEMORY.md 仍然会加载到模型请求。
                .disableMemoryHooks().disableCompaction()
                .disableFilesystemTools().disableShellTool().disableWebTools()
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableToolsConfig()
                .maxIters(6).build();
    }

    /** 用 WorkspaceManager 解析当前用户的实际路径，不把记忆路径写死。 */
    private static void showMemoryFiles(HarnessAgent agent, RuntimeContext context) {
        var manager = agent.getWorkspaceManager();
        for (String relative : new String[]{"MEMORY.md", "memory/" + LocalDate.now() + ".md"}) {
            Path physical = manager.resolveRuntimeDataPath(context, relative);
            String content = manager.readManagedWorkspaceFileUtf8(context, relative);
            System.out.println("\n[记忆文件] " + physical);
            System.out.println(content == null || content.isBlank() ? "（暂未生成）" : content);
        }
    }

    private static RuntimeContext newSession() {
        return RuntimeContext.builder().userId(USER_ID).sessionId("memory-" + UUID.randomUUID()).build();
    }

    private static void ask(HarnessAgent agent, String question, RuntimeContext context) {
        System.out.println("问题：" + question);
        System.out.print("Agent：");
        agent.streamEvents(new UserMessage(question), context)
                .doOnNext(event -> {
                    if (event instanceof TextBlockDeltaEvent text) System.out.print(text.getDelta());
                    else if (event instanceof ToolCallStartEvent tool) {
                        System.out.println("\n[记忆工具] " + tool.getToolCallName());
                    }
                })
                .blockLast(Duration.ofMinutes(3));
        System.out.println();
    }

    private static void chat(HarnessAgent agent, RuntimeContext context) throws Exception {
        var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("\n可以继续问，也可以说‘请记住：……’。/memory 查看文件，exit 或 0 退出。");
        while (true) {
            System.out.print("问题：");
            String input = console.readLine();
            if (input == null || "exit".equalsIgnoreCase(input.trim()) || "0".equals(input.trim())) return;
            if ("/memory".equalsIgnoreCase(input.trim())) showMemoryFiles(agent, context);
            else if (!input.isBlank()) ask(agent, input, context);
        }
    }

    /** 沿用项目的 DeepSeek 配置，环境变量优先。 */
    private static Model loadModel() throws Exception {
        Properties config = new Properties();
        try (InputStream input = MemoryDemo.class.getResourceAsStream("/application.properties")) {
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
        if (value == null || value.isBlank()) value = config.getProperty(key, fallback);
        return value.trim();
    }
}
