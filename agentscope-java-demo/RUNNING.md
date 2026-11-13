# 运行指南

项目包含多个可独立运行的示例，均为 Java main 类。先进入项目根目录：

```powershell
cd D:\electron\agentscope-java-demo
```

## 前置准备（每次新开终端都要做）

系统默认 `JAVA_HOME` 是 JDK 8，AgentScope 要求 JDK 17+，需临时切换。

**推荐使用通用脚本：**它自动切换本次进程的 JDK、统一终端输入/输出与 Maven JVM 为 UTF-8，
编译后启动指定示例；退出后恢复此前的控制台编码、`JAVA_HOME` 和 `MAVEN_OPTS`。

```powershell
# FirstAgent：
& .\scripts\run-demo.ps1
# 上下文实验台（真实模型）：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.context.ContextManagementDemo'
# 上下文实验台（离线）：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.context.ContextManagementDemo' -DemoArgs '--offline'
# Workspace：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.workspace.WorkspaceDemo'
# 会话记录：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.session.SessionLogDemo'
# 恢复上次会话：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.session.SessionLogDemo' -DemoArgs '--resume'
# 长期记忆（完整演示）：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.memory.MemoryDemo'
# 长期记忆（回忆已有内容）：
& .\scripts\run-memory-demo.ps1
# 文件系统：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.filesystem.FilesystemDemo'
# 本地文件系统和命令执行（无需 Docker，--check 只检查教学目录）：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.sandbox.SandboxDemo' -DemoArgs '--check'
# 主 Agent 与两个子 Agent：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.subagent.SubagentDemo'
# Skill：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.skill.SkillDemo'
# 计划模式（展示计划后，在终端输入 y 批准或拒绝）：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.planmode.PlanModeDemo'
```

其他 Java main 示例也可以通过 `-MainClass` 指定，程序参数通过 `-DemoArgs` 传递。
脚本不更改 API Key、模型配置或已有示例数据。需要手动启动 Maven 时，先做下面的准备，
再执行本文各节的命令；每次新开终端需要重新设置。

**PowerShell：**

```powershell
$env:JAVA_HOME='D:\GreenSoft\java\jdk-17.0.14'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
chcp 65001 > $null
$taskUtf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $taskUtf8
[Console]::OutputEncoding = $taskUtf8
$OutputEncoding = $taskUtf8
$env:MAVEN_OPTS = "$env:MAVEN_OPTS -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
```

**CMD：**

```cmd
set JAVA_HOME=D:\GreenSoft\java\jdk-17.0.14
set PATH=%JAVA_HOME%\bin;%PATH%
chcp 65001
set "MAVEN_OPTS=%MAVEN_OPTS% -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
```

JDK 17 在中文 Windows 上默认字符集可能为 GBK。代码按 UTF-8 读取输入时，
终端也需要按 UTF-8 编码发送，否则中文可能变成乱码或 `?`，会影响实际提问内容。
`$OutputEncoding` 控制 PowerShell 管道发送给本机进程的编码，不替代控制台输入编码。
`exec:java` 在 Maven 的 JVM 中运行，因此用 `MAVEN_OPTS` 在 JVM 启动前设置编码；
仅在 Maven 命令尾部加 `-Dfile.encoding=UTF-8` 不能可靠改变已初始化的默认字符集。
已有 `?` 无法恢复：退出旧进程，统一编码后重新输入。

IDEA 直接运行 Java main 时，在 Run Configuration 的 VM options 中设置
`-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`，
并确认 IDE 的控制台编码为 UTF-8，然后重新运行；IDEA 内置 Terminal 则按上述 PowerShell 方式启动。

---

## 1. FirstAgent（三个调用场景合一）

