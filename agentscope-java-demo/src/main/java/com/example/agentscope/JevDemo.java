package com.example.agentscope;

import io.agentscope.extensions.judge.jev.Answer;
import io.agentscope.extensions.judge.jev.ChoiceAnswer;
import io.agentscope.extensions.judge.jev.ChoiceQuestion;
import io.agentscope.extensions.judge.jev.JevClient;
import io.agentscope.extensions.judge.jev.JevRetryPolicy;
import io.agentscope.extensions.judge.jev.NoulAnswer;
import io.agentscope.extensions.judge.jev.NoulQuestion;
import io.agentscope.extensions.judge.jev.ScoreAnswer;
import io.agentscope.extensions.judge.jev.ScoreQuestion;
import io.agentscope.extensions.judge.jev.SystemOneRequest;
import io.agentscope.extensions.judge.jev.SystemOneResult;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Jev（TypeSafe System One）快速上手示例，对应文档 https://java.agentscope.io/v2/zh/jev。
 *
 * <p>Jev 不是聊天模型：它把一段非结构化输入变成带校准概率/置信度的类型化判定结果，
 * 适合做路由、评分、分类这类低成本预判。本示例覆盖文档中的三种问题类型：
 * <ul>
 *   <li>{@link ChoiceQuestion} 闭集选择：把客服工单路由给正确的团队
 *   <li>{@link NoulQuestion} 是非判断：判断一句话是否包含负面情绪的 P(yes)
 *   <li>{@link ScoreQuestion} 等级评分：对回复质量给出概率加权分数
 * </ul>
 *
 * <p>配置见 {@code application.properties} 的 {@code jev.*} 段；
 * API 密钥优先读环境变量 {@code TYPESAFE_API_KEY}，兜底 {@code JEV_API_KEY}。
 *
 * <p>运行：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.example.agentscope.JevDemo
 * </pre>
 */
public class JevDemo {

    /** jev.* 配置。 */
    private record JevConfig(boolean enabled, String apiKey, String baseUrl, String model,
                             Duration timeout, int maxRetries, Duration initialBackoff) {
    }

    public static void main(String[] args) {
        JevConfig cfg = loadConfig();
        if (!cfg.enabled()) {
            System.out.println("jev.enabled=false，跳过 Jev 示例。"
                    + "请在 application.properties 中把 jev.enabled 改为 true，"
                    + "并配置 jev.api-key（或环境变量 TYPESAFE_API_KEY）后重新运行。");
            return;
        }
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            System.err.println("缺少 Jev API 密钥：请设置环境变量 TYPESAFE_API_KEY"
                    + "，或在 application.properties 中配置 jev.api-key");
            System.exit(1);
        }

        JevClient client = JevClient.builder()
                .apiKey(cfg.apiKey())
                .baseUrl(cfg.baseUrl())
                .model(cfg.model())
                .timeout(cfg.timeout())
                .retryPolicy(new JevRetryPolicy(cfg.maxRetries(), cfg.initialBackoff()))
                .build();

        // 三种问题合并在一次 System One 请求里，每个问题一个 answer key
        SystemOneRequest request = SystemOneRequest.builder()
                .state("我的打款已经失败3天了，客服也一直没回复。")
                .question("team", new ChoiceQuestion(
                        "这个问题应该由哪个团队处理？",
                        orderedMap(Map.of(
                                "billing", "支付与退款",
                                "technical", "缺陷与集成问题",
                                "account", "登录与账号问题"))))
                .question("is_negative", new NoulQuestion(
                        "客户明显很生气。", null))
                .question("urgency", new ScoreQuestion(
                        "评估这个问题需要多快得到回复。",
                        List.<Object>of("低 - 可以等几天",
                                "中 - 今天应该回复",
                                "高 - 客户在亏钱，需立即回复")))
                .build();

        SystemOneResult result = client.systemOneBlocking(request);
        System.out.printf("[model=%s, usage=%s]%n", result.model(), result.usage());

        for (Map.Entry<String, Answer> entry : result.answers().entrySet()) {
            printAnswer(entry.getKey(), entry.getValue());
        }

        // 文档建议的用法：低置信度路由到人工
        ChoiceAnswer routing = (ChoiceAnswer) result.answers().get("team");
        if (routing.confidence() != null && routing.confidence() < 0.75) {
            System.out.println("[路由决策] 置信度不足 0.75，转人工复核");
        } else {
            System.out.println("[路由决策] 自动路由到团队: " + routing.choice());
        }
    }

    /** 按 answer 的实际类型打印，对应文档"支持的问题类型"一节。 */
    private static void printAnswer(String key, Answer answer) {
        if (answer instanceof ChoiceAnswer a) {
            System.out.printf("  %s => choice=%s, confidence=%.2f, probabilities=%s%n",
                    key, a.choice(), a.confidence(), a.probabilities());
        } else if (answer instanceof NoulAnswer a) {
            System.out.printf("  %s => P(yes)=%.2f%n", key, a.noul());
        } else if (answer instanceof ScoreAnswer a) {
            System.out.printf("  %s => score=%.2f, confidence=%.2f, legend=%s, probabilities=%s%n",
                    key, a.score(), a.confidence(), a.legend(), a.probabilities());
        } else {
            System.out.printf("  %s => %s%n", key, answer);
        }
    }

    /** 加载 jev.* 配置：环境变量优先于配置文件。 */
    private static JevConfig loadConfig() {
        Properties props = new Properties();
        try (InputStream in = JevDemo.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }

        boolean enabled = Boolean.parseBoolean(props.getProperty("jev.enabled", "false"));
        String apiKey = resolve(System.getenv("TYPESAFE_API_KEY"),
                resolve(System.getenv("JEV_API_KEY"), props.getProperty("jev.api-key"), null), null);
        String baseUrl = props.getProperty("jev.base-url", JevClient.DEFAULT_BASE_URL);
        String model = props.getProperty("jev.model", JevClient.DEFAULT_MODEL);
        Duration timeout = Duration.ofSeconds(
                Long.parseLong(props.getProperty("jev.timeout-seconds", "5")));
        int maxRetries = Integer.parseInt(props.getProperty("jev.retry.max-retries", "2"));
        Duration backoff = Duration.ofMillis(
                Long.parseLong(props.getProperty("jev.retry.initial-backoff-millis", "500")));

        return new JevConfig(enabled, apiKey, baseUrl, model, timeout, maxRetries, backoff);
    }

    /** 保持选项声明顺序（System One 按声明顺序返回概率）。 */
    private static Map<String, Object> orderedMap(Map<String, String> map) {
        return new LinkedHashMap<>(map);
    }

    private static String resolve(String first, String second, String defaultValue) {
        for (String v : new String[]{first, second}) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return defaultValue;
    }
}
