package demo;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReAct 步骤日志记录器
 * <p>
 * 通过 Spring AI Alibaba 的 Hook 机制，在 Agent 执行前后、模型调用前后插入日志，
 * 完整追踪 ReAct（Reasoning + Acting）循环的每一步：
 * 思考（模型推理）→ 行动（工具调用）→ 观察（工具返回）→ 最终回复。
 * <p>
 * 内部维护两个计数器：
 * <ul>
 *   <li>stepCounter - 记录 ReAct 循环步数（每次 beforeModel 递增）</li>
 *   <li>modelCallCounter - 记录模型实际调用次数（与步数相同）</li>
 * </ul>
 */
public class ReactStepLogger {

    private static final Logger log = LoggerFactory.getLogger(ReactStepLogger.class);

    /**
     * ReAct 循环步数计数器（线程安全）
     */
    private final AtomicInteger stepCounter = new AtomicInteger(0);

    /**
     * 模型调用次数计数器（线程安全）
     */
    private final AtomicInteger modelCallCounter = new AtomicInteger(0);

    /**
     * 当前使用的模型提供者名称（用于日志输出）
     */
    private final String provider;

    /**
     * 创建 ReAct 步骤日志记录器
     *
     * @param provider 模型提供者名称，如 "DashScope (qwen-plus)" 或 "DeepSeek (deepseek-chat)"
     */
    public ReactStepLogger(String provider) {
        this.provider = provider;
    }

    /**
     * 创建模型级 Hook（在模型调用前后触发）
     * <p>
     * 记录内容：
     * <ul>
     *   <li>beforeModel：打印 ReAct 步骤编号、模型调用次数、当前消息列表</li>
     *   <li>afterModel：打印模型决定调用的工具或最终回复</li>
     * </ul>
     * <p>
     * 注意：必须返回 {@code new AgentCommand(List.of(), UpdatePolicy.APPEND)}，
     * 因为 {@code AgentCommand(List)} 单参构造默认使用 {@link UpdatePolicy#REPLACE}，
     * 会导致消息列表被清空，引发 "No messages found in state" 错误。
     *
     * @return MessagesModelHook 实例
     */
    public MessagesModelHook createModelHook() {
        return new MessagesModelHook() {
            @Override
            public String getName() {
                return "react_step_model_logger";
            }

            @Override
            public HookPosition[] getHookPositions() {
                return new HookPosition[]{HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
            }

            @Override
            public AgentCommand beforeModel(List<Message> messages, RunnableConfig config) {
                int step = stepCounter.incrementAndGet();
                int modelCall = modelCallCounter.incrementAndGet();
                log.info("==============================================================");
                log.info("[ReAct 步骤 {}] 模型调用 #{} | 提供者: {}", step, modelCall, provider);
                log.info("[思考] 模型正在推理，当前消息数: {}", messages.size());
                for (int i = 0; i < messages.size(); i++) {
                    Message msg = messages.get(i);
                    String text = msg.getText();
                    if (text != null && text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    log.info("  消息[{}] 类型={} 内容={}", i, msg.getMessageType(), text);
                }
                log.info("==============================================================");
                return new AgentCommand(List.of(), UpdatePolicy.APPEND);
            }

            @Override
            public AgentCommand afterModel(List<Message> messages, RunnableConfig config) {
                if (!messages.isEmpty()) {
                    Message lastMsg = messages.get(messages.size() - 1);
                    String text = lastMsg.getText();
                    if (text != null && text.length() > 300) {
                        text = text.substring(0, 300) + "...";
                    }
                    if (lastMsg instanceof AssistantMessage) {
                        AssistantMessage assistantMsg = (AssistantMessage) lastMsg;
                        if (!assistantMsg.getToolCalls().isEmpty()) {
                            for (AssistantMessage.ToolCall tc : assistantMsg.getToolCalls()) {
                                log.info("  [行动] 模型决定调用工具: {} 参数: {}", tc.name(), tc.arguments());
                            }
                        } else {
                            log.info("  [最终回复] {}", text);
                        }
                    } else {
                        log.info("  [模型输出] 类型={} 内容={}", lastMsg.getMessageType(), text);
                    }
                }
                return new AgentCommand(List.of(), UpdatePolicy.APPEND);
            }
        };
    }

    /**
     * 创建 Agent 级 Hook（在 Agent 执行前后触发）
     * <p>
     * 记录内容：
     * <ul>
     *   <li>beforeAgent：打印 Agent 启动信息、输入消息列表</li>
     *   <li>afterAgent：打印总步数、总模型调用次数、最终回复</li>
     * </ul>
     *
     * @return MessagesAgentHook 实例
     */
    public MessagesAgentHook createAgentHook() {
        return new MessagesAgentHook() {
            @Override
            public String getName() {
                return "react_step_agent_logger";
            }

            @Override
            public HookPosition[] getHookPositions() {
                return new HookPosition[]{HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT};
            }

            @Override
            public void setAgent(ReactAgent agent) {
                // 本实现不需要持有 Agent 引用，留空即可
            }

            @Override
            public ReactAgent getAgent() {
                return null;
            }

            @Override
            public int getOrder() {
                return 0;
            }

            @Override
            public AgentCommand beforeAgent(List<Message> messages, RunnableConfig config) {
                log.info("================================================================");
                log.info("[ReAct 启动] 智能体开始处理 | 提供者: {} | 输入消息数: {}", provider, messages.size());
                for (Message msg : messages) {
                    String text = msg.getText();
                    if (text != null && text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    log.info("  输入: 类型={} 内容={}", msg.getMessageType(), text);
                }
                log.info("================================================================");
                return new AgentCommand(List.of(), UpdatePolicy.APPEND);
            }

            @Override
            public AgentCommand afterAgent(List<Message> messages, RunnableConfig config) {
                int totalSteps = stepCounter.get();
                int totalModelCalls = modelCallCounter.get();
                log.info("================================================================");
                log.info("[ReAct 完成] 总步骤数: {} | 模型调用次数: {} | 提供者: {}", totalSteps, totalModelCalls, provider);
                if (!messages.isEmpty()) {
                    Message lastMsg = messages.get(messages.size() - 1);
                    String text = lastMsg.getText();
                    if (text != null && text.length() > 500) {
                        text = text.substring(0, 500) + "...";
                    }
                    log.info("  最终回复: {}", text);
                }
                log.info("================================================================");
                return new AgentCommand(List.of(), UpdatePolicy.APPEND);
            }
        };
    }

    /**
     * 获取本次 Agent 执行的总 ReAct 步数
     *
     * @return 总步数
     */
    public int getTotalSteps() {
        return stepCounter.get();
    }

    /**
     * 获取本次 Agent 执行的模型调用总次数
     *
     * @return 总模型调用次数
     */
    public int getTotalModelCalls() {
        return modelCallCounter.get();
    }
}