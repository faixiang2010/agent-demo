package com.example.agentscope;

import java.nio.file.Path;

/** 所有示例统一使用 .agentscope/<示例名称>/{workspace,state}。 */
public record DemoStorage(Path root) {

    public static DemoStorage harnessContext() {
        return forDemo("harness-context");
    }

    public static DemoStorage harnessWorkspace() {
        return forDemo("harness-workspace");
    }

    public static DemoStorage harnessSession() {
        return forDemo("harness-session");
    }

    public static DemoStorage harnessMemory() {
        return forDemo("harness-memory");
    }

    public static DemoStorage harnessFilesystem() {
        return forDemo("harness-filesystem");
    }

    static DemoStorage firstAgent() {
        return forDemo("first-agent");
    }

    static DemoStorage multiUser() {
        return forDemo("multi-user");
    }

    private static DemoStorage forDemo(String name) {
        return new DemoStorage(Path.of(".agentscope", name).toAbsolutePath().normalize());
    }

    public Path workspace() {
        return root.resolve("workspace");
    }

    public Path state(String agentName) {
        return root.resolve("state").resolve(agentName);
    }
}
