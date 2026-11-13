# Workspace 入门示例

参考：[AgentScope Workspace 文档](https://java.agentscope.io/v2/zh/docs/harness/workspace)。
代码使用项目现有 AgentScope Java 2.0.4，演示本机工作区。

在 IDEA 用 JDK 17+ 运行 `WorkspaceDemo.main()`。模型沿用 `application.properties` 中的
`llm.*` 配置；`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL` 环境变量优先。

启动自动跑三个步骤，然后可以自己输入问题，反复查询，输入 `exit` 或 `0` 退出。
只想看自动演示，可以给 main 传 `--auto`。

| 步骤 | 观察内容 | 代码位置 |
|---|---|---|
| 1 | 没把图书馆名字和营业时间写进问题，Agent 仍可以从工作区读取 | `prepareWorkspace`、`createAgent` |
| 2 | 借阅细则按需通过 `read_file` 读取，终端显示工具名称 | `demonstrate` 的第二段 |
| 3 | Java 修改 `PREFERENCES.md`，同一个 Agent 下一次调用读到新的格式要求 | `demonstrate` 的第三段 |

```text
.agentscope/harness-workspace/
├── workspace/
│   ├── AGENTS.md                  行为规则
│   ├── PREFERENCES.md             additionalContextFile 显式加载的参考材料
│   ├── knowledge/
│   │   ├── KNOWLEDGE.md           知识入口
│   │   └── borrowing.md           按需读取的明细
│   └── ...                       框架按需生成的运行文件
└── state/workspace-demo/          本例显式指定的 Agent 状态目录
```

路径相对于进程工作目录，启动时会打印实际绝对路径。首次生成四个教学文件，以后不覆盖，
你可以自己修改内容再运行。步骤 3 临时修改偏好，结束或出错后通过 `finally` 恢复原文。
图书馆信息是虚构的，不是实际营业规定。

工作区内容由框架在每次 Agent 调用时重新加载；同一次调用中的修改不会自动刷新。
`AGENTS.md` 是行为规则；知识入口与额外文件是参考材料；`borrowing.md` 正文需读文件工具获取。
本例没有把工作区变成向量数据库，也没有使用之前的 Milvus RAG。

`LocalFilesystemSpec().project(workspace).sharedLocalWorkspace(true)` 让本例文件工具的
相对路径也从这个工作区解析，Java 准备的文件可以直接用 `knowledge/borrowing.md` 读取。
这里是单用户教学配置；共享本地根模式不用于多租户隔离。

先看四个文件，再看 `createAgent()` 中的 `workspace(...)` 和 `additionalContextFile(...)`，
最后对照终端的三个步骤阅读 `demonstrate()`。记忆、子智能体、Shell 等功能在本例关闭，
以便集中理解 Workspace；Workspace 目录本身不等同于操作系统级沙箱。

PowerShell 运行（项目根目录）：

```powershell
# 自动使用 JDK17，并统一终端与 Maven JVM 为 UTF-8：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.workspace.WorkspaceDemo'
# 只看自动演示：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.workspace.WorkspaceDemo' -DemoArgs '--auto'
```

手动 Maven 命令需先按 [运行指南](../../../../../../../../RUNNING.md) 的“前置准备”统一 UTF-8。
如果中文已变为 `?`，先退出旧进程，按新方式启动后重新输入。
IDEA 的 VM options 与控制台编码设置也见该指南。
