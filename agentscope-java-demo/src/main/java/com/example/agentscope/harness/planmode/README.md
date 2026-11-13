# 计划模式入门

参考：[Plan Mode 文档](https://java.agentscope.io/v2/zh/docs/harness/plan-mode)。使用项目现有 2.0.4。

最小任务：先写计划，再创建一份欢迎文件。

```text
应用 enterPlanMode → 模型 plan_write → 展示 PLAN.md
       ↓
模型 plan_exit → 原生 RequireUserConfirmEvent → 终端输入 y 或拒绝
       ↓
批准：通过 ConfirmResult 恢复执行 → write_file → read_file 验证
拒绝：仍在 PLAN 阶段，目标文件不存在
```

```powershell
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.planmode.PlanModeDemo'
# 只生成计划，不请求批准、不创建目标文件：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.planmode.PlanModeDemo' -DemoArgs '--plan-only'
```

先自动完成规划，然后展示实际 `plans/PLAN.md`。收到退出计划模式的原生确认事件后，
终端输入 `y` 批准，其他输入或 EOF 拒绝。拒绝结果也交回框架，代码核对计划模式及目标文件状态。
这不是普通聊天里说一句“可以了”，而是 `Msg.METADATA_CONFIRM_RESULTS` 携带原生 `ConfirmResult`。

`enablePlanMode()` 安装能力；`enterPlanMode(context)` 显式进入，保证入门演示先规划。
`plan_write` 是计划阶段允许的专用写入工具；普通 `write_file` 不允许在批准前执行。
Shell 工具关闭，没有使用全局 `PermissionMode.BYPASS`。

只需要无人值守地验证这份固定教学任务时，可以**显式选择**：

```powershell
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.planmode.PlanModeDemo' -DemoArgs '--auto-approve'
```

这个参数只给本例的 `plan_exit` 返回批准结果，不代表用户批准了真实项目部署或其他工具。
默认仍由用户看完计划后在终端决定。每轮使用独立教学目录，结果保存在
`.agentscope/harness-planmode/run-<UUID>/workspace/{plans/PLAN.md,notes/welcome.txt}`。
代码核对批准前目标文件不存在、批准后模式改变且文件内容正确。

公共配置、终端与事件展示在 `harness/DemoSupport.java`；主要流程全部在 `PlanModeDemo.main()`。
模型配置与 UTF-8/JDK17 设置见 [运行指南](../../../../../../../../RUNNING.md)。
