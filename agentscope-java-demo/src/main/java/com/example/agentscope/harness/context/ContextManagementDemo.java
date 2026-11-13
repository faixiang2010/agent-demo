package com.example.agentscope.harness.context;

import com.example.agentscope.DemoStorage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.message.*;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.*;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.memory.MemoryFlushManager;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.memory.session.SessionTranscriptWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.agentscope.harness.context.ContextDemoSupport.*;

/**
 * 读代码路线：main -> run的数字switch -> scenario01~scenario18。
 * 选数字后自动跑完整流程，方法内步骤注释与控制台日志一一对应。
 * 想改变效果，直接改对应方法开头的数据，再选同一数字。
 * 2.0.4缺少新版contextSource/ContextPolicy等接口，兼容演示会明确标注。
 */
public class ContextManagementDemo {
    private static final String[] SCENES = {
            "程序存了信息，模型就知道吗？", "回答规则和参考资料怎么放？", "业务状态更新后，怎么让模型看到最新值？",
            "资料太多，先保留哪些？", "资料来源失败或超时，怎么办？", "待办完成，是否代表验证通过？",
            "工具返回长日志，怎么搬到文件？", "本次输入超预算，怎么在请求前发现？", "聊天太长，怎么生成摘要？",
            "旧工具结果，怎么裁剪？", "旧工具参数，怎么截断？", "上下文溢出，怎么压缩后重试？",
            "改了AGENTS.md，下次请求生效吗？", "摘要、长期事实、完整历史有什么区别？", "怎么检查真实模型输入和流式事件？",
            "综合流程：一次请求里，工具执行后刷新资料", "长任务：原生Todo更新、压缩后恢复和计划模式",
            "记忆与记录：模型提取、整理、新会话读取及原生历史回查"
    };
    private static final String[] METHODS = {
            "scenario01ContextAndState", "scenario02RulesAndMaterials", "scenario03DynamicSource",
            "scenario04ReferenceSelection", "scenario05SourceFailure", "scenario06TasksAndVerification",
            "scenario07ToolResultOffload", "scenario08Budget", "scenario09Compaction",
            "scenario10PruneResults", "scenario11TruncateArguments", "scenario12OverflowRecovery",
            "scenario13RefreshTiming", "scenario14MemoryAndHistory", "scenario15Diagnostics",
            "scenario16ToolLoop", "scenario17TaskRecovery", "scenario18MemoryLifecycle"
    };

    private final BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final DemoStorage storage = DemoStorage.harnessContext();
    private final boolean offline;
    private Model model;
    private int sceneNumber;
    private String scene;
    private RuntimeContext ctx;
    private JsonFileAgentStateStore stateStore;
    private RecordingModel recording;
    private Path workspace;
    private Path reports;
    private final List<String> observations = new ArrayList<>();

