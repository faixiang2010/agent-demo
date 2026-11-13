# 长期记忆入门

参考：[AgentScope Memory 文档](https://java.agentscope.io/v2/zh/docs/harness/memory)。
本例使用项目现有 AgentScope Java **2.0.4** 的真实记忆工具与整理组件。

在 IDEA 使用 **JDK 17+** 运行 `MemoryDemo.main()`。模型沿用 `application.properties`
中的 `llm.*`；`DEEPSEEK_*` 环境变量优先。

## 自动演示

| 步骤 | 发生什么 | 对应方法 |
|---|---|---|
| 1 | LLM 调用 `memory_save` 保存偏好，终端显示生成的两个文件 | `saveMemory` |
| 2 | `MemoryConsolidator` 调用模型整理日台账，重写 `MEMORY.md` | `consolidateMemory` |
| 3 | 关闭旧 Agent，用相同用户与工作区创建新 Agent、新会话，仍能回答偏好 | `recallMemory` |
| 4 | LLM 调用 `memory_search` 查关键词，再用 `memory_get` 读取相关行 | `searchMemory` |

然后进入自由提问，可以输入“请记住：我喜欢用表格比较方案”。
输入 `/memory` 查看文件，输入 `exit` 或 `0` 退出。

| main 参数 | 行为 |
|---|---|
| 无参数 | 完整自动演示，然后自由提问 |
| `--auto` | 完整自动演示后退出，不等待输入 |
| `--recall` | 跳过保存和整理，用新会话回忆、检索已有记忆，再自由提问 |

先运行一次默认示例，退出后用 `--recall` 重启，观察记忆跨进程保留。
如果还没有保存过，回忆会缺少资料；不会自动为你补造偏好。

```text
.agentscope/harness-memory/
├── workspace/learner/
│   ├── MEMORY.md                 长期记忆，新会话仍会加载
│   ├── memory/YYYY-MM-DD.md      当日记忆台账
│   └── agents/...               框架的会话产物
└── state/memory-demo/            各 session 的工作状态
```

路径相对于进程工作目录，示例会打印当前用户的实际记忆文件路径。
`userId` 固定为 `learner`，每次都生成新的 `sessionId`。
第三步没有恢复第一步的聊天历史，也没有在问题中重复偏好；回答依赖长期记忆。

## 三个容易混淆的概念

| 内容 | 用途 |
|---|---|
| 聊天历史 | 原来具体说过什么，按会话保存与恢复 |
| 长期记忆 | 偏好和重要事实，同一用户跨会话使用 |
| 对话摘要 | 历史太长时缩短当前工作上下文，不等同于长期记忆 |

框架有两条常见的记忆写入路线：

- **显式保存：**`memory_save` 直接追加到 `MEMORY.md` 和当日台账，本例主要演示这一条。
- **自动提取：**Memory Hooks 调用模型从对话提取长期事实，写入日台账，再由整理步骤合并。

本例调用 `.disableMemoryHooks()` 关闭自动提取和后台维护，方便按步骤观察；
保留记忆工具，所以 `MEMORY.md` 仍会加载。不是关闭了所有记忆能力。
步骤 2 显式调用整理并等待完成，也会消耗一次额外模型请求；没有新增台账时，框架可能跳过整理。
想体验自动流程，可以移除 `.disableMemoryHooks()`，并按文档使用 `MemoryConfig`
配置提取与整理触发策略；自动流程有后台执行时机，结果不一定立刻生成。

`memory_save` 是追加写入，重复完整演示可能留下重复台账；整理负责合并长期记忆。
示例不会删除已有记忆，也没有用 Java `Files.writeString` 直接伪造记忆内容。
本例只演示同一用户；改变用户身份后不能依赖另一用户的记忆。

2.0.4 的 `memory_search` 是关键词检索，并非 Milvus 向量检索；该版本没有新版的
`matchMode` 参数，本例只传单个关键词。原生 Session Log、长历史压缩与大工具结果卸载
不在这个入门示例中重复展开。

PowerShell 在项目根目录运行（统一终端和 Maven JVM 为 UTF-8）：

也可以使用通用 `scripts/run-demo.ps1 -MainClass 'com.example.agentscope.harness.memory.MemoryDemo'`，
并通过 `-DemoArgs '--recall'` 直接读取已有记忆。其他示例与 IDEA 的编码设置见
[运行指南](../../../../../../../../RUNNING.md)。

```powershell
# 完整演示，然后自由提问：
& .\scripts\run-memory-demo.ps1 -Mode Interactive
# 重新启动，直接读取已有记忆（默认模式）：
& .\scripts\run-memory-demo.ps1
# 只跑自动演示：
& .\scripts\run-memory-demo.ps1 -Mode Auto
```

如果用手动 Maven 命令，先统一编码，再启动 Maven：

```powershell
chcp 65001 > $null
$taskUtf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $taskUtf8
[Console]::OutputEncoding = $taskUtf8
$OutputEncoding = $taskUtf8
$env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
$env:MAVEN_OPTS = "$env:MAVEN_OPTS -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' compile exec:java '-Dexec.mainClass=com.example.agentscope.harness.memory.MemoryDemo' '-Dexec.args=--recall'
```

JDK 17 在中文 Windows 上可能默认使用 GBK，而代码明确按 UTF-8 读取。
只设置 `JAVA_HOME` 不会更改终端编码。`exec:java` 在 Maven 自己的 JVM 中运行，
所以要通过 `MAVEN_OPTS` 在 JVM 启动前设置编码，单独在 Maven 命令尾部加
`-Dfile.encoding=UTF-8` 不能可靠地改变已初始化的默认字符集。
`$OutputEncoding` 用于 PowerShell 向本机进程传递管道文本，不代替控制台输入编码。
已经被替换成 `?` 的输入无法还原；退出当前进程，统一编码后重新输入。
脚本退出后会恢复之前的控制台编码、`JAVA_HOME` 和 `MAVEN_OPTS`。
