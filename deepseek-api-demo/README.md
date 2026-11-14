# DeepSeek API 测试台

一个用于本地调试 DeepSeek API 的可视化测试工具。通过浏览器选择接口、填写参数、编辑请求 JSON，并查看普通或流式响应，无需手工拼接 URL 和请求体。

## 功能概览

- **对话补全**：基础对话、思考模式、多轮对话、JSON 输出、工具调用、对话前缀续写。
- **多模态与补全**：视觉理解、FIM 补全。
- **其他接口**：模型与余额查询、Responses、Files、网页搜索/抓取、Anthropic 兼容端点。
- **流式调试**：支持 OpenAI SSE、Responses 事件流和 Anthropic 事件流，可随时停止请求。
- **结果查看**：对话与原始 JSON 视图、HTTP 状态、耗时、Token 用量及网络交互详情。
- **元数据驱动**：接口定义和示例集中在 `src/main/resources/api-definitions.json`。

## 技术栈

- Java 21
- Spring Boot 3.3.5 + WebFlux
- Maven
- 原生 HTML / CSS / JavaScript

前端不需要 Node.js、npm 或单独的构建步骤，静态资源由 Spring Boot 直接提供。

## 环境准备

请提前安装并配置：

1. **JDK 21**
2. **Maven 3.8+**
3. **DeepSeek API Key**：仅在真实调用 DeepSeek API 时需要，启动应用本身不需要。

可在 PowerShell 中检查环境：

```powershell
java -version
mvn -version
```

如果尚未配置环境变量，可先按实际安装目录设置：

```powershell
$env:JAVA_HOME = "<JDK 21 安装目录>"
$env:Path = "$env:JAVA_HOME\bin;<Maven 安装目录>\bin;$env:Path"
```

## 启动项目

以下命令均在项目根目录 `deepseek-api-demo` 下执行。

### 方式一：Maven 开发模式启动

```powershell
mvn spring-boot:run
```

看到类似 `Netty started on port 8080` 的日志后，打开：

[http://localhost:8080](http://localhost:8080)

停止应用时，在终端按 `Ctrl+C`。

### 方式二：打包后运行 JAR

```powershell
mvn clean package
java -jar target/deepseek-api-demo-1.0.0.jar
```

然后打开 [http://localhost:8080](http://localhost:8080)。

### 修改端口

默认端口配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080
```

也可以不修改文件，在启动 JAR 时临时指定端口：

```powershell
java -jar target/deepseek-api-demo-1.0.0.jar --server.port=8081
```

此时访问 `http://localhost:8081`。

## 使用方式

### 1. 配置 API 地址和密钥

页面顶部提供两个配置项：

- **Base URL**：默认 `https://api.deepseek.com`，通常无需修改。
- **API Key**：填写自己的 DeepSeek API Key，不要把真实密钥写入源码或提交到 Git。

点击 **保存** 可将配置保留在当前浏览器中；点击 **测试连通** 会通过后端调用模型列表接口，检查 Base URL 和 API Key 是否可用。

> **安全提示**：点击“保存”后，API Key 会保存在当前浏览器的 `localStorage`（键名 `deepseek_key`）中，不会写入项目配置文件。公共或共享电脑使用完毕后，请清空 API Key 输入框并再次点击“保存”，或清除该站点的浏览器数据。建议仅在本机或可信网络中使用本测试台。

### 2. 选择接口

1. 在顶部主题栏选择接口分类。
2. 在左侧接口列表选择具体场景。
3. 查看接口说明、方法、请求地址和注意事项。

接口自带示例请求。修改快捷参数后，对应值会同步到请求体 JSON；也可以直接编辑 JSON，支持嵌套对象和文档中的其他字段。

### 3. 编辑并发送请求

- 点击 **填入示例**：恢复当前接口的内置示例。
- 点击 **格式化**：校验并格式化请求体 JSON。
- 开启 **流式输出**：对支持流式的接口实时查看响应增量。
- 点击 **调用**，或按 **Ctrl+Enter**：发送请求。
- 请求过程中点击 **停止**：中止当前调用。

真实调用会请求 DeepSeek 服务并可能产生 API 费用。文件删除等具有副作用的示例，请在确认参数后再调用。

### 4. 查看响应

右侧结果区支持：

- **对话 / JSON**：在友好对话视图和原始响应之间切换。
- **状态与耗时**：查看 HTTP 状态码及请求耗时。
- **Token 用量**：查看接口返回的 usage 信息。
- **网络**：查看请求 URL、脱敏请求头、请求参数、响应头、响应参数和 Trace ID。
- **复制 / 清空**：复制当前结果或清空结果区。

## 对话补全示例说明

项目示例已按 DeepSeek 对话补全与思考模式文档配置：

- 基础对话显式设置 `thinking.type=disabled`，使 `temperature` 等采样参数生效。
- 思考模式支持 `reasoning_effort=high/max`，并分别展示 `reasoning_content` 和 `content`。
- 思考模式下，`temperature`、`top_p`、`presence_penalty`、`frequency_penalty` 不生效。
- 无工具调用时，历史 `reasoning_content` 无需参与上下文拼接。
- 发生工具调用时，后续所有轮次必须完整回传 assistant 的 `reasoning_content` 及必要消息字段，否则 API 会返回 400。

参考文档：

- [对话补全 API](https://api-docs.deepseek.com/zh-cn/api/create-chat-completion)
- [思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)

## 常见问题

### 启动时提示端口被占用

修改 `application.yml` 中的 `server.port`，或使用 `--server.port=<新端口>` 启动。

### 页面可以打开，但调用提示 API Key 错误

检查顶部 Base URL 和 API Key，然后点击“测试连通”。页面无需密钥即可浏览，但真实 API 调用必须提供有效密钥。

### 点击调用后提示 JSON 格式错误

点击“格式化”定位并修正请求体，确保 JSON 使用双引号，且不存在多余逗号或注释。

### 修改接口定义后页面没有变化

接口定义由后端在启动时加载。修改 `api-definitions.json` 后需要重启应用；如果通过 JAR 运行，还需要重新执行 `mvn clean package`。

### 思考模式下采样参数没有作用

这是 DeepSeek API 的预期行为，并非页面故障。请使用思考模式示例中的 `reasoning_effort` 控制推理强度。

## 项目结构

```text
deepseek-api-demo/
├── pom.xml                              # Java 21、Spring Boot 与 WebFlux 配置
├── README.md                            # 启动和使用说明
├── PRODUCT.md                           # 产品定位与能力说明
└── src/main/
    ├── java/com/demo/deepseek/           # 控制器、代理服务与接口定义模型
    └── resources/
        ├── application.yml               # 服务端口等配置
        ├── api-definitions.json          # 接口元数据、示例参数与提示
        └── static/                       # 原生前端页面、样式与脚本
```

## 开发说明

- 新增或调整接口示例时，优先修改 `api-definitions.json`，通用页面会自动加载定义。
- 后端代理负责转发请求并处理 SSE，不需要为普通参数新增前端字段解析逻辑。
- 不要提交真实 API Key、用户上传的私密文件或包含敏感信息的响应内容。