    public ContextManagementDemo(boolean offline) { this.offline = offline; }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !"--offline".equals(args[0]))) {
            throw new IllegalArgumentException("用法：ContextManagementDemo [--offline]");
        }
        new ContextManagementDemo(args.length == 1).run();
    }

    /** 唯一输入点：只选数字，场景中不再询问或暂停。 */
    private void run() {
        System.out.println("老大，上下文就是这次回答前交给模型看的信息。");
        System.out.println(offline ? "模式：离线模拟，检查框架过程，不生成真实业务答案。"
                : "模式：真实模型，使用已有application.properties / DEEPSEEK_*配置。");
        System.out.println("数据目录：" + storage.root());
        System.out.println("选数字 → 自动跑完整流程 → 在同名方法中对照步骤日志看代码。");
        while (true) {
            System.out.println("\n========== 数字菜单 ==========");
            for (int i = 0; i < SCENES.length; i++) System.out.printf("%2d. %s → %s()%n", i + 1, SCENES[i], METHODS[i]);
            System.out.println(" 0. 退出。先看1、2、3，再看7、9；综合流程看16、17、18。场景内无需操作。");
            System.out.print("选择场景：");
            try {
                String input = console.readLine();
                if (input == null || "0".equals(input.trim()) || "/exit".equalsIgnoreCase(input.trim())) break;
                int choice;
                try { choice = Integer.parseInt(input.trim()); }
                catch (NumberFormatException e) { System.out.println("请输入0到" + SCENES.length + "之间的整数。"); continue; }
                if (choice < 1 || choice > SCENES.length) { System.out.println("请输入0到" + SCENES.length + "之间的整数。"); continue; }
                prepare(choice);
                // IDEA Ctrl+B跳到对应方法；或Ctrl+F搜索scenario01、scenario09等编号。
                switch (choice) {
                    case 1 -> scenario01ContextAndState();
                    case 2 -> scenario02RulesAndMaterials();
                    case 3 -> scenario03DynamicSource();
                    case 4 -> scenario04ReferenceSelection();
                    case 5 -> scenario05SourceFailure();
                    case 6 -> scenario06TasksAndVerification();
                    case 7 -> scenario07ToolResultOffload();
                    case 8 -> scenario08Budget();
                    case 9 -> scenario09Compaction();
                    case 10 -> scenario10PruneResults();
                    case 11 -> scenario11TruncateArguments();
                    case 12 -> scenario12OverflowRecovery();
                    case 13 -> scenario13RefreshTiming();
                    case 14 -> scenario14MemoryAndHistory();
                    case 15 -> scenario15Diagnostics();
                    case 16 -> scenario16ToolLoop();
                    case 17 -> scenario17TaskRecovery();
                    case 18 -> scenario18MemoryLifecycle();
                    default -> throw new IllegalStateException("未知场景");
                }
                write(reports.resolve("学习小结.txt"), "代码方法：" + METHODS[choice - 1] + "\n" + String.join("\n", observations));
                System.out.println("[本场景完成] 中文小结及输入记录：" + reports);
                System.out.println("自动回到数字菜单。改本方法开头的数据，再选同一个数字即可对比。");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                System.out.println("[本场景未完成] " + e.getClass().getSimpleName() + "：" + e.getMessage());
                System.out.println("检查模型配置或网络，也可加--offline离线运行。已有记录保留，返回菜单。");
            }
        }
        System.out.println("已退出。数据保留在：" + storage.root());
    }

    // ==================== 场景01：菜单1对应这里 ====================
    private void scenario01ContextAndState() {
        // 默认数据：改这里，再选1。ctx是请求身份/属性，属性不会自动进入模型输入。
        String fact = "本次审批编号是 APPROVAL-7831";
        String question = "我的审批编号是什么？没有资料就说不知道，不要猜。";

        // 步骤1：存进程序，直接提问；预期模型输入没有fact。
        step(1, "把编号存到RuntimeContext，只提问，不把编号放进模型消息。", "保存的事实=" + fact);
        ctx = RuntimeContext.builder().userId("learner").sessionId(ctx.getSessionId()).put("business_fact", fact).build();
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        check("第一次模型输入包含编号", text(recording.latest()).contains(fact));

        // 步骤2：事实、问题和会话不变，只改变业务材料注入方式。
        step(2, "通过中间件把business_fact临时附到输入，再问同一个问题。", "关键代码=middleware(temporaryContext(...))");
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> c.get("business_fact"))).build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        check("第二次模型输入包含编号", text(recording.latest()).contains(fact));

        // 步骤3：保存的状态与本轮输入不同，临时材料不写进工作历史。
        step(3, "读取保存的AgentState，对照第二次模型输入。", "关键代码=stateStore.get(userId, sessionId, ...)");
        AgentState state = currentState();
        check("临时材料被作为消息写进持久历史", text(state.getContext()).contains("demo-temporary-context"));
        conclusion("程序的信息不会全部自动告诉模型。本轮材料可以进入模型输入，但不必存成聊天历史；此注入是2.0.4兼容演示。");
    }

    // ==================== 场景02：菜单2对应这里 ====================
    private void scenario02RulesAndMaterials() {
        String rule = "只用两条要点回答，每条不超过30字。";
        String material = "项目使用 JDK 17，构建工具为 Maven 3.9。";
        String question = "介绍这个项目的开发环境。";

        // 步骤1：规则规定回答方式，资料提供回答依据。
        step(1, "写入规则文件和参考文件。", "规则=" + rule + "；资料=" + material);
        write(workspace.resolve("AGENTS.md"), "# 助手职责\n" + rule);
        write(workspace.resolve("project-guide.md"), "# 项目参考资料\n" + material);

        // 步骤2：在本方法直接配置、创建、调用、关闭Agent。
        step(2, "配置稳定规则和参考文件，再发送问题。", "关键代码=sysPrompt(...) + additionalContextFile(...)");
        try (HarnessAgent agent = baseBuilder().sysPrompt("中文回答，遵守规则：" + rule)
                .additionalContextFile("project-guide.md").build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }

        // 步骤3：先确认输入，再看回答是否遵守格式、是否使用资料。
        step(3, "检查真正交给模型的内容。", "看回答是否用了两条要点和JDK/Maven资料；离线只看输入检查。");
        check("模型收到回答规则", text(recording.latest()).contains(rule));
        check("模型收到参考资料", text(recording.latest()).contains(material));
        conclusion("规则控制怎么回答，资料支撑回答内容。2.0.4工作区注入可能位于System，不能当成新版资料隔离保证。");
    }

    // ==================== 场景03：菜单3对应这里 ====================
    private void scenario03DynamicSource() {
        String initialStatus = "测试失败，登录模块有2项失败";
        String updatedStatus = "测试全部通过";
        String question = "现在构建状态如何？下一步建议是什么？";
        AtomicInteger loads = new AtomicInteger();
        String[] businessStatus = {initialStatus}; // 模拟业务系统当前值，不是用户新消息。

        // 步骤1：真实中间件每次模型调用前读取当前值，不缓存初始结果。
        step(1, "准备动态来源，每次模型调用前查询businessStatus。", "初始状态=" + initialStatus);
        var source = temporaryContext(c -> "当前构建状态=" + businessStatus[0] + "；查询次数=" + loads.incrementAndGet());
        try (HarnessAgent agent = baseBuilder().middleware(source).build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        check("第一次输入含初始状态", text(recording.latest()).contains("当前构建状态=" + initialStatus));

        // 步骤2：只改变业务数据，再发送同一个问题。
        step(2, "把业务状态改为通过，再问同一个问题。", "更新状态=" + updatedStatus);
        businessStatus[0] = updatedStatus;
        try (HarnessAgent agent = baseBuilder().middleware(source).build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        check("第二次输入含更新状态", text(recording.latest()).contains("当前构建状态=" + updatedStatus));

        // 步骤3：业务事实重新加载，旧聊天中可能仍然有旧回答。
        step(3, "核对查询次数和持久历史。", "来源查询次数=" + loads.get());
        check("临时来源被存成历史消息", text(currentState().getContext()).contains("demo-temporary-context"));
        conclusion("同一个问题也应查最新业务状态。这里是中间件兼容演示，非新版contextSource；来源查询应只读。");
    }

    // ==================== 场景04：菜单4对应这里 ====================
    private void scenario04ReferenceSelection() {
        // 应用字符预算策略，非新版ContextBlock/ContextSelectionPolicy。
        record Reference(String id, String content, int priority, boolean required) { }
        String revision = "guide-v3";
        int budget = 22;
        List<Reference> references = List.of(new Reference("必需", "登录失败返回401", 100, true),
                new Reference("重要", "检查登录会话是否过期", 20, false),
                new Reference("次要", "项目首页采用蓝色背景", 1, false));

        // 步骤1：required优先，其次priority降序；版本只用于追踪。
        step(1, "准备三份资料，先保留必需和重要资料。", "版本=" + revision + "；字符预算=" + budget);
        List<Reference> ordered = references.stream().sorted(Comparator.comparing(Reference::required).reversed()
                .thenComparing(Comparator.comparingInt(Reference::priority).reversed())).toList();
        int requiredSize = references.stream().filter(Reference::required).mapToInt(r -> r.content().length()).sum();
        if (requiredSize > budget) throw new IllegalArgumentException("必需资料超预算，不能静默丢弃");
        StringBuilder selected = new StringBuilder();
        int used = 0;
        for (Reference ref : ordered) {
            if (used + ref.content().length() <= budget) {
                used += ref.content().length();
                selected.append(ref.id()).append('@').append(revision).append(':').append(ref.content()).append('\n');
                System.out.println("[保留] " + ref.id() + "：" + ref.content());
            } else System.out.println("[省略] " + ref.id() + "：" + ref.content());
        }

        // 步骤2：只把入选材料交给模型。
        step(2, "把筛选后的资料附到输入，询问登录问题。", "实际选择=" + used + "/" + budget + "字符（非token预算）");
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> selected.toString())).build()) {
            Msg reply = agent.call(new UserMessage("登录失败返回什么？建议先检查什么？"), ctx).block();
            showReply(reply);
        }
        check("输入保留必需资料", text(recording.latest()).contains("登录失败返回401"));
        check("输入带入次要资料", text(recording.latest()).contains("项目首页采用蓝色背景"));
        conclusion("先舍弃低优先级可选资料；必需资料超预算应失败。版本不证明资料正确，选择也不代替权限检查。");
    }

    // ==================== 场景05：菜单5对应这里 ====================
    private void scenario05SourceFailure() {
        int timeoutMillis = 100;
        String successData = "审批已经通过";

        // 步骤1：默认自动对比五种来源行为，不需要再选分支。
        step(1, "依次加载成功、异常、超时、空列表、Mono.empty五种来源。", "超时=" + timeoutMillis + "ms；关键来源失败先停止。");
        List<Mono<List<String>>> sources = List.of(Mono.just(List.of(successData)),
                Mono.error(new IllegalStateException("模拟业务服务异常")),
                Mono.delay(Duration.ofMillis(timeoutMillis + 100L)).map(t -> List.of(successData)),
                Mono.just(List.of()), Mono.empty());
        String[] labels = {"成功", "异常", "超时", "空列表", "Mono.empty"};
        for (int i = 0; i < sources.size(); i++) {
            try {
                List<String> data = sources.get(i).timeout(Duration.ofMillis(timeoutMillis))
                        .switchIfEmpty(Mono.error(new IllegalStateException("Mono.empty是失败；没有资料应返回空列表"))).block();
                System.out.println("[来源=" + labels[i] + "] 加载成功，资料=" + data);
            } catch (Exception e) {
                System.out.println("[来源=" + labels[i] + "] 停止：" + e.getMessage() + "；此分支未调用模型。");
            }
        }

        // 步骤2：可选来源OMIT，不拿旧值冒充新数据。
        step(2, "对可选来源改用OMIT：失败时省略资料。", "关键代码=onErrorResume(...)返回空列表");
        List<String> loaded = sources.get(1).onErrorResume(e -> {
            System.out.println("[本地 OMIT] " + e.getMessage() + "；这次不提供审批状态。");
            return Mono.just(List.of());
        }).block();
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> String.join("\n", loaded))).build()) {
            Msg reply = agent.call(new UserMessage("审批状态是什么？没有资料请明确说不知道。"), ctx).block();
            showReply(reply);
        }
        check("省略后输入仍包含通过状态", text(recording.latest()).contains(successData));
        conclusion("空列表是成功但没资料；Mono.empty是加载未给结果。关键来源失败停止，可选来源可省略；这是Reactor兼容演示。");
    }

    // ==================== 场景06：菜单6对应这里 ====================
    private void scenario06TasksAndVerification() {
        String goal = "修复登录问题";
        String requirement = "必须新增会话过期测试";
        String verification = "失败：会话过期测试仍失败";
        String plan = "定位原因 -> 修改代码 -> 新增测试 -> 验证";

        // 步骤1：故意把待办标为完成，但独立的验证结果失败。
        step(1, "保存待办与计划，准备独立验证结果。", "待办=COMPLETED；实际验证=" + verification);
        Task task = Task.builder().id("task-1").subject("补充登录测试").state(Task.State.COMPLETED)
                .description("实验数据：完成标记不等于验证成功").build();
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).tasksContext(new TaskContextState(List.of(task))).build());
        write(workspace.resolve("PLAN.md"), plan);
        String progress = "目标：" + goal + "\n要求：" + requirement + "\n待办已标记完成\n实际验证：" + verification + "\n计划：" + plan;
        write(reports.resolve("business-task-state.txt"), progress);

        // 步骤2：原生Todo+兼容业务材料，询问整体是否成功。
        step(2, "启用原生待办能力，把要求和验证事实交给助手。", "关键代码=enableTaskList()；未实际执行测试工具。");
        try (HarnessAgent agent = baseBuilder().enableTaskList().middleware(temporaryContext(c -> progress)).build()) {
            Msg reply = agent.call(new UserMessage("整个任务成功了吗？区分待办完成和验证通过，不修改待办。"), ctx).block();
            showReply(reply);
        }
        check("输入包含失败的验证证据", text(recording.latest()).contains(verification));
        conclusion("待办完成是进度，测试通过是证据。要求和验证使用兼容材料，非VerificationService；计划不自动同步Todo。");
    }

    // ==================== 场景07：菜单7对应这里 ====================
    private void scenario07ToolResultOffload() {
        String log = "LOGIN_FAILED: 会话已经过期。".repeat(100);
        int threshold = 300;
        int previewChars = 80;

        // 步骤1：准备已完成工具记录，不依赖模型主动选择工具。
        step(1, "把长工具日志存成历史。", "日志=" + log.length() + "字符；阈值=" + threshold + "字符；工具记录为实验数据。");
        List<Msg> history = toolHistory(log, "", 4);
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(history).build());
        write(reports.resolve("seed-history.txt"), text(history));

        // 步骤2：直接配置原生卸载，请求准备时框架处理长结果。
        step(2, "配置ToolResultEviction，再让助手分析错误。", "关键代码=toolResultEviction(...maxResultChars...previewChars...)");
        try (HarnessAgent agent = baseBuilder().toolResultEviction(ToolResultEvictionConfig.builder()
                .maxResultChars(threshold).previewChars(previewChars).build()).build()) {
            Msg reply = agent.call(new UserMessage("根据日志说明错误原因，不需要读完整日志。"), ctx).block();
            showReply(reply);
        }

        // 步骤3：直接展示真实预览与文件，不能只凭回答声称卸载成功。
        step(3, "对照完整文件与本次工具输入。", "模型只收到预览和位置；本次没有回读全文。");
        listFiles(workspace, "large_tool_results");
        for (Msg message : recording.latest()) {
            if (message.getRole() == MsgRole.TOOL) System.out.println("[模型实际收到] " + preview(text(List.of(message)), 700));
        }
        check("实际输入含卸载文件位置", text(recording.latest()).contains("large_tool_results"));
        conclusion("全文在文件，输入留预览和路径。把repeat(100)改成repeat(1)，再选7对比短日志不卸载的效果。");
    }

    // ==================== 场景08：菜单8对应这里 ====================
    private void scenario08Budget() {
        String material = "订单编号 A-17，当前状态是待审批。".repeat(50);
        int smallLimit = 100;
        int largeLimit = 200000;
        String question = "资料中订单状态是什么？";

        // 步骤1：守卫在进入实际Model.stream之前检查。
        step(1, "故意设置很小预算，请求应在模型前停止。", "字符上限=" + smallLimit + "；字符估算不是token计数。");
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> material)).middleware(new BudgetGuard(smallLimit)).build()) {
            agent.call(new UserMessage(question), ctx).block();
        } catch (BudgetExceededException e) {
            System.out.println("[预期拦截] " + e.getMessage());
        }
        check("被拦截请求到达Model.stream", !recording.latest().isEmpty());

        // 步骤2：上限变大，材料不变，再请求一次。
        step(2, "增大预算，重新发送相同资料和问题。", "字符上限=" + largeLimit);
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> material)).middleware(new BudgetGuard(largeLimit)).build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        check("放行后请求到达Model.stream", !recording.latest().isEmpty());
        conclusion("预算包括消息与工具定义。maxContextTokens仅约束工作区材料；这里是应用字符守卫，非新版ContextPolicy。");
    }

    // ==================== 场景09：菜单9对应这里 ====================
    private void scenario09Compaction() {
        String fact = "项目代号星河，负责人天宇，截止日期10月20日";
        int rounds = 5;
        int triggerMessages = 4;
        int keepMessages = 2;

        // 步骤1：自动准备5轮历史，一问一答各算一条。
        step(1, "准备旧聊天并保存到状态。", "重要事实=" + fact + "；历史=" + rounds * 2 + "条消息。");
        List<Msg> history = chatHistory(fact, rounds);
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(history).build());
        write(reports.resolve("seed-history.txt"), text(history));

        // 步骤2：keepTokens(0)让保留规则按keepMessages生效。
        step(2, "超过阈值时整理旧对话，保留最近消息。", "triggerMessages=" + triggerMessages + "；keepMessages=" + keepMessages);
        CompactionConfig config = CompactionConfig.builder().triggerMessages(triggerMessages).keepTokens(0)
                .keepMessages(keepMessages).flushBeforeCompact(false).offloadBeforeCompact(false)
                .summaryPrompt("请整理对话摘要，特别保留负责人、截止日期和下一步。\n{messages}").build();
        try (HarnessAgent agent = baseBuilder().compaction(config).build()) {
            Msg reply = agent.call(new UserMessage("负责人是谁？截止日期是什么？"), ctx).block();
            showReply(reply);
        }

        // 步骤3：在控制台直接读框架保存的摘要，不必另开文件找证据。
        step(3, "检查工作历史是否有摘要，展示正文。", "对照开头的负责人和日期，看看有没有遗漏。");
        AgentState state = currentState();
        long summaries = state.getContext().stream().filter(m -> ConversationCompactor.SUMMARY_MSG_NAME.equals(m.getName())).count();
        System.out.println("[摘要消息=" + summaries + "] 原历史=" + history.size() + "条；现历史=" + state.getContext().size() + "条（含本次问答）。");
        state.getContext().stream().filter(m -> ConversationCompactor.SUMMARY_MSG_NAME.equals(m.getName()))
                .forEach(m -> System.out.println("[摘要正文]\n" + m.getTextContent()));
        check("旧历史生成了摘要", summaries > 0);
        conclusion("摘要像会议纪要，可能漏细节。triggerMessages改成50再选9，可看到未达阈值不压缩；离线摘要不验证语义。");
    }

    // ==================== 场景10：菜单10对应这里 ====================
    private void scenario10PruneResults() {
        String oldLog = "ERROR_LOGIN: 会话过期；详细日志。".repeat(100);
        int previewChars = 80;

        // 步骤1：旧日志很长，最近结果另行保护。
        step(1, "准备旧长日志和最近工具结果。", "旧日志=" + oldLog.length() + "字符；历史工具为实验数据，不执行工具。");
        List<Msg> history = toolHistory(oldLog, "", 4);
        history.add(Msg.builder().role(MsgRole.ASSISTANT).content(ToolUseBlock.builder().id("recent-tool")
                .name("demo_recent_result").input(Map.of()).build()).build());
        history.add(Msg.builder().role(MsgRole.TOOL).content(ToolResultBlock.builder().id("recent-tool")
                .name("demo_recent_result").output(TextBlock.builder().text("最近结果：优先保护，不裁剪。").build()).build()).build());
        write(reports.resolve("before-compaction.txt"), text(history));

        // 步骤2：原生裁剪器在生成摘要前先缩短旧结果。
        step(2, "调用原生ConversationCompactor，裁剪旧日志再摘要。", "关键代码=prune(...maxOutputChars(" + previewChars + "))");
        CompactionConfig config = CompactionConfig.builder().triggerMessages(2).keepTokens(0).keepMessages(2)
                .flushBeforeCompact(false).offloadBeforeCompact(false)
                .prune(CompactionConfig.PruneConfig.builder().protectTokens(1).minimumTokens(1).maxOutputChars(previewChars).build()).build();
        List<Msg> result = new ConversationCompactor(modelForScene(), null)
                .compactIfNeeded(ctx, history, config, scene, ctx.getSessionId()).block().orElse(history);
        write(reports.resolve("after-prune-summary.txt"), text(result));

        // 步骤3：chars pruned来自框架的真实转换。
        step(3, "检查摘要输入的裁剪标记和压缩后保留的近期结果。", "最近结果留在近期历史，不一定进入旧历史的摘要请求。");
        String input = text(recording.latest());
        check("实际摘要输入有chars pruned标记", input.contains("chars pruned"));
        check("压缩后的近期历史保留最近结果", text(result).contains("最近结果：优先保护"));
        int marker = input.indexOf("chars pruned");
        if (marker >= 0) System.out.println("[裁剪片段] " + input.substring(Math.max(0, marker - 100), Math.min(input.length(), marker + 100)));
        conclusion("裁剪只保留旧结果一部分；场景7卸载还保留全文文件。两者不是同一机制。");
    }

    // ==================== 场景11：菜单11对应这里 ====================
    private void scenario11TruncateArguments() {
        String oldParameter = "这里是写入文件的很长正文。".repeat(100);
        int maxArgLength = 100;

        // 步骤1：准备已发生的write_file参数，本次不执行该工具。
        step(1, "保存旧工具参数原文。", "原文=" + oldParameter.length() + "字符；截断阈值=" + maxArgLength);
        List<Msg> history = toolHistory("写入成功", oldParameter, 4);
        write(reports.resolve("original-tool-history.txt"), text(history));

        // 步骤2：整体保留6条，让截断后的旧调用仍可见。
        step(2, "配置原生truncateArgs并压缩历史。", "旧参数超过阈值后留前20字符和标记，不是保留100字符。");
        CompactionConfig config = CompactionConfig.builder().triggerMessages(2).keepTokens(0).keepMessages(6)
                .flushBeforeCompact(false).offloadBeforeCompact(false)
                .truncateArgs(CompactionConfig.TruncateArgsConfig.builder().triggerMessages(2).keepTokens(0)
                        .keepMessages(2).maxArgLength(maxArgLength).truncationText("…[旧参数已截断]").build()).build();
        List<Msg> result = new ConversationCompactor(modelForScene(), null)
                .compactIfNeeded(ctx, history, config, scene, ctx.getSessionId()).block().orElse(history);
        write(reports.resolve("truncated-tool-history.txt"), text(result));

        // 步骤3：直接展示处理后的参数。
        step(3, "读取工具调用参数，检查截断标记。", "原始档案仍在original-tool-history.txt中。");
        for (Msg message : result) for (ContentBlock block : message.getContent()) {
            if (block instanceof ToolUseBlock tool) System.out.println("[处理后的参数] " + tool.getInput());
        }
        check("旧参数出现截断标记", text(result).contains("[旧参数已截断]"));
        conclusion("缩短旧历史参数，不改变已执行工具的行为；本场景不执行写文件工具。");
    }

    // ==================== 场景12：菜单12对应这里 ====================
    private void scenario12OverflowRecovery() {
        String fact = "交付时间周五，负责人小明";
        List<Msg> history = chatHistory(fact, 6);

        // 步骤1：本地模拟超限，不发送巨量付费请求。
        step(1, "准备长历史，注入模拟context_length_exceeded。", "历史=" + history.size() + "条；尚未执行业务工具。");
        write(reports.resolve("before-compaction.txt"), text(history));
        // 步骤2：只捕获这类错误，只进行一次压缩恢复。
        step(2, "捕获模拟溢出，原生压缩旧历史。", "关键代码=onErrorResume(DemoOverflowException.class, ...)");
        List<Msg> reduced = Mono.<List<Msg>>error(new DemoOverflowException())
                .onErrorResume(DemoOverflowException.class, error -> {
                    System.out.println("[捕获模拟溢出] 整理旧历史；仅允许一次恢复，不重放工具。");
                    return new ConversationCompactor(modelForScene(), null)
                            .compactIfNeeded(ctx, history, compactionConfig(2, 2), scene, ctx.getSessionId())
                            .map(result -> result.orElse(history));
                }).block();
        write(reports.resolve("after-compaction.txt"), text(reduced));
        System.out.println("[压缩前后] " + history.size() + "条 → " + reduced.size() + "条");

        // 步骤3：保存缩短后的历史，再发最新问题一次。
        step(3, "恢复压缩后的状态，再问最新问题。", "没有重演工具，没有无限重试。");
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(reduced).build());
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage("交付时间和负责人是什么？"), ctx).block();
            showReply(reply);
        }
        conclusion("先缩短历史再恢复一次；这是应用模拟兜底，非当前SDK原生自动恢复。");
    }

    // ==================== 场景13：菜单13对应这里 ====================
    private void scenario13RefreshTiming() {
        String before = "每次回答以【版本A】开头";
        String after = "每次回答以【版本B】开头";
        String question = "用一句话介绍自己。";

        // 步骤1：写版本A规则并请求。
        step(1, "写入AGENTS.md版本A并请求。", "规则=" + before);
        write(workspace.resolve("AGENTS.md"), before);
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        boolean sawA = text(recording.latest()).contains(before);

        // 步骤2：改同一文件为B，新实例使用相同会话。
        step(2, "改为版本B，再问相同问题。", "规则=" + after);
        write(workspace.resolve("AGENTS.md"), after);
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
        }
        boolean sawB = text(recording.latest()).contains(after);

        // 步骤3：以真实输入为证据，不只看模型自称用了什么规则。
        step(3, "分别检查两次输入。", "这里只验证两次调用之间文件更新生效。");
        check("第一次包含版本A", sawA);
        check("第二次包含版本B", sawB);
        conclusion("下一次调用重新加载规则文件。动态来源刷新看场景3；这里不验证一次调用内的文件更新。");
    }

    // ==================== 场景14：菜单14对应这里 ====================
    private void scenario14MemoryAndHistory() {
        String fact = "我偏好中文回答，项目使用JDK17";

        // 步骤1：应用备份，不冒充SDK Session Log。
        step(1, "保存完整聊天原文。", "文件=full-history.txt；适合精确回查。");
        List<Msg> raw = chatHistory(fact, 4);
        write(reports.resolve("full-history.txt"), text(raw));

        // 步骤2：用原生压缩器生成当前工作摘要。
        step(2, "把旧对话整理成当前工作摘要。", "文件=working-summary.txt；摘要可能遗漏细节。");
        List<Msg> reduced = new ConversationCompactor(modelForScene(), null)
                .compactIfNeeded(ctx, raw, compactionConfig(2, 2), scene, ctx.getSessionId()).block().orElse(raw);
        write(reports.resolve("working-summary.txt"), text(reduced));

        // 步骤3：明确保存用户确认的事实，不启用自动模型记忆提取。
        step(3, "把已确认的偏好写到MEMORY.md，然后请求。", "长期事实=" + fact + "；这是应用保存，不是模型自动提炼。");
        Path memory = workspace.resolve(ctx.getUserId()).resolve("MEMORY.md");
        write(memory, "# 用户明确确认的长期事实\n" + fact);
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(reduced).build());
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage("我有什么偏好？现在应该如何继续？"), ctx).block();
            showReply(reply);
        }
        System.out.println("[完整档案] " + reports.resolve("full-history.txt"));
        System.out.println("[当前摘要] " + reports.resolve("working-summary.txt"));
        System.out.println("[长期事实] " + memory);
        conclusion("档案用于回查，摘要服务当前工作，长期事实服务后续会话；本场景未运行自动flush/consolidation。");
    }

    // ==================== 场景15：菜单15对应这里 ====================
    private void scenario15Diagnostics() {
        String information = "接口文档guide-v3：过期会话返回401";
        String question = "过期会话应该返回什么？";

        // 步骤1：RecordingModel在实际Model.stream调用处保存输入。
        step(1, "准备资料，打开模型输入记录和流式事件观察。", "资料=" + information);
        List<String> eventTypes = new ArrayList<>();

        // 步骤2：直接消费原生streamEvents，文本事件到来时打印文本。
        step(2, "订阅streamEvents，逐个收集真实事件。", "TextBlockDeltaEvent就是逐步输出的文字。");
        try (HarnessAgent agent = baseBuilder().middleware(temporaryContext(c -> information)).build()) {
            agent.streamEvents(new UserMessage(question), ctx).doOnNext(event -> {
                eventTypes.add(event.getType().toString());
                if (event instanceof TextBlockDeltaEvent delta) System.out.print(delta.getDelta());
            }).blockLast();
            System.out.println();
        }
        write(reports.resolve("observed-events.txt"), String.join("\n", eventTypes));
        currentState();

        // 步骤3：控制台直接展示材料送达结果与事件类型。
        step(3, "核对模型输入与实际事件。", "事件类型=" + new LinkedHashSet<>(eventTypes));
        check("模型实际收到资料", text(recording.latest()).contains(information));
        conclusion("回答不对先查输入。本版记录提示正文，非新版只含元数据的ContextManifest；passed也不证明回答正确。");
    }

    // ==================== 场景16：一次请求里的真实工具循环 ====================
    private void scenario16ToolLoop() {
        String question = "请调用demo_fix_and_test_login修复并验证示例登录逻辑，再根据最新状态说明结果。只调用一次，不操作其他工具。";
        AtomicReference<String> status = new AtomicReference<>("失败：示例逻辑对过期会话错误地返回200，等待修复并复验");
        AtomicInteger sourceReads = new AtomicInteger();
        LoginDemoTools tools = new LoginDemoTools(status);

        // 步骤1：真正注册一个Java工具。业务值只能由工具改变，来源回调只是读取。
        step(1, "注册修复并验证工具，准备每轮查询的动态来源。", "初始状态=" + status.get());
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        var source = temporaryContext(c -> {
            String value = "本轮构建状态=" + status.get();
            System.out.println("[本轮来源读取" + sourceReads.incrementAndGet() + "] " + value);
            return value;
        });

        // 离线只模拟模型的两次决策，工具执行、状态刷新和结果卸载仍由真实Harness完成。
        Model selectedModel = baseModel();
        if (offline) selectedModel = new Model() {
            private int turns;
            @Override public String getModelName() { return "offline-tool-loop-model"; }
            @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> schemas, GenerateOptions options) {
                ContentBlock decision = turns++ == 0
                        ? ToolUseBlock.builder().id("login-test-16").name("demo_fix_and_test_login").input(Map.of()).build()
                        : TextBlock.builder().text("[离线模拟决策] 工具已完成，请看Java验证结果与本轮状态检查。").build();
                return Flux.just(ChatResponse.builder().id(UUID.randomUUID().toString()).content(List.of(decision))
                        .finishReason(decision instanceof ToolUseBlock ? "tool_calls" : "stop").build());
            }
        };
        recording = new RecordingModel(selectedModel, reports);

        // 步骤2：这里只有一次streamEvents调用，Harness内部自动跑多轮模型-工具循环。
        step(2, "提交一次用户请求：模型选工具 → Java工具执行 → 下一轮重新查询状态。", "配置原生长结果卸载，阈值300字符；无需再次提交用户问题。");
        List<String> events = new ArrayList<>();
        try (HarnessAgent agent = baseBuilder().toolkit(toolkit).maxIters(6)
                .middleware(source).toolResultEviction(ToolResultEvictionConfig.builder().maxResultChars(300).previewChars(80).build()).build()) {
            agent.streamEvents(new UserMessage(question), ctx).doOnNext(event -> {
                events.add(event.getType().toString());
                if (event instanceof ToolCallStartEvent start) System.out.println("[工具开始] " + start.getToolCallName());
                if (event instanceof ToolCallEndEvent end) System.out.println("[工具结束] " + end.getToolCallName());
                if (event instanceof TextBlockDeltaEvent delta) System.out.print(delta.getDelta());
            }).blockLast();
            System.out.println();
        }
        write(reports.resolve("tool-loop-events.txt"), String.join("\n", events));
        AgentState state = currentState();

        // 步骤3：证据来自真实请求、工具计数和存储；不是应用手动调用工具伪装ReAct。
        step(3, "对照第一轮失败状态、后续通过状态和真实工具记录。", "模型请求=" + recording.requests().size() + "次；Java工具执行=" + tools.executions.get() + "次。");
        check("一次请求内发生了多轮模型调用", recording.requests().size() >= 2);
        check("第一轮动态资料是失败状态", text(recording.requests().get(0)).contains("本轮构建状态=失败"));
        check("工具之后动态资料变成通过", recording.requests().stream().skip(1).anyMatch(m -> text(m).contains("本轮构建状态=通过")));
        check("修复并复验工具恰好执行一次", tools.executions.get() == 1);
        check("实际输入含长日志卸载位置", text(recording.latest()).contains("large_tool_results"));
        check("会话历史含真实工具调用和回执", text(state.getContext()).contains("demo_fix_and_test_login"));
        check("动态来源被当作历史消息持久化", text(state.getContext()).contains("demo-temporary-context"));
        listFiles(workspace, "large_tool_results");
        conclusion("同一次请求里，工具完成后再准备下一轮输入，业务来源会重新读取。Java工具验证的是本地示例逻辑，非本项目的Maven测试；动态来源仍是2.0.4兼容中间件。");
    }

    // ==================== 场景17：原生待办更新、压缩恢复与计划状态 ====================
    private void scenario17TaskRecovery() {
        String question = "只用todo_write维护三项待办：定位原因、修复示例登录、执行最终验证。先记录定位进行中，再更新为前两项完成、最终验证进行中。最后简短回复，不使用其他工具。";
        String planText = "# 登录任务计划\n1. 定位原因\n2. 修复示例登录\n3. 执行最终验证，未验证不能认定成功。\n";

        // 离线模型决定调用原生todo_write；框架注入AgentState并实际持久化任务列表。
        Model selectedModel = baseModel();
        if (offline) selectedModel = new Model() {
            private int turns;
            @Override public String getModelName() { return "offline-todo-model"; }
            @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> schemas, GenerateOptions options) {
                ContentBlock decision;
                int turn = turns++;
                if (turn < 2) {
                    List<Map<String, String>> todos = List.of(
                            Map.of("content", "定位原因", "status", turn == 0 ? "in_progress" : "completed"),
                            Map.of("content", "修复示例登录", "status", turn == 0 ? "pending" : "completed"),
                            Map.of("content", "执行最终验证", "status", turn == 0 ? "pending" : "in_progress"));
                    Map<String, Object> args = Map.of("todos", todos);
                    // 与真实模型一致：content是工具参数JSON，input是已经解析的参数映射。
                    decision = ToolUseBlock.builder().id("todo-17-" + turn).name("todo_write")
                            .input(args).content(JsonUtils.getJsonCodec().toJson(args)).build();
                } else decision = TextBlock.builder().text("[离线模拟决策] 下一步是执行最终验证；以持久任务状态和本轮提醒为准。").build();
                return Flux.just(ChatResponse.builder().id(UUID.randomUUID().toString()).content(List.of(decision))
                        .finishReason(decision instanceof ToolUseBlock ? "tool_calls" : "stop").build());
            }
        };
        recording = new RecordingModel(selectedModel, reports);

        // 步骤1：真实Agent循环调用todo_write，不再预先构造COMPLETED任务代替工具操作。
        step(1, "让模型通过原生todo_write创建并更新待办。", "最后一项故意保持进行中，方便观察压缩后还能否继续。");
        try (HarnessAgent agent = baseBuilder().enableTaskList().enablePlanMode().maxIters(6).build()) {
            Msg reply = agent.call(new UserMessage(question), ctx).block();
            showReply(reply);
            step(2, "应用通过原生API进入计划模式，并保存完整计划。", "关键代码=enterPlanMode(ctx)，没有调用需要交互审批的plan_exit工具。");
            agent.enterPlanMode(ctx);
            AgentState planning = currentState();
            String planPath = planning.getPlanModeContext().getCurrentPlanFile();
            agent.getWorkspaceManager().writeUtf8WorkspaceRelative(ctx, planPath, planText);
            System.out.println("[计划路径] " + planPath);
        }
        AgentState before = currentState();
        check("原生Todo实际保存了三项待办", before.getTasksContext().getTasks().size() == 3);
        write(reports.resolve("before-task-state.json"), before.toJson());
        before.getTasksContext().getTasks().forEach(task -> System.out.println("[待办] " + task.getSubject() + "：" + task.getState()));

        // 步骤3：在真实工具历史后加若干中性进度消息，保证旧回执位于摘要区。
        // 任务/计划独立保存，不能只保存context而把结构化状态弄丢。
        step(3, "准备长历史；模型压缩历史时保留结构化任务和计划状态。", "追加消息是模拟长程工作进度，原todo_write回执来自真实调用。");
        List<Msg> longHistory = new ArrayList<>(before.getContext());
        longHistory.addAll(chatHistory("补充进度：正在检查相关模块", 5));
        stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(longHistory)
                .tasksContext(before.getTasksContext()).planModeContext(before.getPlanModeContext())
                .permissionContext(before.getPermissionContext()).build());
        write(reports.resolve("before-compaction.txt"), text(longHistory));
        CompactionConfig config = CompactionConfig.builder().triggerMessages(4).keepTokens(0).keepMessages(2)
                .flushBeforeCompact(false).offloadBeforeCompact(false)
                .model(baseModel()).summaryPrompt("请整理对话摘要，只总结进度，不逐字复制工具回执；任务状态另有结构化记录。\n{messages}").build();

        // 步骤4：新实例恢复相同身份，原生Todo提醒重新放入模型输入。
        step(4, "创建实例B恢复同一会话，再问下一步。", "旧工具回执将被摘要，但当前待办与计划模式应仍可见。");
        try (HarnessAgent agent = baseBuilder().enableTaskList().enablePlanMode().compaction(config).maxIters(4).build()) {
            check("实例B恢复了计划模式", agent.isPlanModeActive(ctx));
            Msg reply = agent.call(new UserMessage("目前还有什么未完成？只报告下一步，不调用工具，不退出计划模式。"), ctx).block();
            showReply(reply);
            AgentState after = currentState();
            write(reports.resolve("after-task-state.json"), after.toJson());
            check("历史实际生成了摘要", after.getContext().stream().anyMatch(m -> ConversationCompactor.SUMMARY_MSG_NAME.equals(m.getName())));
            check("压缩前后结构化Todo完全一致", after.getTasksContext().equals(before.getTasksContext()));
            check("原生Todo提醒含未完成项", text(recording.latest()).contains("[~] 执行最终验证"));
            check("任务提醒被反复存成历史", after.getContext().stream().anyMatch(m -> m.getMetadata() != null
                    && "todo_state".equals(m.getMetadata().get(Msg.METADATA_REMINDER_KIND))));
            check("实际历史仍含旧todo_write回执", after.getContext().stream().flatMap(m -> m.getContent().stream())
                    .anyMatch(block -> block instanceof ToolResultBlock result && "todo_write".equals(result.getName())));
            agent.exitPlanMode(ctx); // 应用明确切回执行模式；不使用模型plan_exit的审批流程。
            check("应用切回执行模式后计划模式仍开启", agent.isPlanModeActive(ctx));
        }
        conclusion("压缩改变工作历史，不把Todo和计划状态一起摘要掉。这里真实更新并恢复Todo、进入/退出计划模式；进度为教学数据，最终验证尚未执行。");
    }

    // ==================== 场景18：原生记忆提取、整理、新会话与原生档案 ====================
    private void scenario18MemoryLifecycle() {
        String fact = "我偏好中文回答，团队项目使用JDK17";
        String auditCode = "AUDIT-18-" + UUID.randomUUID().toString().substring(0, 8);
        RuntimeContext originalCtx = ctx;
        // 每次实验独立记忆目录，避免前次合并的水位让本次跳过。
        workspace = workspace.resolve(originalCtx.getSessionId());

        // 步骤1：真实调用产生消息，使用当前版本SDK的SessionTranscriptWriter归档。
        step(1, "先告诉助手长期偏好和一条仅用于回查的核对码。", "偏好=" + fact + "；核对码=" + auditCode);
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage("长期偏好：" + fact + "\n历史核对码：" + auditCode + "（仅本次排查，别当长期偏好）。简单确认收到即可。"), ctx).block();
            showReply(reply);
            AgentState original = currentState();
            write(reports.resolve("original-state.json"), original.toJson());
            var manager = agent.getWorkspaceManager();
            new SessionTranscriptWriter(manager).appendMessages(ctx, original.getContext(), scene, ctx.getSessionId());
            Path archive = manager.resolveSessionContextFile(ctx, scene, ctx.getSessionId());
            check("原生JSONL档案含核对码", readUtf8(archive).contains(auditCode));

            // 步骤2：调用原生提取组件，模型输出由框架追加到每日记忆文件。
            step(2, "原生MemoryFlushManager调用模型提取长期事实。", "输出写入memory每日文件；不是应用把fact直接写入MEMORY.md。");
            new MemoryFlushManager(manager, modelForScene(), MemoryFlushManager.DEFAULT_FLUSH_PROMPT
                    + "\n只保留长期偏好，不保留历史核对码；用中文。").flushMemories(ctx, original.getContext()).block();
            listFiles(workspace, "memory");
            check("提取后有每日记忆文件", !manager.listMemoryFilePaths(ctx).isEmpty());

            // 步骤3：显式等待原生整理完成，让结果可观察，避免依赖后台定时任务。
            step(3, "原生MemoryConsolidator读取每日文件，调用模型整理MEMORY.md。", "关键代码=consolidate(ctx).block()；额外模型请求用于记忆处理。");
            new MemoryConsolidator(manager, modelForScene()).consolidate(ctx).block();
            String curated = manager.readMemoryMd(ctx);
            System.out.println("[框架整理的长期记忆]\n" + curated);
            check("框架已生成非空MEMORY.md", !curated.isBlank());

            // 步骤4：压缩工作历史，并核对原生档案仍然保留原话。
            step(4, "压缩工作历史，再从原生会话档案精确回查。", "核对码要求不进摘要，但应留在已提交的原始消息记录中。");
            List<Msg> longHistory = new ArrayList<>(original.getContext());
            longHistory.addAll(chatHistory("后续进度：继续整理分享材料", 5));
            CompactionConfig config = CompactionConfig.builder().triggerMessages(2).keepTokens(0).keepMessages(2)
                    .flushBeforeCompact(false).offloadBeforeCompact(false)
                    .summaryPrompt("请整理对话摘要，只保留工作重点，不保留历史核对码。\n{messages}").build();
            List<Msg> reduced = new ConversationCompactor(modelForScene(), null)
                    .compactIfNeeded(ctx, longHistory, config, scene, ctx.getSessionId()).block().orElse(longHistory);
            stateStore.save(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.builder()
                    .userId(ctx.getUserId()).sessionId(ctx.getSessionId()).context(reduced).build());
            write(reports.resolve("after-compaction.txt"), text(reduced));
            check("压缩工作历史仍含核对码", text(reduced).contains(auditCode));
            String archived = readUtf8(archive);
            check("压缩后原生档案仍含核对码", archived.contains(auditCode));
            archived.lines().filter(line -> line.contains(auditCode)).forEach(line -> System.out.println("[原生档案命中] " + preview(line, 450)));
            System.out.println("[原生档案路径] " + archive);
        }

        // 步骤5：同一用户的新会话，空聊天历史，但工作区长期记忆可加载。
        step(5, "换一个新session，恢复长期偏好；不加载原会话聊天。", "用户仍是learner，工作区相同，sessionId改变。");
        ctx = RuntimeContext.builder().userId(originalCtx.getUserId()).sessionId("memory-new-" + UUID.randomUUID()).build();
        try (HarnessAgent agent = baseBuilder().build()) {
            Msg reply = agent.call(new UserMessage("我偏好哪种回答语言？团队使用哪个JDK？根据已有资料回答，不调用工具。"), ctx).block();
            showReply(reply);
        }
        check("新的session与原会话不同", !ctx.getSessionId().equals(originalCtx.getSessionId()));
        check("新会话输入包含长期偏好", text(recording.latest()).contains("中文") && text(recording.latest()).contains("JDK17"));
        check("新会话输入混入原核对码", text(recording.latest()).contains(auditCode));
        conclusion("本场景真实调用SDK记忆提取与整理组件，并通过2.0.4原生JSONL档案回查文字消息；不是新版Session Log API。离线仅模拟模型生成的记忆，真实模式需检查内容是否准确。");
    }

    // ==================== 以下是通用基础设施，首次读场景无需展开 ====================

    /** 准备隔离的身份和目录，不执行场景业务。 */
    private void prepare(int choice) {
        sceneNumber = choice;
        scene = "scene-" + String.format("%02d", choice);
        ctx = RuntimeContext.builder().userId("learner").sessionId("experiment-" + UUID.randomUUID()).build();
        workspace = storage.workspace().resolve(scene);
        reports = storage.root().resolve("reports").resolve(scene).resolve(ctx.getSessionId());
        stateStore = new JsonFileAgentStateStore(storage.state(scene));
        recording = null;
        observations.clear();
        System.out.println("\n========== 场景" + String.format("%02d", choice) + "：" + SCENES[choice - 1] + " ==========");
        String method = METHODS[choice - 1];
        System.out.println("[代码入口] " + method + "()；Ctrl+F搜索该方法，或在菜单switch上Ctrl+B跳转。");
        // 普通文件位置提示，不伪造异常堆栈；Ctrl+F/Ctrl+B始终可以定位方法。
        Path source = Path.of("src/main/java/com/example/agentscope/harness/context/ContextManagementDemo.java");
        if (Files.isRegularFile(source)) {
            try {
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains("void " + method + "()")) {
                    System.out.println("[源码位置] " + source.toAbsolutePath().normalize() + ":" + (i + 1));
                    break;
                }
            } catch (IOException ignored) { /* 导航提示不影响实验。 */ }
        }
    }

    /** 只打印日志，不做业务、不暂停；编号与方法内步骤注释一致。 */
    private void step(int number, String action, String detail) {
        String line = "[场景" + String.format("%02d", sceneNumber) + "/步骤" + number + "] " + action;
        System.out.println("\n" + line + "\n  " + detail);
        observations.add(line + " " + detail);
    }

    private void check(String label, boolean result) {
        String line = "[实际效果] " + label + "=" + result + (result ? "（是）" : "（否）");
        System.out.println(line);
        observations.add(line);
    }

    private void conclusion(String content) {
        System.out.println("[本场景结论] " + content);
        observations.add("结论：" + content);
    }

    private void showReply(Msg reply) {
        if (reply == null) throw new IllegalStateException("模型未返回消息");
        System.out.println("[助手回复] " + reply.getTextContent());
        currentState();
    }

    private AgentState currentState() {
        AgentState state = stateStore.get(ctx.getUserId(), ctx.getSessionId(), "agent_state", AgentState.class).orElseThrow();
        write(reports.resolve("agent-state.json"), state.toJson());
        return state;
    }

    private Model baseModel() {
        if (model != null) return model;
        if (offline) return model = offlineModel();
        Properties props = config();
        String key = setting(props, "DEEPSEEK_API_KEY", "llm.api-key", "");
        if (key.isBlank()) throw new IllegalStateException("缺少DEEPSEEK_API_KEY / llm.api-key");
        model = OpenAIChatModel.builder().apiKey(key)
                .baseUrl(setting(props, "DEEPSEEK_BASE_URL", "llm.base-url", "https://api.deepseek.com"))
                .modelName(setting(props, "DEEPSEEK_MODEL", "llm.model", "deepseek-chat"))
                .stream(Boolean.parseBoolean(props.getProperty("llm.stream", "true"))).build();
        return model;
    }

    private RecordingModel modelForScene() {
        if (recording == null) recording = new RecordingModel(baseModel(), reports);
        return recording;
    }

    /** 共用模型、目录、无关功能默认值；每个场景在此基础上配置自己的能力。 */
    private HarnessAgent.Builder baseBuilder() {
        return HarnessAgent.builder().name(scene).agentId(scene).model(modelForScene()).workspace(workspace).stateStore(stateStore)
                .sysPrompt("你是上下文管理教学助手，中文简短回答。只按实验问题回答，除非明确要求，不修改文件。")
                .disableMemoryHooks().disableSubagents().disableShellTool().disableDynamicSkills().disableDynamicSubagents().disableToolsConfig()
                .compaction(compactionConfig(Integer.MAX_VALUE, 2));
    }

    private static CompactionConfig compactionConfig(int trigger, int keep) {
        return CompactionConfig.builder().triggerMessages(trigger).triggerTokens(Integer.MAX_VALUE).keepTokens(0)
                .keepMessages(keep).flushBeforeCompact(false).offloadBeforeCompact(false).build();
    }

    /** 批量造历史，仅是实验数据；核心框架转换在各场景内直接调用。 */
    private static List<Msg> chatHistory(String fact, int rounds) {
        List<Msg> messages = new ArrayList<>();
        for (int i = 1; i <= rounds; i++) {
            messages.add(new UserMessage("第" + i + "轮：" + fact + "。任务进度：第" + i + "步完成，继续后续工作。"));
            messages.add(Msg.builder().role(MsgRole.ASSISTANT).textContent("收到，继续跟进。第" + i + "步已记录。").build());
        }
        return messages;
    }

    private static List<Msg> toolHistory(String output, String parameter, int tail) {
        List<Msg> history = new ArrayList<>();
        history.add(new UserMessage("以下是此前已经执行过的演示工具记录，只分析，不要再次执行。"));
        String tool = parameter.isBlank() ? "demo_test_log" : "write_file";
        history.add(Msg.builder().role(MsgRole.ASSISTANT).content(ToolUseBlock.builder().id("demo-tool-1")
                .name(tool).input(Map.of("content", parameter)).build()).build());
        history.add(Msg.builder().role(MsgRole.TOOL).content(ToolResultBlock.builder().id("demo-tool-1")
                .name(tool).output(TextBlock.builder().text(output).build()).build()).build());
        for (int i = 0; i < tail; i++) history.add(new UserMessage("较新的工作进度：第" + (i + 1) + "步已完成。"));
        return history;
    }

    private void listFiles(Path root, String part) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(p -> p.toString().contains(part)).forEach(p -> System.out.println("[完整文件] " + p));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String readUtf8(Path file) {
        try { return Files.readString(file, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** 场景16的真实Java工具；只修复并验证内存里的示例逻辑，不修改项目代码。 */
    public static final class LoginDemoTools {
        private final AtomicReference<String> status;
        private final AtomicInteger executions = new AtomicInteger();
        private boolean buggy = true;

        public LoginDemoTools(AtomicReference<String> status) { this.status = status; }

        @Tool(name = "demo_fix_and_test_login", description = "修复内存中的示例登录逻辑并执行两项本地验证，更新构建状态，返回测试日志。只需调用一次。",
                readOnly = false, concurrencySafe = false)
        public String fixAndTestLogin() {
            executions.incrementAndGet();
            int before = loginStatus("expired");
            buggy = false;
            int expired = loginStatus("expired");
            int active = loginStatus("active");
            boolean passed = expired == 401 && active == 200;
            status.set(passed ? "通过：过期会话返回401、有效会话返回200，两项本地验证通过"
                    : "失败：本地验证结果与预期不符");
            System.out.println("[Java工具实际执行] 过期会话返回值：" + before + " → " + expired
                    + "；有效会话=" + active + "；结果=" + status.get());
            return "真实Java示例验证：" + status.get() + "\n"
                    + (" expired=" + expired + ", active=" + active + ", assertion=" + passed + "\n").repeat(100);
        }

        private int loginStatus(String session) { return !buggy && "expired".equals(session) ? 401 : 200; }
    }

    /** 场景08的应用字符守卫，非新版ContextPolicy。 */
    static final class BudgetGuard implements io.agentscope.core.middleware.MiddlewareBase {
        private final int limit;
        BudgetGuard(int limit) { this.limit = limit; }
        @Override public int order() { return 10000; }
        @Override public reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent> onModelCall(
                io.agentscope.core.agent.Agent agent, RuntimeContext ctx, io.agentscope.core.middleware.ModelCallInput input,
                java.util.function.Function<io.agentscope.core.middleware.ModelCallInput, reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent>> next) {
            int estimate = chars(input.messages()) + String.valueOf(input.tools()).length();
            System.out.println("[预算检查] 约" + estimate + "字符 / 上限" + limit + "（非供应商token数）");
            if (estimate > limit) return reactor.core.publisher.Flux.error(new BudgetExceededException(estimate, limit));
            return next.apply(input);
        }
    }

    private static final class BudgetExceededException extends RuntimeException {
        BudgetExceededException(int estimate, int limit) { super("输入超预算：" + estimate + " > " + limit); }
    }

    private static final class DemoOverflowException extends RuntimeException {
        DemoOverflowException() { super("context_length_exceeded（本地模拟）"); }
    }
}
