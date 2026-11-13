package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小验证 Demo：引入 agentscope-admin-spring-boot-starter，
 * 自动装配出一个 ReActAgent（DashScope 模型，占位 Key 即可启动），
 * 然后观察 admin starter 暴露的全部 HTTP 表面。
 *
 * 启动后打开 http://localhost:8087/ 查看索引页。
 */
@SpringBootApplication
public class AdminDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminDemoApplication.class, args);
    }

    /**
     * 极简索引页：把这个应用所有可访问的 HTTP 入口列出来，
     * 便于一眼看清"admin starter 只有 JSON 接口，没有聊天界面"。
     */
    @RestController
    static class IndexController {

        @GetMapping("/")
        public String index() {
            return """
                    <!doctype html><html lang="zh"><head><meta charset="utf-8">
                    <title>agentscope-admin starter 验证 Demo</title>
                    <style>body{font-family:Segoe UI,Microsoft YaHei,sans-serif;max-width:860px;margin:40px auto;padding:0 20px;line-height:1.7}
                    h1{font-size:22px} code{background:#f3f3f3;padding:2px 6px;border-radius:4px}
                    li{margin:6px 0} .tag{display:inline-block;font-size:12px;padding:1px 8px;border-radius:10px;margin-left:8px}
                    .json{background:#e8f4ea;color:#1d7a36} .note{background:#fff7e6;border:1px solid #ffd591;padding:12px 16px;border-radius:8px}
                    a{color:#1668dc}</style></head><body>
                    <h1>agentscope-admin-spring-boot-starter 验证 Demo</h1>
                    <p>本应用只引入了 admin starter + 一个自动装配的 ReActAgent。下面是它实际暴露的
                    <b>全部</b> HTTP 入口（本索引页除外，由 Demo 自己提供，不是 starter 的）：</p>
                    <h3>控制面 —— Actuator JSON 端点（11 个 agentscope-*）</h3>
                    <ul>
                      <li><a href="/actuator/agentscope-status">/actuator/agentscope-status</a><span class="tag json">JSON</span> 运行状态总览</li>
                      <li><a href="/actuator/agentscope-agents">/actuator/agentscope-agents</a><span class="tag json">JSON</span> 已注册 Agent 清单</li>
                      <li><a href="/actuator/agentscope-tools">/actuator/agentscope-tools</a><span class="tag json">JSON</span> 工具清单</li>
                      <li><a href="/actuator/agentscope-models">/actuator/agentscope-models</a><span class="tag json">JSON</span> 模型清单</li>
                      <li><a href="/actuator/agentscope-commands">/actuator/agentscope-commands</a><span class="tag json">JSON</span> 管理命令目录</li>
                      <li><a href="/actuator/agentscope-usage">/actuator/agentscope-usage</a><span class="tag json">JSON</span> Token 用量统计</li>
                      <li><a href="/actuator/agentscope-permissions">/actuator/agentscope-permissions</a><span class="tag json">JSON</span> 权限/HITL 状态</li>
                      <li><a href="/actuator/agentscope-subagents">/actuator/agentscope-subagents</a><span class="tag json">JSON</span> 子 Agent 清单</li>
                      <li><a href="/actuator/agentscope-doctor">/actuator/agentscope-doctor</a><span class="tag json">JSON</span> 健康诊断</li>
                      <li><code>/actuator/agentscope-drain</code><span class="tag json">POST</span> 排空（写操作）</li>
                      <li><code>/actuator/agentscope-shutdown</code><span class="tag json">POST</span> 关停（写操作）</li>
                    </ul>
                    <h3>数据面 —— 会话运维 REST API</h3>
                    <ul>
                      <li><code>POST /v1/admin/sessions/{id}/compact</code> 压缩会话上下文</li>
                      <li><code>POST /v1/admin/sessions/{id}/abort</code> 中止会话</li>
                      <li>……（完整目录见 <a href="/actuator/agentscope-commands">agentscope-commands</a>）</li>
                    </ul>
                    <p class="note">⚠️ 注意：整个 admin starter <b>不提供任何聊天页面 / 前端资源</b>。
                    访问不存在的 UI 路径（例如 <a href="/chat">/chat</a>、<a href="/admin">/admin</a>）都会得到 404。
                    它是给运维系统调用的 <b>JSON 管理接口集</b>，不是 FAQ 所说的"开箱即用 Web 应用"。</p>
                    </body></html>
                    """;
        }
    }
}
