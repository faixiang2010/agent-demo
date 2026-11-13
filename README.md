# agent-demo

> 个人编写的 AI Agent / Demo / 脚手架项目集合。
> 所有项目均为本地实验性质的快速原型，不含第三方开源仓库的代码副本。

---

## 项目清单

### Java / Maven

| 项目 | 技术栈 | 说明 |
|------|--------|------|
| [admin-demo](./admin-demo/) | Spring Boot 4 + AgentScope 2.0 | 最小 Demo：验证 agentscope-admin-spring-boot-starter 的功能 |
| [agentscope-java-demo](./agentscope-java-demo/) | Maven + AgentScope Harness | AgentScope Java 2.0 QuickStart，对接 DeepSeek OpenAI 兼容接口 |
| [deepseek-api-demo](./deepseek-api-demo/) | Spring Boot 3 + WebFlux | DeepSeek API 全接口覆盖测试台（Web UI 调试工具） |
| [spring-ai-alibaba-demo](./spring-ai-alibaba-demo/) | Spring Boot 3.5 + Spring AI Alibaba 1.1 | ReactAgent Quick Start，集成 MCP、RAG、天气工具调用、Nacos 配置 |

### Python

| 项目 | 技术栈 | 说明 |
|------|--------|------|
| [agent-chat-ui-back-end](./agent-chat-ui-back-end/) | LangGraph Server | LangGraph 后端，配合 agent-chat-ui 前端使用 |
| [crew_demo](./crew_demo/) | CrewAI + uv | CrewAI JSON-first 配置的 Agent 团队示例 |
| [deep-agents-ui-back-end](./deep-agents-ui-back-end/) | LangGraph Server + DeepAgents | 基于 DeepAgents 的 Agent 后端服务 |
| [lang-chain-python](./lang-chain-python/) | LangChain + DeepSeek | LangChain QuickStart Demo（Prompt → LLM → Parser 链） |
| [lang-deepagents-python](./lang-deepagents-python/) | LangGraph + DeepAgents | DeepAgents Python 独立运行示例 |
| [lang-graph-python](./lang-graph-python/) | LangGraph + DeepSeek | LangGraph StateGraph 基础示例（chatbot 节点） |
| [czsc-demo](./czsc-demo/) | Python 单文件 | 量化交易脚本快速验证 |

### Node.js / Electron

| 项目 | 技术栈 | 说明 |
|------|--------|------|
| [lang-graph](./lang-graph/) | Node.js + LangChain.js | LangChain / LangGraph JavaScript 版 HelloWorld 合集（6 个示例） |
| [my-electron-app](./my-electron-app/) | Electron Forge 7 | Electron 基础应用（启用安全 Fuses 配置） |

### PowerShell

| 项目 | 技术栈 | 说明 |
|------|--------|------|
| [c-cleanup](./c-cleanup/) | PowerShell | Windows C 盘保守清理与空间分析脚本（只读扫描 + 白名单清理） |

---

## 运行前说明

大部分项目使用 **DeepSeek** 作为 LLM，需要在 `.env` 或环境变量中配置：

```bash
DEEPSEEK_API_KEY=sk-your-api-key-here
DEEPSEEK_MODEL=deepseek-chat       # 或 deepseek-flash
```

**不要提交 `.env` 文件。** 每个项目的 `.gitignore` 都已配置好过滤规则。

---

## 运行环境

> 各项目 pom.xml / pyproject.toml 会声明所需的具体版本，下面是最低要求。

- **JDK**: 17 / 21（按项目 pom.xml 指定）
- **Maven**: 3.8+
- **Python**: 3.10+
- **Node.js**: 18+（Electron 项目需 20+）

```powershell
# 设置 JDK 环境变量（Windows PowerShell）
$env:JAVA_HOME = "<你的 JDK 根目录>"
$env:PATH = "$env:JAVA_HOME\bin;<你的 Maven 根目录>\bin;$env:PATH"

# 启动 Spring Boot 项目
mvn spring-boot:run
```

---

## 目录结构

```
agent-demo/
├── .gitignore              # 根级过滤（兜底子目录可能遗漏的构建产物）
├── README.md               # 本文件
├── admin-demo/             # Spring Boot + AgentScope Admin 最小 Demo
├── agentscope-java-demo/   # AgentScope Java QuickStart
├── agent-chat-ui-back-end/ # LangGraph 后端
├── c-cleanup/              # PowerShell C盘清理脚本
├── crew_demo/              # CrewAI 示例
├── czsc-demo/              # Python 单文件脚本
├── deep-agents-ui-back-end/# DeepAgents 后端
├── deepseek-api-demo/      # DeepSeek API 测试台
├── lang-chain-python/      # LangChain Demo
├── lang-deepagents-python/ # DeepAgents Python 示例
├── lang-graph/             # LangGraph.js 示例合集
├── lang-graph-python/      # LangGraph Python Demo
├── my-electron-app/        # Electron 基础应用
└── spring-ai-alibaba-demo/ # Spring AI Alibaba Demo
```

---

## 参考的开源 Agent 框架

