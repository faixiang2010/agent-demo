# 会话记录与恢复入门

参考：[会话日志文档](https://java.agentscope.io/v2/zh/docs/harness/session-log)。

**版本区别：**项目当前使用 AgentScope Java **2.0.4**，还没有文档中的原生
`SessionLog`、`AgentSession`、`session.transcript()`、checkpoint 事件恢复等 API。
本例使用这个版本实际提供的 `TranscriptStore` 和 `AgentStateStore`，演示聊天记录读取
与已完成对话的恢复，不把 JSONL Transcript 当成新版的原生执行日志。
没有升级全项目依赖，也不模拟不存在的原生 API。

## 怎么运行

IDEA 使用 **JDK 17+** 运行 `SessionLogDemo.main()`。
模型沿用项目 `application.properties` 的 `llm.*`，`DEEPSEEK_*` 环境变量优先。

启动后自动完成三个步骤：

1. 告诉 Agent 借书证编号和书名，框架自动保存对话。
2. 程序读取记录，在终端显示用户和助手的历史消息，这一步不调用模型。
3. 关闭旧 Agent 后创建新实例，沿用身份与存储，询问编号和书名，验证恢复。

然后可以继续输入问题，输入 `/history` 查看记录，输入 `exit` 或 `0` 退出。

| main 参数 | 行为 |
|---|---|
| 无参数 | 新建会话，自动演示，然后自由提问 |
| `--auto` | 新建会话，只跑三个步骤，不等待输入 |
| `--resume` | 重新打开最近一次演示会话，查看记录，继续自由提问 |
| `--history` | 只读取最近一次会话的聊天记录，不加载模型配置，不调用模型 |

默认每次生成新的 `sessionId`，避免不同轮次的教学对话混在一起；旧会话文件不删除。
最近一次会话 ID 保存在 `last-session.txt`。想观察**真正的进程重启**，先正常退出，
再用 `--resume` 启动，询问“我的借书证编号是什么？”。

PowerShell 在项目根目录运行：

```powershell
# 自动使用 JDK17，并统一终端与 Maven JVM 为 UTF-8：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.session.SessionLogDemo'
# 退出后重新运行，继续上次会话：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.session.SessionLogDemo' -DemoArgs '--resume'
# 只读取历史：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.session.SessionLogDemo' -DemoArgs '--history'
```

手动 Maven 命令需先按 [运行指南](../../../../../../../../RUNNING.md) 的“前置准备”统一 UTF-8。
已变成 `?` 的输入不能还原，需要重新输入；已有历史记录不会由启动脚本自动修复或删除。
IDEA 的 VM options 与控制台编码设置也见该指南。

## 代码怎么看

- `createAgent()`：指定聊天记录存储和工作状态存储，保留框架自动记录中间件。
- `ask()`：正常调用 Agent，调用完成后由框架保存记录，没有手工拼接聊天记录。
- `showHistory()`：通过 `listSegments()`、`readSegment()` 读取该版本的 JSONL 分段。
- `main()` 第三步：重新创建 Agent，用相同身份恢复对话；问题没有再次携带编号和书名。

```text
.agentscope/harness-session/
├── last-session.txt                       最近一次会话 ID
├── transcripts/demo-user/session-log-demo/
│   └── <sessionId>/events/*.jsonl          已记录的聊天消息
├── state/session-log-demo/                模型继续对话所需的工作状态
└── workspace/                            框架工作区及兼容会话文件
```

路径相对于进程工作目录，启动时会打印绝对路径。恢复时需要保持相同的 `agentId`、
`userId`、`sessionId` 和存储目录；Transcript 的 `tenant` 也保持为 `demo-user`。
2.0.4 的 Transcript 中间件使用 Agent 的 `name` 作为目录标识，因此本例让 `name` 与
`agentId` 相同，记录读写使用一致的标识。
本例单用户串行调用，未演示多副本写入协调。

**回查历史与恢复不是同一个动作。**本例的 Transcript 用于回查；`JsonFileAgentStateStore`
用于恢复后续模型调用的工作上下文。只保存 Transcript 不代表 Agent 自动从它恢复。
2.0.4 的分段写入是异步、尽力而为的镜像，本例等待已有任务结束后再读；这不提供新版
Session Log 的原子提交、写入租约、持久输入队列或未知工具结果核对保证。
本例恢复的是已经完成的对话，不是中途崩溃的工具任务续做。
