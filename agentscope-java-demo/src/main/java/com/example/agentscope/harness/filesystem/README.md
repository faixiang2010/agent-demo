# 文件系统入门

参考：[AgentScope Filesystem 文档](https://java.agentscope.io/v2/zh/docs/harness/filesystem)。
使用项目现有 **2.0.4**，从无需额外服务的本机模式开始。

## 运行

在 IDEA 使用 **JDK 17+** 运行 `FilesystemDemo.main()`。
模型沿用 `application.properties` 的 `llm.*`，`DEEPSEEK_*` 环境变量优先。
PowerShell 在项目根目录执行（脚本自动统一 UTF-8 与 JDK17）：

```powershell
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.filesystem.FilesystemDemo'
# 只看自动演示，不进入自由提问：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.filesystem.FilesystemDemo' -DemoArgs '--auto'
```

手动 Maven 命令和 IDEA 的编码设置见 [运行指南](../../../../../../../../RUNNING.md)。

## 看什么

| 步骤 | 观察什么 | 代码方法 |
|---|---|---|
| 1 | LLM 读取计划、修改状态、创建小结，再读取；磁盘核对项目原文件仍是待办，工作区副本是完成 | `editAndWrite` |
| 2 | LLM 列目录、按文件名找文件、按内容搜关键词 | `searchFiles` |
| 3 | Java 通过同一个 Filesystem 读取一个存在但位于允许范围外的教学文件，验证访问失败 | `checkPathBoundary` |

之后可以继续输入“读取 notes/summary.txt”或“在 notes 下新建一份学习清单”，
输入 `exit` 或 `0` 退出。终端会显示 `[文件工具] read_file` 等真实工具名称。
第一步不仅观察模型回答，还检查实际文件是否创建、状态是否修改以及原文件是否保持不变。

```text
.agentscope/harness-filesystem/run-<UUID>/
├── project/notes/plan.txt           Java 准备的原文件，状态：待办
├── workspace/notes/
│   ├── plan.txt                    Agent 修改的副本，状态：完成
│   └── summary.txt                 Agent 创建的小结
├── state/filesystem-demo/          会话工作状态
└── outside-example.txt             本例创建的范围外测试文件
```

每次运行创建新的教学目录，不覆盖已有演示；文件全部保留，启动时打印绝对路径。
Java 只准备项目种子文件和范围外测试文件，计划修改与小结创建由 LLM 调用框架工具完成。

## 最重要的配置

```java
.workspace(workspace)
.filesystem(new LocalFilesystemSpec()
        .project(project)
        .mode(LocalFsMode.ROOTED)
        .sharedLocalWorkspace(true))
```

本机模式有两层：**读先看 workspace，找不到再看 project；默认写到 workspace。**
因此同样的 `notes/plan.txt`，首次读取得到项目原文件，修改后再读得到工作区副本。
这是 copy-on-write 行为；`workspace(...)` 不代表所有项目文件都已被复制进去。

如果希望普通代码文件直接修改项目目录，可使用 `projectWritable(true)`。
这会改变写入位置；工作区元数据仍写到 workspace。本例保留默认值 `false`，
方便直接比较原文件与修改副本。

`sharedLocalWorkspace(true)` 是单用户教学配置，让相对路径对应打印的根目录，
不提供多租户文件隔离。真正按用户隔离的系统需配置命名空间，不能照搬此选项。
第三步是文件路径范围测试，不是操作系统级沙箱；Shell 工具在本例关闭。

## 文件工具怎么分

| 工具 | 作用 |
|---|---|
| `read_file` | 读取文件正文 |
| `write_file` | 创建文件，本例写入不存在的小结文件 |
| `edit_file` | 精确替换已有文件中的文字 |
| `list_files` | 列出目录内容 |
| `glob_files` | 按名称模式查找，例如 `*.txt` |
| `grep_files` | 搜索文件内容，例如“完成” |

**Workspace 描述“放什么资料”，Filesystem 描述“这些资料在哪里、如何读写”。**
文档还有共享存储与沙箱模式；本例集中演示本机文件工具与 Overlay，
不需要 Redis、Milvus、Docker 或外部沙箱配置。
