package com.example.agentscope.harness.session;

import com.example.agentscope.DemoStorage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.session.SessionEntry;
import io.agentscope.harness.agent.memory.session.SessionTree;
import io.agentscope.harness.agent.transcript.FilesystemTranscriptStore;
import io.agentscope.harness.agent.transcript.TranscriptRef;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 阅读路线：main -> createAgent -> ask -> showHistory。
 * 本项目是 2.0.4：Transcript 保存聊天记录，AgentStateStore 保存继续对话的工作状态。
 * 新版文档的原生 SessionLog / AgentSession 尚不在该版本中，详见同目录 README。
 */
public class SessionLogDemo {
    private static final DemoStorage STORAGE = DemoStorage.harnessSession();
    private static final String AGENT_ID = "session-log-demo";
    private static final String USER_ID = "demo-user";
    private static final Path LAST_SESSION = STORAGE.root().resolve("last-session.txt");

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        if (args.length > 1 || !Set.of("", "--auto", "--resume", "--history").contains(mode)) {
            throw new IllegalArgumentException("用法：SessionLogDemo [--auto | --resume | --history]");
        }
        Files.createDirectories(STORAGE.workspace());
        RuntimeContext context = context("--resume".equals(mode) || "--history".equals(mode)
                ? lastSession() : "chat-" + UUID.randomUUID());
        System.out.println("老大，会话就是同一个用户与同一个 Agent 的一段持续对话。");
        System.out.printf("agentId=%s，userId=%s，sessionId=%s%n", AGENT_ID, USER_ID, context.getSessionId());
        System.out.println("数据目录：" + STORAGE.root());

        // 读取历史完全不需要构建模型，也不会执行一次新的 Agent 调用。
        if ("--history".equals(mode)) {
            showHistory(context);
            return;
        }
        Model model = loadModel();
        if (!"--resume".equals(mode)) {
            System.out.println("\n========== 1. 聊一句，框架自动保存 ==========");
            try (HarnessAgent firstAgent = createAgent(model)) {
                ask(firstAgent, "请记住：我的借书证编号是 LIB-7319，我借了《Java入门》。简短确认即可。", context);
            } // 关闭第一个实例，接下来不会复用其内存。
            Files.writeString(LAST_SESSION, context.getSessionId(), StandardCharsets.UTF_8);

            System.out.println("\n========== 2. 只读取聊天记录，不调用模型 ==========");
            showHistory(context);
        }

        System.out.println("\n========== 3. 新建 Agent，沿用身份和存储 ==========");
        try (HarnessAgent rebuiltAgent = createAgent(model)) {
            if ("--resume".equals(mode)) {
                System.out.println("已选择上次会话。先查看历史，再输入问题继续。");
                showHistory(context);
            } else {
                // 问题里不包含编号和书名；框架从状态存储恢复此前的对话。
                ask(rebuiltAgent, "我的借书证编号是什么？我借了什么书？", context);
                showHistory(context);
            }
            if (!"--auto".equals(mode)) chat(rebuiltAgent, context);
        }
        System.out.println("已退出。下次用 --resume 继续，用 --history 只查看历史。");
    }

    /** 两类存储明确分开：聊天记录用于回查；工作状态用于恢复模型的对话上下文。 */
    private static HarnessAgent createAgent(Model model) {
        return HarnessAgent.builder()
                // 2.0.4 的 Transcript 按 name 分目录，让 name 与 agentId 一致，便于回查。
                .name(AGENT_ID).agentId(AGENT_ID)
                .model(model)
                .sysPrompt("你是借阅助手。中文简短回答，称呼用户为老大；只依据对话回答，不知道就说不知道。")
                .workspace(STORAGE.workspace())
                .transcriptStore(new FilesystemTranscriptStore(STORAGE.root().resolve("transcripts")))
                .transcriptTenant(USER_ID)
                .stateStore(new JsonFileAgentStateStore(STORAGE.state(AGENT_ID)))
                // 聚焦日志与恢复：不启用工具、长期记忆和压缩等无关功能。
                .disableFilesystemTools().disableShellTool().disableWebTools()
                .disableMemoryTools().disableMemoryHooks().disableCompaction()
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableToolsConfig().disableWorkspaceContext()
                .build(); // 保留默认的 Transcript 记录中间件，不要 disableTranscript()。
    }

    /** 一次调用完成后，框架会自动记录新的消息，不需要手动 appendMessages。 */
    private static void ask(HarnessAgent agent, String question, RuntimeContext context) {
        System.out.println("问题：" + question);
        System.out.print("Agent：");
        agent.streamEvents(new UserMessage(question), context)
                .doOnNext(event -> {
                    if (event instanceof TextBlockDeltaEvent text) System.out.print(text.getDelta());
                })
                .blockLast(Duration.ofMinutes(3));
        System.out.println();
    }

    /** 通过当前版本的 TranscriptStore 列出分段并读取；不是让 LLM 总结历史。 */
    private static void showHistory(RuntimeContext context) throws Exception {
        // 2.0.4 的分段写入是异步镜像。等待已提交的写入任务后再读，避免立即读取看不到新记录。
        if (!SessionTree.awaitMirrorQuiescence(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("等待聊天记录写入超时，请稍后使用 --history 再读。");
        }
        var store = new FilesystemTranscriptStore(STORAGE.root().resolve("transcripts"));
        var ref = new TranscriptRef(USER_ID, AGENT_ID, context.getSessionId());
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (var segment : store.listSegments(ref)) {
            try (var reader = new BufferedReader(new InputStreamReader(
                    store.readSegment(segment.key()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    SessionEntry entry = JsonUtils.getJsonCodec().fromJson(line, SessionEntry.class);
                    if (entry instanceof SessionEntry.MessageEntry message && seen.add(entry.getId())) {
                        System.out.printf("[历史 %d] %s：%s%n", ++count, message.getRole(), message.getContent());
                    }
                }
            }
        }
        System.out.println("[历史读取完成] 共 " + count + " 条消息，没有调用模型或追加对话。");
        if (count == 0) System.out.println("暂无记录，请先运行默认演示。若刚结束调用，请检查是否有写入失败日志。");
    }

    /** 同一段自由对话始终沿用同一个 context；输入 /history 可以随时查看记录。 */
    private static void chat(HarnessAgent agent, RuntimeContext context) throws Exception {
        var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("\n可以继续输入问题。/history 查看历史；exit 或 0 退出。");
        while (true) {
            System.out.print("问题：");
            String question = console.readLine();
            if (question == null || "exit".equalsIgnoreCase(question.trim()) || "0".equals(question.trim())) return;
            if ("/history".equalsIgnoreCase(question.trim())) showHistory(context);
            else if (!question.isBlank()) ask(agent, question, context);
        }
    }

    private static RuntimeContext context(String sessionId) {
        return RuntimeContext.builder().userId(USER_ID).sessionId(sessionId).build();
    }

    private static String lastSession() throws Exception {
        if (!Files.exists(LAST_SESSION)) throw new IllegalStateException("还没有上次会话，请先运行默认演示。");
        String id = Files.readString(LAST_SESSION, StandardCharsets.UTF_8).trim();
        if (!id.matches("chat-[0-9a-f-]{36}")) throw new IllegalStateException("last-session.txt 中的会话 ID 无效。");
        return id;
    }

    /** 与已有示例一致，复用 application.properties，环境变量优先。 */
    private static Model loadModel() throws Exception {
        Properties config = new Properties();
        try (InputStream input = SessionLogDemo.class.getResourceAsStream("/application.properties")) {
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
