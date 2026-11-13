# 本地文件系统与命令执行

参考：[文件系统文档](https://java.agentscope.io/v2/zh/docs/harness/filesystem)、[Sandbox 文档](https://java.agentscope.io/v2/zh/docs/harness/sandbox)。使用项目现有 2.0.4。

按老大的要求，`SandboxDemo` 已改为 `LocalFilesystemSpec`，保留原来的类名和启动入口。
无需 Docker、镜像或 Python。模型沿用项目配置，Java 使用 JDK17+。

```powershell
# 在项目根目录运行；脚本统一 JDK17 和 UTF-8：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.sandbox.SandboxDemo'
# 自动演示后退出：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.sandbox.SandboxDemo' -DemoArgs '--auto'
# 只创建、检查本机教学目录，不调用模型：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.sandbox.SandboxDemo' -DemoArgs '--check'
```

默认演示结束后可以继续提问，输入 `exit` 或 `0` 退出。IDEA 直接运行 `main` 也可以。

| 步骤 | 看什么 |
|---|---|
| 第一次调用 | 模型用 `write_file` 创建 `notes/hello.txt`，用 `execute` 执行 `echo LOCAL-EXEC-7319` |
| 第二次调用 | 同一个 Agent/session 用 `read_file` 读取文件，再执行 Windows 的 `cd` 或其他系统的 `pwd` |
| Java 核验 | Java 从本机实际文件读取 `LOCAL-FS-7319`，确认文件已写入 |

核心配置：

```java
var spec = new LocalFilesystemSpec()
        .project(storage.workspace())
        .sharedLocalWorkspace(true)
        .executeTimeoutSeconds(30);
// 在 HarnessAgent.builder() 中：
// .workspace(storage.workspace()).filesystem(spec)
// 不调用 disableShellTool()，才能使用 execute。
```

每次启动创建 `.agentscope/harness-sandbox/run-<UUID>/workspace`，文件直接写在这个本机目录中。
聊天状态保存在同轮的 `state` 目录。下一次模型调用可以读取文件，是因为文件已经保存在磁盘上；
本例没有容器、快照，也不演示跨进程会话恢复。

Windows 的 `execute` 使用 `cmd.exe /c`，所以演示用 `cd` 显示当前目录，避免使用 Linux 专属命令。
文件工具的路径限制不等于操作系统隔离；Shell 命令仍具有当前 Java 进程用户的本机权限。
提示词让模型只操作教学目录，但提示词不是安全边界。

与 `FilesystemDemo` 相比，本例重点是同时展示文件读写和本机 `execute` 命令。
已在 Windows + JDK17 下实测 `--auto`：模型真实调用了文件读写和命令工具，`echo`、`cd` 成功，Java 核验文件内容通过。
通用编码设置见 [运行指南](../../../../../../../../RUNNING.md)。
