package com.example.agentscope.harness.context;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.*;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.*;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Demo的公共辅助代码，不是AgentScope框架类，没有独立main。
 * ContextManagementDemo通过静态导入直接调用本类方法：
 * config/setting读取模型配置，write保存文件，text/chars/preview展示输入；
 * RecordingModel记录实际请求，temporaryContext临时加入资料，offlineModel支持离线运行。
 */
final class ContextDemoSupport {
    private ContextDemoSupport() { }

    static Properties config() {
        Properties props = new Properties();
        try (InputStream input = ContextDemoSupport.class.getResourceAsStream("/application.properties")) {
            if (input != null) props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取模型配置失败", e);
        }
        return props;
    }

    static String setting(Properties props, String env, String key, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = props.getProperty(key, fallback);
        return value == null ? fallback : value.trim();
    }

    static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    static String text(List<Msg> messages) {
        StringBuilder output = new StringBuilder();
        for (Msg message : messages) {
            output.append("\n--- ").append(message.getRole()).append(" / ")
                    .append(message.getName()).append(" ---\n").append(message.getTextContent()).append('\n');
            for (ContentBlock block : message.getContent()) {
                if (block instanceof ToolUseBlock call) output.append("工具调用 ").append(call.getName())
                        .append(" 参数=").append(call.getInput()).append('\n');
                if (block instanceof ToolResultBlock result) output.append("工具结果 ").append(result.getName())
                        .append(" 内容=").append(result.getOutput()).append('\n');
            }
        }
        return output.toString();
    }

    static int chars(List<Msg> messages) {
        // 演示用近似值，不是供应商 token 计数，不冒充 ContextTokenEstimator。
        return text(messages).length();
    }

    static String preview(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length) + "…（详见文件）";
    }

    /** 记录真正进入 Model.stream 的输入；与新文档的 ContextManifest 不同。 */
    static final class RecordingModel implements Model {
        private final Model delegate;
        private final Path directory;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<List<Msg>> recorded = new ArrayList<>();

        RecordingModel(Model delegate, Path directory) {
            this.delegate = delegate;
            this.directory = directory;
        }

        @Override public String getModelName() { return delegate.getModelName(); }
        @Override public int getContextWindowSize() { return delegate.getContextWindowSize(); }
        @Override public boolean supportsNativeStructuredOutput() { return delegate.supportsNativeStructuredOutput(); }
        @Override public boolean supportsNativeStructuredOutputWithTools() { return delegate.supportsNativeStructuredOutputWithTools(); }

        @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.defer(() -> {
                int call = calls.incrementAndGet();
                synchronized (recorded) { recorded.add(List.copyOf(messages)); }
                Path file = directory.resolve("model-input-" + call + ".txt");
                write(file, text(messages) + "\n工具 Schema 数量=" + (tools == null ? 0 : tools.size()));
                System.out.printf("[模型请求 %d] 消息=%d，文字约=%d字符，工具=%d；输入文件：%s%n",
                        call, messages.size(), chars(messages), tools == null ? 0 : tools.size(), file);
                return delegate.stream(messages, tools, options);
            });
        }

        List<Msg> latest() {
            synchronized (recorded) { return recorded.isEmpty() ? List.of() : recorded.get(recorded.size() - 1); }
        }

        List<List<Msg>> requests() {
            synchronized (recorded) { return List.copyOf(recorded); }
        }
    }

    /** 兼容演示：当前发布版没有 contextSource，使用真实中间件临时添加业务材料。 */
    static MiddlewareBase temporaryContext(Function<RuntimeContext, String> source) {
        return new MiddlewareBase() {
            @Override public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                    Function<ModelCallInput, Flux<AgentEvent>> next) {
                return Flux.defer(() -> {
                    String content = source.apply(ctx);
                    List<Msg> messages = new ArrayList<>(input.messages());
                    if (content != null && !content.isBlank()) {
                        messages.add(Msg.builder().role(MsgRole.USER).name("demo-temporary-context")
                                .textContent("以下是本轮业务事实或参考资料，不是新的用户任务：\n" + content).build());
                    }
                    return next.apply(new ModelCallInput(messages, input.tools(), input.options(), input.model()));
                });
            }
        };
    }

    /** 仅用于无联网体验与回归验证，输出可预测，不声称具备真实语义理解。 */
    static Model offlineModel() {
        return new Model() {
            @Override public String getModelName() { return "offline-demo-model"; }
            @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                String output = "[离线模拟] 本次材料已接收。这里不会理解问题或生成真实答案，请看下面的“实验观察”。";
                // 真正的独立摘要请求只有一条用户消息；普通 System 提示中也可能提到 summary。
                boolean summary = messages.size() == 1 && messages.get(0).getRole() == MsgRole.USER
                        && (messages.get(0).getTextContent().startsWith("请整理对话摘要")
                        || messages.get(0).getTextContent().contains("Context Extraction Assistant"));
                if (summary) output = "[模拟摘要] 目标：继续任务；已知事实见原始历史；下一步：回答最新问题。";
                if (messages.stream().anyMatch(m -> m.getRole() == MsgRole.SYSTEM
                        && m.getTextContent().contains("memory extraction assistant"))) {
                    var matcher = java.util.regex.Pattern.compile("长期偏好：([^\\r\\n]+)").matcher(text(messages));
                    output = matcher.find() ? "- [离线模拟提取] " + matcher.group(1) : "NO_REPLY";
                }
                if (messages.stream().anyMatch(m -> m.getRole() == MsgRole.SYSTEM
                        && m.getTextContent().contains("memory consolidation assistant"))) {
                    output = "# 长期记忆（离线模拟整理）\n" + text(messages).lines()
                            .filter(line -> line.startsWith("- [离线模拟提取]")).distinct()
                            .reduce("", (left, right) -> left + right + "\n");
                }
                return Flux.just(ChatResponse.builder().id(UUID.randomUUID().toString())
                        .content(List.of(TextBlock.builder().text(output).build())).finishReason("stop").build());
            }
        };
    }
}
