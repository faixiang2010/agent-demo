# 子 Agent 入门

参考：[Subagent 文档](https://java.agentscope.io/v2/zh/docs/harness/subagent)。使用项目现有 2.0.4。

场景：同一行 Java 代码，主 Agent 委派给两个专家，再汇总结果。

```text
主 Agent
├── reviewer：分析空指针风险
└── tutor：给初学者解释并提供安全写法
          ↓
主 Agent 汇总：风险、原因、修改建议
```

读代码：`main()` 中看两份 `SubagentDeclaration`；`demonstrate()` 中看任务和真实调用验证。
两个子 Agent 使用独立任务会话、自己的角色提示词；模型沿用主 Agent，没有额外 API Key。
同步委派使用原生 `agent_spawn`；不是 Java 按顺序手动调用两个模型再拼字符串。
应用通过 `CTX_FORCE_SYNC` 强制等待，避免本例退出时后台子任务尚未完成。
两个独立任务可能并行，终端分别缓存子 Agent 文本，结束时整段显示，避免字句混在一起。

```powershell
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.subagent.SubagentDemo'
# 只看自动演示：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.subagent.SubagentDemo' -DemoArgs '--auto'
```

默认演示后可以自己提问，`exit` 或 `0` 退出。
观察 `[工具 / 主 Agent] agent_spawn` 与两个 `[开始] 子 Agent ...`，最后看主 Agent 汇总。
代码检查实际工具和两个子 Agent 来源事件；只说“我请了专家”不算成功。
数据在 `.agentscope/harness-subagent/run-<UUID>/`，每轮目录独立。

一个 Agent 调用多个普通工具不等于多 Agent；本例是两个独立 Agent 在执行任务。
文档也支持工作区 `subagents/*.md`、后台任务、远程 Agent 等，本例先用代码声明和同步调用。
多 Agent 的独立上下文与工作区不等同于操作系统级隔离。
模型配置与 UTF-8/JDK17 设置沿用 [运行指南](../../../../../../../../RUNNING.md)。
