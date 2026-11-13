package com.example.agentscope.harness.workspace;

import com.example.agentscope.DemoStorage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

/**
 * 阅读顺序：prepareWorkspace -> createAgent -> demonstrate -> ask。
 * Workspace 是 Agent 的配置、参考资料与运行产物的工作区；本例使用本机文件。
 * 默认自动演示三个步骤，再进入自由提问。--auto 只演示，不等待终端输入。
 */
public class WorkspaceDemo {
    private static final DemoStorage STORAGE = DemoStorage.harnessWorkspace();

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !"--auto".equals(args[0]))) {
            throw new IllegalArgumentException("用法：WorkspaceDemo [--auto]");
        }
        Path workspace = STORAGE.workspace();
        prepareWorkspace(workspace);
        System.out.println("老大，Workspace 就是 Agent 的工作资料夹：" + workspace);
        System.out.println("AGENTS.md：行为规则；knowledge/：知识文件；PREFERENCES.md：额外参考材料。");

        try (HarnessAgent agent = createAgent(loadModel(), workspace)) {
            demonstrate(agent, workspace);
            if (args.length == 0) {
                chat(agent);
            }
        }
        System.out.println("演示结束，工作区文件保留在：" + workspace);
    }

    /** 步骤1：准备几个容易理解的文件。已存在的文件不覆盖，可以自己编辑后重跑。 */
    static void prepareWorkspace(Path workspace) throws Exception {
        writeIfMissing(workspace.resolve("AGENTS.md"), """
                # 图书馆助手
                你是“向日葵图书馆”的助手。
                用中文回答，称呼用户为“老大”。
                只根据工作区资料回答营业时间与借阅规定，不知道就说不知道。
                需要借阅细则时，使用 read_file 读取 knowledge/borrowing.md。
                不修改任何文件。
                """);
        writeIfMissing(workspace.resolve("knowledge/KNOWLEDGE.md"), """
                # 图书馆知识入口
                名称：向日葵图书馆。
                营业时间：周二至周日 08:00—20:00，周一闭馆。
                借阅细则在 knowledge/borrowing.md，需要时读取该文件。
                """);
        writeIfMissing(workspace.resolve("knowledge/borrowing.md"), """
                # 借阅细则（虚构的教学资料）
                每人最多借 3 本书，借期 14 天，可以续借 1 次。
                续借口令：SUNFLOWER-7319。
                """);
        writeIfMissing(workspace.resolve("PREFERENCES.md"), """
                # 回答格式偏好
                回答尽量简短，使用项目符号。
                """);
    }

    /** 步骤2：告诉 HarnessAgent 工作区位置，框架负责加载文件与提供 read_file 工具。 */
    static HarnessAgent createAgent(Model model, Path workspace) {
        return HarnessAgent.builder()
                .name("library-assistant")
                .agentId("workspace-demo")
                .model(model)
                .workspace(workspace)
                // 单用户教学：让 read_file 的相对路径与 Java 创建文件的位置一致。
                .filesystem(new LocalFilesystemSpec().project(workspace).sharedLocalWorkspace(true))
                .additionalContextFile("PREFERENCES.md")
                .stateStore(new JsonFileAgentStateStore(STORAGE.state("workspace-demo")))
                // 为了集中观察文件加载，关闭本例用不到的能力，保留文件工具。
                .disableMemoryHooks().disableMemoryTools().disableCompaction()
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableShellTool().disableWebTools().disableToolsConfig()
                .maxIters(6)
                .build();
    }

    /** 步骤3：同一个 Agent，依次观察自动加载、按需读取、下一次调用重新加载。 */
    static void demonstrate(HarnessAgent agent, Path workspace) throws Exception {
        System.out.println("\n========== 1. 自动加载行为规则和知识入口 ==========");
        ask(agent, "你是哪家图书馆的助手？营业时间是什么？", newSession());

        System.out.println("\n========== 2. 明细文件按需读取 ==========");
        System.out.println("观察 [工具调用] read_file：borrowing.md 的正文不是预先全文注入的。");
        ask(agent, "请读取 knowledge/borrowing.md，告诉我最多借几本、借多久、续借口令是什么？", newSession());

        System.out.println("\n========== 3. 修改文件，下次调用生效 ==========");
        Path preferences = workspace.resolve("PREFERENCES.md");
        String original = Files.readString(preferences);
        try {
            // Java 程序修改文件；不是模型自己修改。下一次 call 由框架重新加载。
            Files.writeString(preferences, "回答必须以【工作区已刷新】开头，只用一句话回答。\n");
            // 使用新会话，避免上一步聊天历史干扰；复用同一个 HarnessAgent。
            ask(agent, "图书馆周一开门吗？", newSession());
        } finally {
            Files.writeString(preferences, original);
        }
        System.out.println("已恢复原来的 PREFERENCES.md。继续提问时会再次加载。");
    }

    private static RuntimeContext newSession() {
        return RuntimeContext.builder().sessionId("workspace-" + UUID.randomUUID()).build();
    }

    private static void ask(HarnessAgent agent, String question, RuntimeContext context) {
        System.out.println("问题：" + question);
        System.out.print("Agent：");
        agent.streamEvents(new UserMessage(question), context)
                .doOnNext(event -> {
                    if (event instanceof TextBlockDeltaEvent delta) {
                        System.out.print(delta.getDelta());
                    } else if (event instanceof ToolCallStartEvent tool) {
                        System.out.println("\n[工具调用] " + tool.getToolCallName());
                    }
                })
                .blockLast(Duration.ofMinutes(3));
        System.out.println();
    }

    private static void chat(HarnessAgent agent) throws Exception {
        RuntimeContext context = newSession();
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("\n现在可以自己提问，例如：可以续借几次？输入 exit 或 0 退出。");
        while (true) {
            System.out.print("问题：");
            String question = console.readLine();
            if (question == null || "exit".equalsIgnoreCase(question.trim()) || "0".equals(question.trim())) return;
            if (!question.isBlank()) ask(agent, question, context);
        }
    }

    private static void writeIfMissing(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** 沿用本项目的 DeepSeek 配置，不需要另外配置模型。 */
    private static Model loadModel() throws Exception {
        Properties config = new Properties();
        try (InputStream input = WorkspaceDemo.class.getResourceAsStream("/application.properties")) {
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
