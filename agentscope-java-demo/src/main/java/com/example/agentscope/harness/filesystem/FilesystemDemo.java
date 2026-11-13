package com.example.agentscope.harness.filesystem;

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
import io.agentscope.harness.agent.workspace.LocalFsMode;

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
 * 阅读顺序：prepareProject -> createAgent -> editAndWrite -> searchFiles -> checkPathBoundary。
 * project 是教学项目原文件；workspace 是 Agent 默认写入修改的地方。
 * 每次运行创建独立目录，不覆盖以前的演示文件；--auto 只跑演示，默认还能继续提问。
 */
public class FilesystemDemo {
    private static final String AGENT_ID = "filesystem-demo";
    private static final String PLAN = "notes/plan.txt";
    private static final String ORIGINAL = "任务：学习 AgentScope 文件系统\n状态：待办\n负责人：老大\n";
    private static final DemoStorage STORAGE = new DemoStorage(
            DemoStorage.harnessFilesystem().root().resolve("run-" + UUID.randomUUID()));
    private static final Path PROJECT = STORAGE.root().resolve("project");

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !"--auto".equals(args[0]))) {
            throw new IllegalArgumentException("用法：FilesystemDemo [--auto]");
        }
        prepareProject();
        RuntimeContext context = RuntimeContext.builder().sessionId("files-" + UUID.randomUUID()).build();
        System.out.println("老大，Filesystem 就是 Agent 读写文件时使用的统一接口。");
        System.out.println("项目原文件 project：" + PROJECT);
        System.out.println("Agent 修改 workspace：" + STORAGE.workspace());

        try (HarnessAgent agent = createAgent(loadModel())) {
            editAndWrite(agent, context);
            searchFiles(agent, context);
            checkPathBoundary(agent, context);
            if (args.length == 0) chat(agent, context);
        }
        System.out.println("演示结束，所有教学文件保留在：" + STORAGE.root());
    }

    /** Java 只准备项目种子文件；接下来的修改和创建都由 LLM 调用框架文件工具完成。 */
    private static void prepareProject() throws Exception {
        Files.createDirectories(PROJECT.resolve("notes"));
        Files.createDirectories(STORAGE.workspace());
        Files.writeString(PROJECT.resolve(PLAN), ORIGINAL, StandardCharsets.UTF_8);
    }

    private static HarnessAgent createAgent(Model model) {
        return HarnessAgent.builder().name(AGENT_ID).agentId(AGENT_ID).model(model)
                .sysPrompt("你是文件系统教学助手，中文简短回答，称呼用户为老大。"
                        + "操作文件必须使用文件工具，使用 notes/... 相对路径，不要自己拼宿主绝对路径。"
                        + "只操作教学 notes 目录，不删除文件。工具失败时说明原因，不虚报成功。")
                .workspace(STORAGE.workspace())
                .filesystem(new LocalFilesystemSpec()
                        .project(PROJECT)                 // 读取工作区没有的文件时，从项目目录兜底读取。
                        .mode(LocalFsMode.ROOTED)          // 只允许配置的目录范围。
                        .sharedLocalWorkspace(true))      // 单用户教学，让相对路径与打印的目录一致。
                // projectWritable 默认 false：写入落在 workspace，读取优先 workspace。
                .stateStore(new JsonFileAgentStateStore(STORAGE.state(AGENT_ID)))
                .disableMemoryHooks().disableMemoryTools().disableCompaction()
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableToolsConfig().disableWorkspaceContext()
                .disableShellTool().disableWebTools()
                .maxIters(12).build(); // 保留默认文件工具。
    }

    /** 1. 读取项目原文件，修改后再读，同时创建一个新文件。 */
    private static void editAndWrite(HarnessAgent agent, RuntimeContext context) throws Exception {
        System.out.println("\n========== 1. 读取、修改、创建文件 ==========");
        ask(agent, "请先用 read_file 读取 notes/plan.txt；再用 edit_file 把‘状态：待办’"
                + "替换为‘状态：完成’；用 write_file 创建 notes/summary.txt，写入一行简单学习小结；"
                + "最后用 read_file 再读 notes/plan.txt，确认完成。", context);

        // 直接读取真实磁盘来核对模型的操作，而不是只看模型是否说‘完成了’。
        String projectText = Files.readString(PROJECT.resolve(PLAN));
        Path changed = STORAGE.workspace().resolve(PLAN);
        Path summary = STORAGE.workspace().resolve("notes/summary.txt");
        if (!ORIGINAL.equals(projectText) || !Files.exists(changed)
                || !Files.readString(changed).contains("状态：完成")
                || !Files.exists(summary) || Files.readString(summary).isBlank()) {
            throw new IllegalStateException("文件操作未全部完成，请检查前面的工具输出；教学文件保留供核对。");
        }
        System.out.println("\n[项目原文件，仍然是待办]\n" + projectText);
        System.out.println("[工作区副本，已经是完成]\n" + Files.readString(changed));
        System.out.println("[新建文件] " + summary + "\n" + Files.readString(summary));
        System.out.println("[验证通过] 修改写到 workspace，project 原文件未变；后续读取优先工作区副本。");
    }

    /** 2. list 看目录、glob 按文件名匹配、grep 按文件内容搜索。 */
    private static void searchFiles(HarnessAgent agent, RuntimeContext context) {
        System.out.println("\n========== 2. 列目录、找文件、搜内容 ==========");
        ask(agent, "请调用 list_files 列出 notes 目录，再调用 glob_files 找 notes 目录下的 *.txt 文件，"
                + "然后调用 grep_files 在 notes 目录中搜索关键词‘完成’。简短报告文件名和匹配内容。", context);
    }

    /** 3. 直接调用同一个 Filesystem 验证路径策略，不让模型猜是否允许访问。 */
    private static void checkPathBoundary(HarnessAgent agent, RuntimeContext context) throws Exception {
        System.out.println("\n========== 3. 验证配置目录之外的访问 ==========");
        // 这是本例自己创建的文件，位于 run 根目录，不在 project / workspace 内。
        Path outside = STORAGE.root().resolve("outside-example.txt");
        Files.writeString(outside, "教学用范围外文件\n", StandardCharsets.UTF_8);
        System.out.println("尝试读取：" + outside);
        boolean blocked;
        try {
            var result = agent.getWorkspaceManager().getFilesystem().read(context, outside.toString(), 0, 10);
            blocked = !result.isSuccess();
            System.out.println("[文件系统返回] " + result.error());
        } catch (SecurityException e) {
            blocked = true;
            System.out.println("[路径策略拒绝] " + e.getMessage());
        }
        if (!blocked) throw new IllegalStateException("目录范围外的文件意外读取成功，路径验证失败。");
        System.out.println("[验证通过] 存在的范围外文件不能通过这个 Filesystem 读取。");
    }

    private static void ask(HarnessAgent agent, String question, RuntimeContext context) {
        System.out.println("问题：" + question);
        System.out.print("Agent：");
        agent.streamEvents(new UserMessage(question), context)
                .doOnNext(event -> {
                    if (event instanceof TextBlockDeltaEvent text) System.out.print(text.getDelta());
                    else if (event instanceof ToolCallStartEvent tool) {
                        System.out.println("\n[文件工具] " + tool.getToolCallName());
                    }
                }).blockLast(Duration.ofMinutes(3));
        System.out.println();
    }

    private static void chat(HarnessAgent agent, RuntimeContext context) throws Exception {
        var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("\n可以继续提问，例如：读取 notes/summary.txt。exit 或 0 退出。");
        while (true) {
            System.out.print("问题：");
            String input = console.readLine();
            if (input == null || "exit".equalsIgnoreCase(input.trim()) || "0".equals(input.trim())) return;
            if (!input.isBlank()) ask(agent, input, context);
        }
    }

    /** 沿用项目的 DeepSeek 配置。 */
    private static Model loadModel() throws Exception {
        Properties config = new Properties();
        try (InputStream input = FilesystemDemo.class.getResourceAsStream("/application.properties")) {
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
