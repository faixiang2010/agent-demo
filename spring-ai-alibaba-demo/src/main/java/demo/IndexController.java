package demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页导航控制器
 * <p>
 * 提供一个 HTML 首页，展示项目说明、接口列表、模型切换方式和版本信息。
 * 方便开发者在浏览器中直接查看和测试接口。
 */
@RestController
public class IndexController {

    /**
     * 返回项目首页 HTML
     * <p>
     * 包含以下内容：
     * <ul>
     *   <li>模型提供者说明（DashScope / DeepSeek）</li>
     *   <li>可用接口列表及快速访问链接</li>
     *   <li>模型切换方式说明</li>
     *   <li>ReAct 日志说明</li>
     *   <li>API Key 配置说明</li>
     *   <li>版本信息</li>
     * </ul>
     *
     * @return 完整的 HTML 页面字符串
     */
    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public String index() {
        return "<!doctype html><html lang='zh'><head><meta charset='utf-8'>"
                + "<title>Spring AI Alibaba 演示</title>"
                + "<style>body{font-family:'Microsoft YaHei',sans-serif;max-width:860px;margin:40px auto;padding:0 20px;line-height:1.7}"
                + "h1{font-size:22px}h3{font-size:18px}code{background:#f3f3f3;padding:2px 6px;border-radius:4px}"
                + "li{margin:6px 0}.tag{display:inline-block;font-size:12px;padding:1px 8px;border-radius:10px;margin-left:8px}"
                + ".get{background:#e8f4ea;color:#1d7a36}.ds{background:#e8eaf6;color:#283593}.cc{background:#fce4ec;color:#c62828}"
                + "a{color:#1668dc}"
                + "pre{background:#f3f3f3;padding:12px;border-radius:8px;overflow-x:auto}</style></head><body>"
                + "<h1>Spring AI Alibaba 演示</h1>"
                + "<p>基于 <a href='https://java2ai.com/docs/quick-start'>Spring AI Alibaba 快速入门</a> 构建 | 支持多模型切换 | ReAct 步骤日志</p>"
                + "<h3>模型提供者</h3><ul>"
                + "<li><span class='tag get'>默认</span> <strong>DashScope</strong> — 通义千问 qwen-plus</li>"
                + "<li><span class='tag ds'>可选</span> <strong>DeepSeek</strong> — deepseek-chat（OpenAI 兼容）</li>"
                + "</ul>"
                + "<h3>接口列表</h3><ul>"
                + "<li><a href='/ai/chat?message=你好'>/ai/chat?message=你好</a> <span class='tag get'>GET</span> ChatModel 对话（默认 DashScope）</li>"
                + "<li><a href='/ai/chat?message=你好&provider=deepseek'>/ai/chat?message=你好&provider=deepseek</a> <span class='tag ds'>GET</span> ChatModel 对话（DeepSeek）</li>"
                + "<li><a href='/ai/agent?message=北京今天天气怎么样'>/ai/agent?message=北京今天天气怎么样</a> <span class='tag get'>GET</span> ReactAgent 智能体（默认 DashScope）</li>"
                + "<li><a href='/ai/agent?message=北京今天天气怎么样&provider=deepseek'>/ai/agent?message=...&provider=deepseek</a> <span class='tag ds'>GET</span> ReactAgent 智能体（DeepSeek）</li>"
                + "<li><a href='/ai/models'>/ai/models</a> <span class='tag get'>GET</span> 查看可用模型</li>"
                + "<li><a href='/actuator/health'>/actuator/health</a> <span class='tag get'>GET</span> 健康检查</li>"
                + "</ul>"
                + "<h3>ChatClient 演示 <span style='font-size:13px;font-weight:normal;color:#888'>(/ai/chat-client)</span></h3><ul>"
                + "<li><a href='/ai/chat-client/basic?userInput=你好，请介绍一下你自己'>/ai/chat-client/basic?userInput=你好，请介绍一下你自己</a> <span class='tag cc'>GET</span> 基础对话 — prompt().user().call().content()</li>"
                + "<li><a href='/ai/chat-client/stream?userInput=用三句话介绍杭州西湖'>/ai/chat-client/stream?userInput=用三句话介绍杭州西湖</a> <span class='tag cc'>GET</span> 流式输出 — stream().content()</li>"
                + "<li><a href='/ai/chat-client/system-prompt?userInput=什么是Spring AI'>/ai/chat-client/system-prompt?userInput=什么是Spring AI</a> <span class='tag cc'>GET</span> 系统提示词 — prompt().system()</li>"
                + "<li><a href='/ai/chat-client/full-response?userInput=用一句话介绍微服务架构'>/ai/chat-client/full-response?userInput=用一句话介绍微服务架构</a> <span class='tag cc'>GET</span> 完整响应 — 含Token用量</li>"
                + "<li><a href='/ai/chat-client/tool?userInput=北京今天天气怎么样'>/ai/chat-client/tool?userInput=北京今天天气怎么样</a> <span class='tag cc'>GET</span> 工具调用 — .tools(weatherTool)</li>"
                + "<li><a href='/ai/chat-client/multi-turn?userInput=那它和for循环有什么区别'>/ai/chat-client/multi-turn?userInput=那它和for循环有什么区别</a> <span class='tag cc'>GET</span> 多轮对话 — .messages()</li>"
                + "<li><a href='/ai/chat-client/info'>/ai/chat-client/info</a> <span class='tag cc'>GET</span> 接口说明</li>"
                + "</ul>"
                + "<h3>切换方式</h3>"
                + "<p>在接口 URL 中添加 <code>provider=dashscope</code> 或 <code>provider=deepseek</code> 参数即可切换模型</p>"
                + "<h3>ReAct 日志</h3>"
                + "<p>智能体接口的执行步骤会输出到控制台，包含：思考（模型推理）→ 行动（工具调用）→ 观察（工具返回）→ 最终回复</p>"
                + "<h3>配置说明</h3>"
                + "<p>DashScope Key: 环境变量 <code>AI_DASHSCOPE_API_KEY</code> 或 application.yml</p>"
                + "<p>DeepSeek Key: 环境变量 <code>DEEPSEEK_API_KEY</code> 或 application.yml</p>"
                + "<h3>版本信息</h3><ul>"
                + "<li>Spring AI Alibaba: <code>1.1.2.0</code></li>"
                + "<li>Spring AI: <code>1.1.2</code></li>"
                + "<li>Spring Boot: <code>3.5.3</code></li>"
                + "</ul></body></html>";
    }
}