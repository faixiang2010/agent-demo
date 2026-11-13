package other;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/** 本地 stdio MCP Server，由 McpClientDemo 启动；不要在协议 stdin 中输入菜单内容。 */
public class McpStdioServerDemo {

    public static void main(String[] args) throws InterruptedException {
        // 保存原始 stdout 给协议使用；普通日志和依赖中的 println 全部改走 stderr。
        var protocolOutput = System.out;
        System.setOut(System.err);
        var transport = new StdioServerTransportProvider(McpJsonMapper.getDefault(), System.in, protocolOutput);
        var timeTool = FunctionToolCallback.builder("get_current_time", (TimeRequest input) -> {
            String zone = input.zoneId() == null || input.zoneId().isBlank() ? "Asia/Shanghai" : input.zoneId();
            System.err.println("[MCP工具调用] get_current_time，时区：" + zone);
            var now = ZonedDateTime.now(ZoneId.of(zone));
            return "当前时间：" + now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    + "，星期：" + now.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale.CHINA));
        }).description("获取指定时区的当前真实日期和时间。中国时间使用 Asia/Shanghai。")
                .inputType(TimeRequest.class).build();
        var knowledgeTool = FunctionToolCallback.builder("search_deepseek_knowledge", (KnowledgeRequest input) -> {
            System.err.println("[MCP工具调用] search_deepseek_knowledge，问题：" + input.question());
            try {
                var documents = RagDemo.retrieveKnowledge(input.question());
                if (documents.isEmpty()) {
                    return "知识库没有找到相关资料，请明确告知用户无法从现有知识中回答。";
                }
                var result = new StringBuilder();
                for (var document : documents) {
                    result.append("来源：").append(document.getMetadata().get("source"))
                            .append("\n网页抓取时间：").append(document.getMetadata().get("fetched_at"))
                            .append("\n相似度：").append(document.getScore())
                            .append("\n正文：").append(document.getText()).append("\n\n");
                }
                return result.toString();
            } catch (Exception e) {
                throw new IllegalStateException("DeepSeek知识检索失败：" + e.getMessage(), e);
            }
        }).description("检索 DeepSeek 官网知识库，获取公司简介、DeepSeek-V3/R1/R2 模型、DeepResearch 深度研究、DeepSeek Code、API 平台等资料。只读检索，不执行入库。question 必须是明确的检索问题。")
                .inputType(KnowledgeRequest.class).build();

        var server = McpServer.sync(transport).serverInfo("deepseek-stdio-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(McpToolUtils.toSyncToolSpecifications(timeTool, knowledgeTool)).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "mcp-server-shutdown"));
        System.err.println("DeepSeek MCP stdio Server 已启动，等待客户端连接。");
        new CountDownLatch(1).await();
    }

    public record TimeRequest(String zoneId) { }

    public record KnowledgeRequest(String question) { }
}
