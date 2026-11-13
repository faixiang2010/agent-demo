package other;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/** Skills 示例：发现技能 -> read_skill -> 按需披露绑定工具 -> Milvus 检索 -> 按技能要求回答。 */
public class SkillCallDemo {

    private static final String DASHSCOPE_API_KEY = "System.getenv("AI_DASHSCOPE_API_KEY")";
    private static final String SKILL_NAME = "deepseek-company-qa";
    private static final String EXAMPLE_QUESTION = "DeepSeek的核心产品线有哪些？分别提供什么能力？";

    public static void main(String[] args) throws Exception {
        var registry = ClasspathSkillRegistry.builder().classpathPath("skills")
                // JAR 资源需要复制到本地，Windows 使用项目 target 目录。
                .basePath(Path.of("target", "skill-runtime").toAbsolutePath().toString()).build();
        try {
            if (!registry.contains(SKILL_NAME)) {
                throw new IllegalStateException("未发现技能，请确认 src/main/resources/skills 在运行 classpath 中");
            }
            registry.listAll().forEach(skill -> System.out.println("发现 Skill：" + skill.getName()
                    + "，说明：" + skill.getDescription()));

            var knowledgeTool = FunctionToolCallback.builder("search_deepseek_knowledge", (KnowledgeRequest input) -> {
                try {
                    var documents = RagDemo.retrieveKnowledge(input.question());
                    if (documents.isEmpty()) {
                        return "现有知识库中没有检索到相关资料。";
                    }
                    var result = new StringBuilder();
                    for (var document : documents) {
                        result.append("来源：").append(document.getMetadata().get("source"))
                                .append("\n网页抓取时间：").append(document.getMetadata().get("fetched_at"))
                                .append("\n相似度：").append(document.getScore())
                                .append("\n正文：").append(document.getText()).append("\n\n");
                    }
                    return result.toString();
                } catch (Exception e) {
                    throw new IllegalStateException("知识检索失败：" + e.getMessage(), e);
                }
            }).description("只读检索DeepSeek官网的现有 Milvus 知识，question 为用户的具体问题。")
                    .inputType(KnowledgeRequest.class).build();

            // 技能元数据先注入提示；read_skill 加载完整指令后，绑定工具才向模型披露。
            var skillsHook = SkillsAgentHook.builder().skillRegistry(registry)
                    .groupedTools(Map.of(SKILL_NAME, List.of(knowledgeTool))).build();
            var trace = new SkillTraceInterceptor();
            var model = DashScopeChatModel.builder()
                    .dashScopeApi(DashScopeApi.builder().apiKey(DASHSCOPE_API_KEY).build())
                    .defaultOptions(DashScopeChatOptions.builder().model("qwen-plus").temperature(0.0).build())
                    .build();
            var agent = ReactAgent.builder().name("deepseek-skills-agent").model(model)
                    .systemPrompt("你是中文助手。任务匹配可用技能时，先用 read_skill 加载该技能，再遵循完整技能指令。"
                            + "关于DeepSeek公司的事实必须通过知识工具核实，不能凭记忆回答。")
                    .hooks(List.of(skillsHook)).interceptors(trace).build();

            if (args.length > 0) {
                boolean verify = "--verify".equals(args[0]);
                ask(agent, verify ? EXAMPLE_QUESTION : String.join(" ", args));
                if (verify) {
                    if (trace.skillReads.get() == 0 || trace.knowledgeSearches.get() == 0) {
                        throw new IllegalStateException("未验证到 read_skill 与绑定工具均被实际调用");
                    }
                    System.out.println("验证通过：模型实际加载了 Skill，并调用了绑定的知识检索工具。");
                }
                return;
            }
            var scanner = new Scanner(System.in);
            System.out.println("示例问题：" + EXAMPLE_QUESTION + " 输入 exit 退出。");
            while (true) {
                System.out.print("问题：");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String question = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(question)) {
                    break;
                }
                if (question.isBlank()) {
                    continue;
                }
                try {
                    ask(agent, question);
                } catch (Exception e) {
                    System.err.println("调用失败：" + e.getMessage());
                }
            }
        } finally {
            registry.close();
            reactor.netty.http.HttpResources.disposeLoopsAndConnections();
        }
    }

    private static void ask(ReactAgent agent, String question) throws Exception {
        System.out.println("用户问题：" + question);
        System.out.println("Agent 回答：\n" + agent.call(question).getText());
    }

    public record KnowledgeRequest(String question) { }

    /** 展示真实的工具执行顺序；验证模式还会统计成功的技能读取和知识检索。 */
    private static class SkillTraceInterceptor extends ToolInterceptor {
        private final AtomicInteger skillReads = new AtomicInteger();
        private final AtomicInteger knowledgeSearches = new AtomicInteger();

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            System.out.println("[Skill 调用链] " + request.getToolName() + "，参数：" + request.getArguments());
            var response = handler.call(request);
            if (!response.isError()) {
                if ("read_skill".equals(request.getToolName())) {
                    skillReads.incrementAndGet();
                } else if ("search_deepseek_knowledge".equals(request.getToolName())) {
                    knowledgeSearches.incrementAndGet();
                }
            }
            return response;
        }

        @Override
        public String getName() {
            return "skill-call-trace";
        }
    }
}
