# 本地 stdio MCP Server 与 LLM 调用

参考文档：https://java2ai.com/integration/mcps/mcp-overview/
工具适配：https://java2ai.com/integration/mcps/mcp-helpers/

## 实现

- `McpStdioServerDemo`：MCP Server，使用 Java SDK 的 `StdioServerTransportProvider`。
- `McpClientDemo`：本地客户端，启动 Server 子进程，完成握手和工具发现，通过
  `SyncMcpToolCallbackProvider` 将 MCP 工具交给 `ChatClient`，由 `qwen-plus` 决定调用。
- `get_current_time`：返回指定时区的真实时间，默认中国时间。
- `search_deepseek_knowledge`：只读检索现有DeepSeek Milvus 知识，返回原文、来源与分数。
  Server 不生成最终回答，由客户端的聊天模型根据工具结果回答。

通信流程：用户问题 -> 千问选择工具 -> MCP Client -> 子进程 stdin/stdout JSON-RPC ->
MCP Server 执行工具 -> 工具结果返回 -> 千问回答。

沿用独立 main 示例风格，依赖 `spring-ai-mcp`，不引入 Boot MCP 自动配置。
Server 不监听 HTTP 端口，不需要手动在另一个终端启动。
Server 的 stdout 只写协议消息，普通输出和日志转到 stderr；客户端以 `[MCP Server]` 展示日志。

## 运行

使用 JDK 17，刷新 Maven 依赖后，在 IDE 中直接运行 `McpClientDemo.main`。
在终端输入问题，回答后可继续查询，输入 `exit` 退出。

例如：`现在北京时间几点？DeepSeek的核心产品线及各自能力是什么？`

聊天 Key 已从 Demo1 复制到客户端代码；知识检索复用 RagDemo 的向量模型 Key。
Milvus 的地址、数据库与集合读取 application.yml 的 rag 节点。
知识必须事先通过 RagDemo 入库；知识工具不会抓取官网、创建数据库/集合或写入知识。
时间工具无需 Milvus。
当前每次提问独立，不保存聊天历史。

项目 pom 排除了 other/**，请使用下方命令验证这些独立示例：

```powershell
$env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' -q dependency:build-classpath '-Dmdep.outputFile=target/mcp-classpath.txt'
$mcpClasspath = (Get-Content 'target/mcp-classpath.txt' -Raw).Trim()
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $mcpClasspath -d target/mcp-demo-classes src/main/java/other/RagDemo.java src/main/java/other/McpStdioServerDemo.java src/main/java/other/McpClientDemo.java
& "$env:JAVA_HOME\bin\java.exe" -Dfile.encoding=UTF-8 -cp "target/mcp-demo-classes;src/main/resources;$mcpClasspath" other.McpClientDemo
```

客户端复用当前 JDK，通过 Java 参数文件传递完整 classpath，避免 Windows 命令行长度限制。
运行结束关闭 MCP 客户端及 Server 子进程，并清理参数文件。
Windows 下 SDK 关闭子进程时可能打印 `Process terminated with code 1`，需结合客户端退出码和工具结果判断；
这条日志本身不表示工具调用失败。

## 验证

客户端传入 `--verify`：通过真实 stdio 调用时间与知识工具，不调用聊天模型。
客户端传入其他参数：将参数作为单次问题，交给 LLM 调用工具并回答后退出。
不传参数：进入交互式连续问答。

工具调用在 stderr 输出 `[MCP工具调用]`，可用于确认 LLM 实际通过 MCP 执行了工具。
