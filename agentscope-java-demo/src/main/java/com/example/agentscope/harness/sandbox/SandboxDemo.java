package com.example.agentscope.harness.sandbox;

import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import static com.example.agentscope.harness.DemoSupport.*;

/** 本地文件系统：Agent 创建文件、执行本机命令，再在下一次调用中读取文件。 */
public class SandboxDemo {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        if (args.length > 1 || !Set.of("", "--auto", "--check").contains(mode))
            throw new IllegalArgumentException("用法：SandboxDemo [--auto | --check]");
        var storage = storage("sandbox");
        Files.createDirectories(storage.workspace());
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String directoryCommand = windows ? "cd" : "pwd";
        System.out.println("老大，本例使用本地文件系统，无需 Docker；文件与命令在本机执行。");
        System.out.println("教学工作目录：" + storage.workspace());
        System.out.println("命令解释器：" + (windows ? "cmd.exe" : "/bin/sh"));
        if ("--check".equals(mode)) {
            System.out.println("[环境检查] 工作目录已创建，可写：" + Files.isWritable(storage.workspace()));
            return; // 只检查本机目录，不调用模型。
        }
        var context = context();
        // 文件工具与 execute 共用教学目录；保留 Shell 工具，不调用 disableShellTool()。
        var spec = new LocalFilesystemSpec().project(storage.workspace()).sharedLocalWorkspace(true)
                .executeTimeoutSeconds(30);
        try (HarnessAgent agent = HarnessAgent.builder().name("local-filesystem-teacher")
                .agentId("local-filesystem-teacher").model(model()).workspace(storage.workspace())
                .filesystem(spec).stateStore(new JsonFileAgentStateStore(storage.state("local-filesystem-teacher")))
                .sysPrompt("你是本地文件系统教学助手，中文简短回答，称呼用户为老大。"
                        + "文件工具和 execute 都在本机执行，只操作当前教学目录的 notes 文件。"
                        + "当前系统为 " + (windows ? "Windows，execute 使用 cmd.exe /c。" : "类 Unix，execute 使用 sh。")
                        + "只执行教学用的 echo 和 " + directoryCommand + " 命令。按要求真实调用工具。")
                .disableMemoryHooks().disableMemoryTools().disableCompaction()
                .disableSubagents().disableDynamicSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableToolsConfig().disableWebTools().maxIters(8).build()) {
            Trace first = ask(agent, "请用 write_file 创建 notes/hello.txt，内容为 LOCAL-FS-7319；"
                    + "再用 execute 执行 echo LOCAL-EXEC-7319，告诉我命令输出。", context);
            first.requireTool("write_file");
            first.requireTool("execute");
            Trace second = ask(agent, "这是下一次调用。请用 read_file 读取 notes/hello.txt，"
                    + "再用 execute 执行 " + directoryCommand + "，告诉我文件内容和命令工作目录。", context);
            second.requireTool("read_file");
            second.requireTool("execute");
            var file = storage.workspace().resolve("notes/hello.txt");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.contains("LOCAL-FS-7319"))
                throw new IllegalStateException("文件内容与演示要求不一致：" + file);
            System.out.println("[本机文件核验] " + file + "，内容：" + content.trim());
            System.out.println("文件直接保存在本机目录，没有容器或沙箱快照；每次启动创建新的教学目录。");
            if (args.length == 0) chat(agent, context);
        }
    }
}