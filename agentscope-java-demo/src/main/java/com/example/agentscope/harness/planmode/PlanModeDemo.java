package com.example.agentscope.harness.planmode;

import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import static com.example.agentscope.harness.DemoSupport.*;

/** 进入只读计划阶段 -> 写真实计划文件 -> plan_exit 请求批准 -> 批准后创建教学文件。 */
public class PlanModeDemo {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        if (args.length > 1 || !Set.of("", "--plan-only", "--auto-approve").contains(mode))
            throw new IllegalArgumentException("用法：PlanModeDemo [--plan-only | --auto-approve]");
        var storage = storage("planmode");
        var context = context();
        var reader = console();
        try (HarnessAgent agent = localBuilder("planner", model(), storage)
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills().disableDefaultWorkspaceSkills()
                .enablePlanMode().build()) {
            agent.enterPlanMode(context); // enablePlanMode 只是装能力，应用显式进入让演示可预测。
            System.out.println("老大，先写计划，批准之前不创建目标文件。目录：" + storage.root());
            Trace planning = ask(agent, "请用 plan_write 写一份简单计划：创建 notes/welcome.txt，"
                    + "内容为‘老大，欢迎学习计划模式’。计划包含目标、步骤、验证。"
                    + "现在只写计划，不调用 plan_exit、不写目标文件，写完等待我确认。", context);
            planning.requireTool("plan_write");
            var plan = storage.workspace().resolve("plans/PLAN.md");
            var target = storage.workspace().resolve("notes/welcome.txt");
            if (!agent.isPlanModeActive(context) || !Files.exists(plan) || Files.exists(target))
                throw new IllegalStateException("计划阶段未达到预期：应有计划文件且没有目标文件。");
            System.out.println("\n[实际计划文件] " + plan + "\n" + Files.readString(plan));
            System.out.println("[验证通过] 仍处于 PLAN 阶段，目标文件还没有创建。");
            if ("--plan-only".equals(mode)) return;

            Trace request = ask(agent, "计划已写好，请调用 plan_exit 请求批准。批准后用 write_file"
                    + "创建 notes/welcome.txt，写入‘老大，欢迎学习计划模式’，再 read_file 验证。", context);
            request.requireTool("plan_exit");
            RequireUserConfirmEvent pending = request.pending;
            if (pending == null) throw new IllegalStateException("没有收到原生批准事件，不能视为审批流程已完成。");
            if (!agent.isPlanModeActive(context) || Files.exists(target))
                throw new IllegalStateException("批准之前应保持只读且没有目标文件。");

            boolean approve;
            if ("--auto-approve".equals(mode)) {
                approve = true; // 仅限显式选择的教学验证模式，不设置全局 BYPASS。
                System.out.println("[教学自动批准] 只批准本次 plan_exit，目标是这个独立目录内的欢迎文件。");
            } else {
                System.out.print("老大，已展示计划。批准执行吗？输入 y 批准，其他输入或 EOF 拒绝：");
                String input = reader.readLine();
                approve = input != null && "y".equalsIgnoreCase(input.trim());
            }
            var results = new ArrayList<ConfirmResult>();
            for (var call : pending.getToolCalls()) {
                results.add(new ConfirmResult(approve && "plan_exit".equals(call.getName()), call));
            }
            Msg reply = UserMessage.builder()
                    .textContent(approve ? "已批准，请执行计划。" : "已拒绝，保持计划模式，不要再请求退出或创建目标文件。")
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results)).build();
            Trace execution = stream(agent, reply, context);
            if (approve) {
                execution.requireTool("write_file");
                if (agent.isPlanModeActive(context) || !Files.exists(target)
                        || !Files.readString(target).contains("欢迎学习计划模式"))
                    throw new IllegalStateException("批准后的执行未达到预期，请检查工具输出。");
                System.out.println("[验证通过] 已进入执行阶段，目标文件：\n" + Files.readString(target));
            } else {
                if (!agent.isPlanModeActive(context) || Files.exists(target))
                    throw new IllegalStateException("拒绝后应仍为 PLAN 阶段，且目标文件不存在。");
                System.out.println("[验证通过] 已拒绝，仍是计划阶段，目标文件没有创建。");
            }
        }
    }
}
