# Harness 示例分组

每个文档主题放在自己的子包，方便后续添加演示。

所有主题的 PowerShell 命令统一使用 `scripts/run-demo.ps1` 设置 UTF-8 与 JDK17。
手动启动 Maven 和 IDEA 的编码设置见 [运行指南](../../../../../../../RUNNING.md)。

| 子包 | 主题 | 运行入口 |
|---|---|---|
| `context` | 上下文管理，数字对应方法，默认全流程自动执行 | `com.example.agentscope.harness.context.ContextManagementDemo.main` |
| `workspace` | 工作区入门：文件加载、按需读取、修改后刷新 | `com.example.agentscope.harness.workspace.WorkspaceDemo.main` |
| `session` | 聊天记录入门：自动保存、历史读取、重建与重启后恢复（2.0.4 兼容示例） | `com.example.agentscope.harness.session.SessionLogDemo.main` |
| `memory` | 长期记忆入门：保存、整理、新会话回忆与检索 | `com.example.agentscope.harness.memory.MemoryDemo.main` |
| `filesystem` | 文件系统入门：读写、Overlay、文件查找与路径范围 | `com.example.agentscope.harness.filesystem.FilesystemDemo.main` |
| `sandbox` | 本地文件系统：文件工具、本机命令执行与跨调用读取，无需 Docker | `com.example.agentscope.harness.sandbox.SandboxDemo.main` |
| `subagent` | 主 Agent 委派两个专家，独立处理后汇总 | `com.example.agentscope.harness.subagent.SubagentDemo.main` |
| `skill` | 自动发现技能、加载详情与参考规则 | `com.example.agentscope.harness.skill.SkillDemo.main` |
| `planmode` | 规划、原生批准、执行与结果验证 | `com.example.agentscope.harness.planmode.PlanModeDemo.main` |

先阅读 [context 使用说明](context/README.md)，在 IDEA 用 JDK17+ 运行入口，输入 `1` 开始。

工作区主题阅读 [workspace 使用说明](workspace/README.md)，运行入口后自动演示，再自由提问。

会话主题阅读 [session 使用说明](session/README.md)，运行入口后自动演示；重启后用 `--resume` 继续。

记忆主题阅读 [memory 使用说明](memory/README.md)，运行入口后自动演示；重启后用 `--recall` 回忆。

文件系统主题阅读 [filesystem 使用说明](filesystem/README.md)，运行入口后自动演示，再自由提问。

新增四个主题分别阅读 [sandbox](sandbox/README.md)、[subagent](subagent/README.md)、
[skill](skill/README.md)、[planmode](planmode/README.md)。共同的模型配置和终端事件展示放在
`DemoSupport.java`；每个示例保留可直接阅读的 main 和业务步骤，不需要先理解公共辅助代码。