> `agent-demo` 中的大部分示例都是基于下面这些开源 Agent 框架编写的。它们的**完整源码仓库**与 `agent-demo` 同级存放，方便对照查阅。

### AgentScope（阿里）

| 本地目录 | 官方仓库 | 简介 |
|----------|----------|------|
| `agentscope-java` | [github.com/agentscope-ai/agentscope-java](https://github.com/agentscope-ai/agentscope-java) | AgentScope Java 2.0 主仓库，提供 agentscope-core / harness / extensions / service 等模块，支持模型扩展、可视化调试、MCP 协议和 Agent-to-Agent。 |
| `agentscope-studio` | [github.com/agentscope-ai/agentscope-studio](https://github.com/agentscope-ai/agentscope-studio) | AgentScope 官方可视化调试面板，支持 Agent 工作流可视化、链路回放、Human-in-the-Loop。 |

### Spring AI Alibaba（阿里）

| 本地目录 | 官方仓库 | 简介 |
|----------|----------|------|
| `spring-ai-alibaba-main` | [github.com/alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba) | Spring AI 生态的 Alibaba 云定制版，提供多 Agent 编排（Sequential / Parallel / Routing / Loop）、图式工作流、上下文工程、A2A 协议、Nacos 集成等生产级能力。 |
| `spring-ai-alibaba-admin` | [github.com/alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-admin) | Spring AI Alibaba Admin 平台模块，提供可视化开发、可观测性、MCP 管理。 |
| `AssistantAgent` | [github.com/spring-ai-alibaba/AssistantAgent](https://github.com/spring-ai-alibaba/AssistantAgent) | Spring AI Alibaba 官方出品的通用 Agent 示例仓库。 |

### LangChain / LangGraph（LangChain 官方）

| 本地目录 | 官方仓库 | 简介 |
|----------|----------|------|
| `agent-chat-ui` | [github.com/langchain-ai/agent-chat-ui](https://github.com/langchain-ai/agent-chat-ui) | LangChain 官方的 Agent Chat 前端（Next.js + Tailwind），配合 LangGraph Server 后端使用。 |
| `deep-agents-ui` | [github.com/langchain-ai/deep-agents-ui](https://github.com/langchain-ai/deep-agents-ui) | LangChain 官方 DeepAgents 前端界面。 |

### DSH（DeepSeek Harness）

| 本地目录 | 官方仓库 | 简介 |
|----------|----------|------|
| `deepseek-harness` | [deepseek.com/harness](https://www.deepseek.com/harness) | DeepSeek 出品的 Agent 开发与协作框架，支持插件化 Agent 构建、Session 管理、MCP 工具调用。**已单独提交**。 |
| `dsh-plugin` | （个人 DSH 插件） | 基于 DSH 框架的个人插件开发。**已单独提交**。 |
| `dsh-superagent-prototype-main` | （研究原型） | DeepSeek Harness Team 开源的 proactive agent 研究原型，监听 GitHub/飞书事件、自动创建工单执行。**未在 GitHub 上公开维护**。 |

### 其他

| 本地目录 | 官方仓库 | 简介 |
|----------|----------|------|
| `biz-buddy` | [gitee.com/zl3624/biz-buddy](https://gitee.com/zl3624/biz-buddy) | Gitee 上的开源 Agent 助手项目。 |
| `cc-haha` | [github.com/NanmiCoder/cc-haha](https://github.com/NanmiCoder/cc-haha) | 基于 Electron 的开源 AI 助手客户端（支持 Bun 运行时）。 |
| `xuya-island` | （个人项目） | 个人编写的独立项目。**已单独提交**。 |

### 框架 × Demo 对应关系

```
agent-demo 中的 Demo              基于的开源框架
─────────────────────────────────────────────────
agentscope-java-demo       ←  AgentScope Java 2.0（agentscope-java）
admin-demo                 ←  AgentScope Admin Starter
spring-ai-alibaba-demo     ←  Spring AI Alibaba（spring-ai-alibaba-main）
agent-chat-ui-back-end     ←  LangGraph + agent-chat-ui（LangChain 前端）
deep-agents-ui-back-end    ←  LangGraph + DeepAgents + deep-agents-ui
lang-chain-python          ←  LangChain Python
lang-graph-python          ←  LangGraph Python
lang-graph                 ←  LangChain.js / LangGraph.js
crew_demo                  ←  CrewAI（独立 Python Agent 框架）
my-electron-app            ←  Electron Forge（无 Agent 依赖，纯 UI 脚手架）
deepseek-api-demo          ←  DeepSeek API 直调（无框架依赖）
czsc-demo                  ←  Python（量化，无 Agent 依赖）
c-cleanup                  ←  PowerShell（运维脚本，无 Agent 依赖）
```

---

## 注意事项

- 所有项目都是**脚手架 / 原型**，代码可以自由修改。
- 构建产物（`target/`、`node_modules/`、`__pycache__/` 等）已在 `.gitignore` 中过滤。
- 包含真实 API Key 的 `.env` 文件**严禁提交**。
- 如需将某个 Demo 独立仓库，直接把对应子目录单独 `git init` 即可。