对应 [官方快速入门](https://java.agentscope.io/v2/zh/docs/quickstart)，使用已有的 DeepSeek 配置依次执行以下场景，共四次用户请求；框架还可能额外调用模型提取长期记忆：

| 场景 | 代码方法 | 观察什么 |
|---|---|---|
| 普通问答与多轮对话 | `runChatScenario` | 实例 A 告诉助手姓名和分享主题；关闭 A 后，实例 B 从同一会话恢复上下文 |
| 流式输出 | `runStreamingScenario` | 实例 C 用 `streamEvents` 输出文本增量；若模型使用工具，会打印工具名 |
| Web 请求生命周期 | `runWebScenario` / `handleWebRequest` | 模拟 WebFlux handler；订阅前不创建实例 D，订阅后创建，结束时关闭 |

第三段是可运行的响应式 handler 演示，没有启动 HTTP 服务，也没有增加 Spring 依赖。
命令行通过 `block()` 模拟 Web 框架订阅；移植到 WebFlux 时直接返回 `Mono<Msg>`。
不要在 `try-with-resources` 中返回未订阅的 Mono，否则实例会在请求执行前关闭。

四次请求共用 `note-taker` / `alice` / `demo-session`，并串行执行。
每次请求使用独立 Agent 实例；Builder 配好后不再修改。重复运行会继续使用已有会话。
如果想观察一次新的会话，可传入新的会话 ID，不必删除原来的数据：

```powershell
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' compile exec:java "-Dexec.args=quickstart-session-01"
```

`call` 和 `streamEvents` 都会执行请求；同一输入不要先 call 再 streamEvents。
记忆压缩配置继续保留，但四次短请求不保证触发压缩，本 Demo 主要展示上述三种调用方式。

### 本地数据目录

从项目根目录启动时，所有工作区和会话状态统一保存在项目内的 `.agentscope/`：

```text
.agentscope/
├── first-agent/                  # FirstAgent 示例
│   ├── workspace/                # 工作资料、记忆和对话日志
│   │   └── alice/
│   └── state/                    # 会话恢复状态
│       └── note-taker/alice/demo-session/agent_state.json
└── multi-user/                   # MultiUserConcurrentDemo 示例
    ├── workspace/
    │   ├── alice/
    │   └── bob/
    └── state/
        └── concurrent-note-taker/
            ├── alice/concurrent-session/agent_state.json
            └── bob/concurrent-session/agent_state.json
```

`workspace` 保存工作资料、记忆与对话记录，`state` 保存自动恢复会话的运行状态。
统一规则为 `.agentscope/<示例名称>/{workspace,state}`，由 `DemoStorage.java` 集中管理。
先按示例找目录，再按数据用途查找。JevDemo 没有会话持久化，不预建空目录。
workspace 内的 `agents`、`default`、`memory` 等子目录是框架生成的内部结构，
不手动重命名或合并；`MEMORY.md` 是用户长期记忆，sessions 是会话记录。
已有数据已按新结构迁移，用户 ID、会话 ID 与文件内容保持原样。
程序已显式指定本地状态存储，不再使用用户目录下的默认状态位置。
启动时会输出实际的工作区与状态路径。IDE 的工作目录也请设为项目根目录。
备份或迁移时复制整个 `.agentscope/` 即可；该目录已加入 `.gitignore`。

**PowerShell**（`-D` 参数必须加引号，否则会被 PowerShell 吞掉报 `Unknown lifecycle phase`）：

```powershell
mvn compile exec:java "-Dexec.mainClass=com.example.agentscope.FirstAgent"
```

**CMD：**

```cmd
mvn compile exec:java -Dexec.mainClass=com.example.agentscope.FirstAgent
```

| 配置项（application.properties） | 当前值 | 说明 |
|---|---|---|
| `llm.api-key` | 已配置 | 也可用环境变量 `DEEPSEEK_API_KEY` 覆盖 |
| `llm.base-url` / `llm.model` | DeepSeek / deepseek-flash | 换厂商或模型改这里 |
| `studio.enabled` | `true` | **为 true 时需先启动 Studio 服务**（后端 http://localhost:3000），否则连接失败后自动降级继续 |

Studio 启动（开发模式，前端 5173 / 后端 3000）：

```powershell
as_studio   # 或你本地的启动方式
```

---

## 2. JevDemo（Jev 判定示例，对接本地 Ollaya）

路由 / 评分 / 分类三种问题类型，打本地 Ollaya 服务。

**PowerShell：**

```powershell
$env:TYPESAFE_API_KEY='sk-local-dummy'   # JevClient 要求密钥非空，本地服务不校验，占位即可
mvn compile exec:java "-Dexec.mainClass=com.example.agentscope.JevDemo"
```

**CMD：**

```cmd
set TYPESAFE_API_KEY=sk-local-dummy
mvn compile exec:java -Dexec.mainClass=com.example.agentscope.JevDemo
```

| 配置项 | 当前值 | 说明 |
|---|---|---|
| `jev.enabled` | `true` | `false` 时程序提示后跳过 |
| `jev.base-url` | `http://127.0.0.1:11435` | **前提是本地 Ollaya 服务已启动** |
| `jev.model` | `laya:typed-decisions` | 本地模型名 |
| `jev.api-key` | 空 | 留空时读环境变量 `TYPESAFE_API_KEY`；填任意非空值后无需再设环境变量 |

---

## 3. MultiUserConcurrentDemo（多用户并发）

IDEA 中直接运行 `MultiUserConcurrentDemo.main`，使用 JDK 17，工作目录设为项目根目录。
模型配置与 FirstAgent 相同。本示例不接入 Studio，控制台按用户显示完整回复。

```powershell
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' compile exec:java "-Dexec.mainClass=com.example.agentscope.MultiUserConcurrentDemo"
```

```text
Alice：介绍“天宇 / ReAct” → 完成并关闭实例 → 新实例追问姓名和主题
Bob：  介绍“小明 / Maven” → 完成并关闭实例 → 新实例追问姓名和主题
       ↑ 两条路线同时执行，各自内部顺序执行
```

两个用户特意使用相同 `concurrent-session`，便于观察 `userId` 的隔离作用。
身份由 `RuntimeContext` 传入，每次请求创建独立 Builder 和 Agent，共享模型及状态存储配置。
当前依赖版本的共享 Builder 在并发 `build()` 时可能抛出 `ConcurrentModificationException`，
因此通过 `Supplier<HarnessAgent>` 每次创建全新 Builder，不对共享 Builder 并发调用 build。
`Flux.flatMap(..., 2)` 同时执行两个用户的对话，各自第一轮成功后才发第二轮。
生产应用需要在用户身份认证后填写 userId，并对同一会话的所有请求统一排队。

观察日志中的用户、轮次、开始/结束：一个用户尚未结束时另一个开始，表示并发重叠。
第二轮期望 Alice 回答天宇/ReAct，Bob 回答小明/Maven；实际措辞由模型决定。
第二轮开始时间不必一致；Alice 完成第一轮即可开始自己的第二轮，不必等待 Bob。

数据统一保存在项目下，不混入 FirstAgent 的工作区：

```text
.agentscope/multi-user/
├── workspace/
│   ├── alice/
│   └── bob/
└── state/concurrent-note-taker/
    ├── alice/concurrent-session/agent_state.json
    └── bob/concurrent-session/agent_state.json
```

默认重复运行会继续使用已有会话。IDEA Program arguments 可填入新的 sessionId，
或者 Maven 添加 `"-Dexec.args=concurrent-session-02"`。
示例发起四次用户请求，长期记忆提取可能产生额外模型调用。

---

## 4. ContextManagementDemo（Harness 上下文交互实验台）

在 IDEA 运行 `com.example.agentscope.harness.context.ContextManagementDemo.main`，
使用 JDK 17+，工作目录设为项目根目录。输入数字1~18，所选场景使用默认数据自动执行完整流程，
中途不用输入或按回车；完成后回到数字菜单，输入0退出。
菜单1对应 `scenario01ContextAndState()`，菜单9对应 `scenario09Compaction()`，其余按编号对应。
日志的“步骤1/2/3”与同方法内的代码注释对应；方法中直接展示配置、Agent调用和结果检查。
在IDEA用Ctrl+F搜索方法名，或在菜单switch上Ctrl+B跳转；场景开始打印普通文件路径和行号。
要改变效果，修改所选方法开头的默认数据，再运行同一数字；中文记录保存在 `学习小结.txt`。

```powershell
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' compile exec:java "-Dexec.mainClass=com.example.agentscope.harness.context.ContextManagementDemo"
```

需要无需网络的体验时，在 IDEA Program arguments 填 `--offline`，
或 Maven 添加 `"-Dexec.args=--offline"`。模拟回答不代表真实模型语义效果。

菜单共18个场景，覆盖规则/材料、动态来源、失败策略、任务进度、卸载、预算、压缩、
裁剪、溢出、刷新、记忆和诊断；数据在 `.agentscope/harness-context/`。
新增16是一次请求内的真实工具循环；17是真实Todo更新、压缩后恢复与计划模式；
18是原生记忆提取/整理、新会话读取，以及当前版本JSONL原始消息回查。
当前发布版2.0.4缺少文档中的部分新接口，菜单明确区分原生和兼容演示。
完整场景与方法对应、版本差异和观察方式见 `src/main/java/com/example/agentscope/harness/context/README.md`。

---

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `Unknown lifecycle phase ".mainClass=..."` | PowerShell 吞掉了 `-D` 参数 | 给 `-Dexec.mainClass=...` 加引号，或改用 CMD |
| `缺少 API 密钥` | 环境变量与配置文件都没配 | 按上表配置 |
| JevDemo 直接提示跳过 | `jev.enabled=false` | 改为 `true` |
| `[Studio] 连接 ... 失败` | Studio 服务未启动 | 启动服务，或 `studio.enabled=false` |
| 中文输入/输出乱码、输入变成 `?` | 终端输入/输出和 Java 编码不一致 | 退出旧进程，用 `scripts/run-demo.ps1` 启动，或按前置准备统一 UTF-8；重新输入已损坏的内容 |
