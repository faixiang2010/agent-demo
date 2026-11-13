# Harness / Context：执行日志对照代码

参考：[官方上下文管理文档](https://java.agentscope.io/v2/zh/docs/harness/context)。
包名：`com.example.agentscope.harness.context`。

## 老大，按这个方式看

1. IDEA用 **JDK17+** 运行 `ContextManagementDemo.main`，工作目录为项目根目录。
2. 输入一个数字，例如 `1`，自动执行该场景全部步骤，中途无需输入或暂停。
3. 在同一个Java文件中用 **Ctrl+F搜索 `scenario01ContextAndState`**。
   也可在菜单的 `case 1` 方法调用上按 **Ctrl+B**；控制台同时打印普通的“文件路径:行号”。
4. 对照方法里的“步骤1/2/3”注释和控制台同编号日志，从上到下读。
5. 场景结束自动回到数字菜单，输入 `0` 退出；要对比不同效果，改该方法开头的数据，再选相同数字。

例如选1，控制台顺序为：

```text
[代码入口] scenario01ContextAndState()
[场景01/步骤1] 把编号存到RuntimeContext，只提问……
[实际效果] 第一次模型输入包含编号=false（否）
[场景01/步骤2] 把business_fact临时附到输入……
[实际效果] 第二次模型输入包含编号=true（是）
[场景01/步骤3] 读取保存的AgentState……
[实际效果] 临时材料被作为消息写进持久历史=false（否）
[本场景结论] 程序的信息不会全部自动告诉模型……
```

**默认数据、场景配置、`build()`、`agent.call()`/`streamEvents()`、效果检查都写在对应方法里。**
压缩场景直接调用原生压缩器。公共部分只处理模型配置、统一目录、打印和批量造历史等辅助工作。
不存在另一个场景调度模式；每个数字只对应一个方法。

建议阅读顺序是 **1 → 2 → 3 → 7 → 9**：先弄清模型看见什么，再理解怎么处理大材料。
理解单点后再选 **16 → 17 → 18**，查看真实工具循环、任务恢复、记忆和档案的连续流程。
模型说“我记住了”不证明功能生效，日志中的检查基于实际输入或保存的状态。

## 数字与代码一一对应

| 数字 | 搜索这个方法 | 默认完整流程 | 改这里看区别 |
|---|---|---|---|
| 1 | `scenario01ContextAndState` | 只存属性 → 明确注入 → 比较状态与输入 | `fact`、`question` |
| 2 | `scenario02RulesAndMaterials` | 写规则和资料 → 配置Agent → 提问和检查 | `rule`、`material` |
| 3 | `scenario03DynamicSource` | 查失败状态 → 更新为通过 → 同问题再查 | `initialStatus`、`updatedStatus` |
| 4 | `scenario04ReferenceSelection` | 排序 → 预算选择 → 模型只接收入选资料 | `budget`、优先级、`required` |
| 5 | `scenario05SourceFailure` | 自动对比五种来源 → 可选来源OMIT → 无资料回答 | `timeoutMillis`、`successData` |
| 6 | `scenario06TasksAndVerification` | 待办完成但验证失败 → 启用Todo → 判断整体状态 | `verification`、目标和要求 |
| 7 | `scenario07ToolResultOffload` | 保存长日志 → 原生卸载 → 显示文件和实际预览 | `repeat(100)`、阈值、预览长度 |
| 8 | `scenario08Budget` | 小预算拦截 → 大预算放行 → 核对是否到模型 | `smallLimit`、`largeLimit`、材料 |
| 9 | `scenario09Compaction` | 准备旧对话 → 原生摘要 → 显示摘要及条数 | `rounds`、`triggerMessages`、`keepMessages` |
| 10 | `scenario10PruneResults` | 旧长日志和最近结果 → 原生裁剪 → 检查标记 | 日志重复次数、`previewChars` |
| 11 | `scenario11TruncateArguments` | 长旧参数 → 原生截断 → 直接展示处理后参数 | `oldParameter`、`maxArgLength` |
| 12 | `scenario12OverflowRecovery` | 模拟溢出 → 原生压缩 → 只恢复一次 | `fact`、历史轮数 |
| 13 | `scenario13RefreshTiming` | 规则A请求 → 改规则B再请求 → 检查两次输入 | `before`、`after`、`question` |
| 14 | `scenario14MemoryAndHistory` | 完整备份 → 原生摘要 → 保存长期事实并请求 | `fact` |
| 15 | `scenario15Diagnostics` | 准备资料 → 消费流式事件 → 检查真实输入 | `information`、`question` |
| 16 | `scenario16ToolLoop` | 一次请求 → 真实Java工具修复并验证 → 下一轮读取新状态 → 原生日志卸载 | `question`、初始状态、工具中的示例逻辑 |
| 17 | `scenario17TaskRecovery` | 原生todo_write更新 → 原生计划模式 → 历史压缩 → 新实例恢复待办和模式 | 任务列表、`planText`、压缩阈值 |
| 18 | `scenario18MemoryLifecycle` | 原生提取 → 原生整理 → 压缩后档案回查 → 新会话加载长期事实 | `fact`、摘要提示 |

## 三个综合场景怎么看

**16：一次问题，内部不止一次模型请求。** 只提交一次用户消息，模型选择自定义工具，
框架真正调用`LoginDemoTools.fixAndTestLogin()`。工具修复内存里的示例逻辑，
实测过期会话返回401、有效会话返回200，并更新业务状态。下一轮中间件重新读取当前状态。
长工具日志同时由框架卸载；控制台核对工具计数、前后状态、卸载位置和持久历史。
这不是运行本项目Maven测试，也不修改项目代码。

**17：聊天被缩短，任务记录仍然可以恢复。** 模型实际调用原生`todo_write`维护完整列表，
最终验证保留为进行中。应用进入原生计划模式，写入计划；然后追加教学进度消息并触发历史摘要。
实例B用同一用户和会话恢复，旧工具回执不再逐字发送，但原生待办提醒仍从结构化任务生成。
最后通过应用API切回执行模式；没有运行模型`plan_exit`的交互审批流程。
待办状态只是教学进度，不代表场景16的验证结果已经同步到这里。

**18：纪要、长期偏好、原话可以同时保留。** 原生`MemoryFlushManager`请求模型提取事实，
框架写每日文件；`MemoryConsolidator`再请求模型合并成`MEMORY.md`。
示例显式等待这两个处理步骤完成，便于按顺序观察，不依赖后台任务何时执行。
工作历史压缩后，核对码仍可从当前版本原生JSONL档案查到；换新session后，
长期偏好被加载，原会话的核对码不应跟着进入输入。每次场景18使用独立工作区子目录，方便重复比较。

离线时16/17用本地模型输出固定的工具决策，18用固定生成规则模拟提取和整理。
**真实的框架循环、Java工具执行、状态保存、压缩、记忆文件和原生档案操作仍照常运行。**
真实模式使用已配置模型，其工具选择和生成内容可能不同，请以实际效果检查为准。

改变阈值后，没有发生压缩/卸载也可能是正确结果；先看实际输入长度和触发条件。
场景中的`ctx`由菜单创建隔离身份，`stateStore`、`workspace`、`reports`指向本次实验目录。
`baseBuilder()`集中配置模型与目录，场景自己的开关紧接着写在本方法中。
`showReply()`只打印和保存观测结果，不隐藏模型请求；`step()`只打印，不等待输入。
`ContextDemoSupport.temporaryContext()`使用真实中间件临时添加资料，不持久化为历史。

## 运行方式

默认使用已有模型配置；环境变量 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL` 优先。
想不用网络，IDEA Program arguments 填 `--offline`；离线固定回答只验证机制，不代表模型理解能力。

```powershell
# 自动使用 JDK17，并统一终端与 Maven JVM 为 UTF-8：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.context.ContextManagementDemo'
# 离线：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.context.ContextManagementDemo' -DemoArgs '--offline'
```

脚本在项目根目录执行。手动 Maven 命令需先按 [运行指南](../../../../../../../../RUNNING.md)
的“前置准备”统一 UTF-8；仅切换 JDK 或执行 `chcp` 不能完整覆盖各处编码设置。
IDEA 的 VM options 与控制台编码设置也见该指南。

菜单支持0退出或EOF结束。数字错误重新提示；配置/网络错误会显示原因后返回菜单。
同一数字可重复执行，每次使用新session，不混入之前实验的聊天。

## 当前版本与文档差异

项目使用发布版 **2.0.4**。所参考文档已包含当前JAR没有的接口：
`instruction`、`contextSource`、`ContextBlock`、`ContextPolicy`、`ContextManifest`、
`TaskContextOptions`、`VerificationService`、`contextSelectionPolicy`。
没有切换未发布版本；各场景代码注释与结论明确说明兼容部分。

- 原生能力：RuntimeContext/AgentState、sysPrompt/AGENTS/参考文件、Todo、计划模式、工具执行、结果卸载、摘要、裁剪、参数截断、流式事件，以及场景18的记忆提取/整理与当前版本JSONL档案。
- 兼容演示：临时业务来源、资料选择、来源失败策略、要求与验证材料、字符预算、应用溢出恢复、模型输入记录。
- 2.0.4参考文件注入可能位于System，不能当新版参考资料隔离保证；兼容临时资料使用USER消息，非原生RUNTIME/REFERENCE placement。
- 场景4的预算只计算资料正文字符，不是总模型预算；场景8也只是消息和工具定义的近似字符守卫。
- 场景6的验证是实验事实，不实际执行测试；计划文件不自动同步Todo。
- 场景7/10/11准备的是已执行工具的模拟历史，真实转换由框架完成，本次不执行对应工具。
- 场景12的供应商溢出错误是本地模拟，不故意发送超大请求，不无限重试。
- 默认关闭后台自动记忆处理；场景14是应用直接写事实，场景18显式调用原生提取与整理组件并等待完成。
- `full-history.txt`是应用备份；场景18使用SDK `SessionTranscriptWriter`与工作区原生JSONL档案。它不是新文档的Session Log API，且2.0.4工具归档可能截断长参数/结果，本场景回查的是未截断文字消息。
- 输入文件包含正文，非新版仅含元数据的Manifest。原生ContextPolicy/ContextManifest仍未实现，字符预算与正文记录不代替它们。

升级到含原生接口的版本后，文档诊断方式是在ContextPolicy回调读
`manifest.items()/transforms()/validation()`；或读CustomEvent `context_build`中的`context_manifest`，
以及Session Log `context/build`里的`event.value.context_manifest`。当前2.0.4不能执行这些新API。
`validation=passed`只证明准备检查通过，不证明模型回答正确。

## 文件保存位置

```text
.agentscope/harness-context/
├── workspace/scene-XX/                 # 规则、材料、长期事实、卸载全文
├── state/scene-XX/learner/experiment-UUID/agent_state.json
└── reports/scene-XX/experiment-UUID/
    ├── 学习小结.txt                     # 方法名、步骤、实际检查和结论
    ├── model-input-1.txt               # 真正到Model.stream的输入
    ├── agent-state.json               # 实际身份的状态快照（有Agent请求的场景）
    └── ...                            # 各场景转换前后历史等
```

所有路径由`DemoStorage.harnessContext()`提供，保持原项目规划。

## 离线验证

```powershell
pwsh -File scripts/verify-context-demo.ps1
```

脚本只输入数字1~18和0，检查全部场景自动完成、无课内输入、编号方法导航、重复执行、错误数字及退出。
同时检查原生卸载文件、裁剪/截断标记、摘要、临时资料不持久化、来源失败与预算拦截/放行。
实验数据放在`target/context-regression/`，不调用外部模型。
同时核对同次请求中的工具后刷新、实际Todo三项、压缩后提醒、计划状态恢复与退出、原生记忆文件、
新会话隔离和压缩后JSONL原话保留；源码位置不再输出`at ...`形式的伪异常堆栈。
