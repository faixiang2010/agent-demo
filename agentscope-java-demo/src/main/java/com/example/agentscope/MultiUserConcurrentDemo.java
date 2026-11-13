package com.example.agentscope;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * 多用户并发：不同用户同时处理，同一用户的两轮消息按顺序处理。
 * 在 IDEA 中直接运行 main，工作目录设为项目根目录，使用 JDK 17+。
 */
public class MultiUserConcurrentDemo {

    record UserScenario(String userId, String sessionId, String name, String topic) {
        RuntimeContext context() {
            return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
        }
    }

    public static void main(String[] args) {
        String sessionId = args.length == 0 ? "concurrent-session" : args[0].trim();
        if (args.length > 1 || sessionId.isBlank()) {
            throw new IllegalArgumentException("用法：MultiUserConcurrentDemo [sessionId]");
        }
        Properties props = loadProperties();
        String apiKey = resolve("DEEPSEEK_API_KEY", props, "llm.api-key", "");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("请设置 DEEPSEEK_API_KEY 或配置 llm.api-key");
        }
        Model model = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(resolve("DEEPSEEK_BASE_URL", props, "llm.base-url", "https://api.deepseek.com"))
                .modelName(resolve("DEEPSEEK_MODEL", props, "llm.model", "deepseek-chat"))
                .stream(Boolean.parseBoolean(props.getProperty("llm.stream", "true")))
                .build();

        // 单独的演示目录，避免与 FirstAgent 的已有记忆混在一起。
        DemoStorage storage = DemoStorage.multiUser();
        Path stateDir = storage.state("concurrent-note-taker");
        JsonFileAgentStateStore stateStore = new JsonFileAgentStateStore(stateDir);
        // 每次请求使用独立 Builder，避免 build() 内部排序共享列表时发生并发修改。
        // 模型和状态存储共享；Agent、Toolkit 和 Builder 均按请求创建。
        Supplier<HarnessAgent> agentFactory = () -> HarnessAgent.builder()
                .name("concurrent-note-taker")
                .agentId("concurrent-note-taker")
                .sysPrompt("你是笔记助手，使用中文简短回答。姓名和任务以当前会话用户提供的信息为准。")
                .model(model)
                .workspace(storage.workspace())
                .stateStore(stateStore)
                .build();
        List<UserScenario> users = List.of(
                new UserScenario("alice", sessionId, "天宇", "ReAct"),
                new UserScenario("bob", sessionId, "小明", "Maven"));

        System.out.println("[数据目录] " + storage.root());
        System.out.println("两个用户同时接待，每个用户内部按“介绍 → 追问”的顺序处理。");
        System.out.println("特意使用相同 sessionId；userId 不同，依然是两份独立的会话档案。");
        System.out.println("alice: 天宇 / ReAct；bob: 小明 / Maven。请对照第二轮回复。\n");
        runConversations(agentFactory, users).blockLast();
        System.out.println("\n[全部完成] 观察各用户的开始/结束日志，交错表示请求有重叠。");
        System.out.println("[记忆隔离] Alice 应记住天宇/ReAct，Bob 应记住小明/Maven。");
        System.out.println("[实例隔离] 四次请求各自创建、关闭一个 Agent，第二轮从保存的会话恢复。");
        System.out.println("[顺序规则] 不同用户可以并发；同一会话先等第一轮完成，才开始第二轮。");
    }

    /** flatMap 最多同时接待两个用户；每个用户的两轮通过 flatMap 串接。 */
    static Flux<Msg> runConversations(Supplier<HarnessAgent> agentFactory, List<UserScenario> users) {
        return Flux.fromIterable(users)
                .flatMap(user -> callOnce(agentFactory, user, 1,
                        "我叫" + user.name() + "，今天准备一个关于" + user.topic() + "的技术分享。请简短确认。")
                        .flatMap(firstReply -> callOnce(agentFactory, user, 2,
                                "我叫什么？今天准备什么主题的分享？只回答我的姓名和主题。")), 2);
    }

    private static Mono<Msg> callOnce(Supplier<HarnessAgent> agentFactory, UserScenario user,
                                      int round, String input) {
        // 在工作线程创建实例。Mono.using 会在完成、异常或取消时关闭实例。
        return Mono.using(
                () -> {
                    log(user, round, "开始请求，创建新实例");
                    return agentFactory.get();
                },
                agent -> agent.call(new UserMessage(input), user.context())
                        .switchIfEmpty(Mono.error(new IllegalStateException("Agent 未返回回复")))
                        .doOnNext(reply -> log(user, round, "助手回复：" + reply.getTextContent()))
                        .doOnError(error -> log(user, round, "请求失败：" + error.getMessage())),
                agent -> {
                    agent.close();
                    log(user, round, "结束请求，实例已关闭");
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static void log(UserScenario user, int round, String message) {
        // 一次打印一条完整日志，避免不同用户的文本逐字混在一起。
        System.out.printf("[用户=%s | 会话=%s | 第%d轮 | 线程=%s] %s%n",
                user.userId(), user.sessionId(), round, Thread.currentThread().getName(), message);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = MultiUserConcurrentDemo.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }
        return props;
    }

    private static String resolve(String envName, Properties props, String key, String fallback) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String value = props.getProperty(key, fallback);
        return value == null ? fallback : value.trim();
    }
}
