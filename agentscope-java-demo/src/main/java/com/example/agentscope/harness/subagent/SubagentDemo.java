package com.example.agentscope.harness.subagent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import java.util.List;
import static com.example.agentscope.harness.DemoSupport.*;

/** 一个主 Agent + 两个专用子 Agent。main 看声明，demonstrate 看委派与汇总。 */
public class SubagentDemo {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !"--auto".equals(args[0])))
            throw new IllegalArgumentException("用法：SubagentDemo [--auto]");
        var storage = storage("subagent");
        var model = model();
        RuntimeContext context = RuntimeContext.builder().sessionId("subagent-demo")
                // 入门演示同步等待结果，避免主 Agent 已退出、子任务仍在后台执行。
                .put(AgentSpawnTool.CTX_FORCE_SYNC, true)
                .put(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS, 120).build();

        try (HarnessAgent main = localBuilder("main-teacher", model, storage)
                .disableShellTool().disableDynamicSkills().disableDefaultWorkspaceSkills()
                .subagents(List.of(
                        SubagentDeclaration.builder().name("reviewer")
                                .description("Java 空指针审查专家，检查代码潜在异常。")
                                .inlineAgentsBody("你是代码审查专家。最多120个中文字说明 null 风险，给出触发条件。"
                                        + "只根据传入代码分析，不调用工具、不修改文件。")
                                .tools(List.of()).steps(3).build(),
                        SubagentDeclaration.builder().name("tutor")
                                .description("Java 入门讲解老师，用简单例子解释代码。")
                                .inlineAgentsBody("你是入门讲解老师。最多120个中文字解释 Map.get 的行为，"
                                        + "给出先检查map再检查value的安全写法。getOrDefault不能处理已有键值为null的情况。"
                                        + "只根据任务分析，不调用工具、不修改文件。")
                                .tools(List.of()).steps(3).build()))
                .build()) {
            System.out.println("老大，主 Agent 负责分工和汇总；子 Agent 各自处理一个问题。");
            System.out.println("数据目录：" + storage.root());
            demonstrate(main, context);
            if (args.length == 0) chat(main, context);
        }
    }

    private static void demonstrate(HarnessAgent main, RuntimeContext context) {
        Trace trace = ask(main, "请真正调用 agent_spawn：把下面代码的空指针风险分析交给 reviewer；"
                + "把 Map.get 的入门解释和安全写法交给 tutor。两个子任务都携带这段代码，"
                + "等待两者返回后再汇总为‘风险、原因、修改建议’。不要自己代替他们回答。\n"
                + "String name = map.get(\"name\").toString();", context);
        trace.requireTool("agent_spawn");
        for (String child : List.of("reviewer", "tutor")) {
            if (trace.childSources.stream().noneMatch(source -> source.contains(child)))
                throw new IllegalStateException("没有看到 " + child + " 的真实事件，委派演示尚未完成。");
        }
        System.out.println("[验证通过] 两个子 Agent 都产生了独立事件，主 Agent 收到结果后汇总。");
    }
}
