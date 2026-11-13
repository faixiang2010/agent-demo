# 研究笔记 01：主流 AI 智能体框架官方文档与最新版本动态

调研范围：LangChain/LangGraph、AutoGen/AG2、CrewAI、LlamaIndex、Microsoft Semantic Kernel / Microsoft Agent Framework、OpenAI Agents SDK/Swarm。
数据口径：版本号与发布日期抓取自官方文档 / PyPI（截至 2026-09 发布版）。

---

## 1. LangChain / LangGraph（LangChain Inc.）

**框架定位（官方原文要点）**
- LangGraph 是**低层（low-level）的编排框架与运行时（orchestration framework and runtime）**，用于构建、管理与部署**长时间运行、有状态（long-running, stateful）**的智能体。
- 核心能力：**在同一张图中把确定性的手写步骤与 LLM 驱动的智能体步骤混合（mix deterministic and agentic steps）**，对"AI 在何处、如何介入"拥有细粒度控制。
- 核心收益：持久化执行（durable execution / persistence）、事件流（streaming）、人类介入（human-in-the-loop）、记忆（短期工作记忆 + 跨会话长期记忆）、生产级部署。
- 官方产品分层（"frameworks, runtimes, and harnesses"叙事，反映 2026 年趋势）：
  - **Deep Agents** = agent harness（在 LangGraph 之上提供规划、子智能体、文件系统工具、上下文管理）
  - **LangChain** = agent framework（模型、工具、agent loop 的抽象与集成）
  - **LangGraph** = orchestration runtime（持久执行、流式、HITL、持久化）
  - **LangSmith** = 平台（追踪、评估、提示词、部署）；另有 **LangSmith Engine**（检测 trace 问题并提 PR）与 **LangSmith Fleet**（无代码 agent builder）。

**最新版本动态**
- `langgraph` **1.2.12**，发布于 **2026-09-21**；License: MIT；Python >= 3.10。（来源 PyPI）
- 重要里程碑：LangGraph **1.0.0 正式版 2025-10-17**，进入 Production/Stable；此后进入 1.x 快速迭代（1.1.x→1.2.x）。
- 商业背书：官方称被 Klarna、Uber、J.P. Morgan、Replit、Elastic 等用于规模化生产。
- 说明：`langgraph` 与 `langchain` 解耦，可独立使用；LangChain 的 agent 构建在 LangGraph 之上以获得持久执行等能力。

**来源链接**
- https://docs.langchain.com/oss/python/langgraph/overview
- https://pypi.org/project/langgraph/
- https://langchain-ai.github.io/langgraph/ （已 301 跳转至 docs.langchain.com，印证文档平台迁移）

---

## 2. AutoGen / AG2（ag2ai 社区，源自微软 AutoGen）

**框架定位（官方原文要点）**
- AG2 自我定位为 **"The Open-Source AgentOS"（开源智能体操作系统）**，"Build production-ready AI agents in minutes, not months"。
- 提供：简洁直观语法、内置对话模式（conversation patterns）、无缝人机协作、多智能体编排。
- **关键架构变更（2026 年最重要的框架分叉事件）**：
  - 自 **AG2 v1.0 起**，**协议驱动（protocol-driven）框架成为顶层包**，导入名为 `ag2`。
  - 经典框架（`ConversableAgent`、`GroupChat`、`import autogen`、`register_function`、`OAI_CONFIG_LIST` 等）**迁移至独立仓库 `ag2ai/ag2-classic`**（文档 classic.docs.ag2.ai），仍维护且可安装（`pip install ag2-classic`）。
  - `pip install ag2` **不再包含** `autogen` 导入名与经典 agent 类；**AG2 v1.0 不是 Classic 的 drop-in 升级**（agent 模型、编排、导入均变化）。
- 新架构核心概念：`Agent`（`ask()` 返回 `AgentReply`，`reply.body` 取文本，全程 async）、`@tool` 工具、Human-in-the-loop（`context.input()` + `hitl_hook`）、**Network（Hub + 类型化 channels）**替代旧 GroupChat/swarm/nested-chat、**agent harness（knowledge + compact 上下文压缩）**。

**最新版本动态**
- `ag2` **1.1.0**，发布于 **2026-09-24**；License: Apache-2.0；Python >= 3.10。（来源 PyPI）
- 关键节点：**AG2 1.0.0 发布 2026-07-27**（首个"协议驱动"大版本）；此前 0.x 系列长期迭代（0.7 于 2025-01，0.9 于 2025-04）。
- 维护主体：由 Chi Wang、Qingyun Wu 等社区志愿者维护（"maintained by a dynamic group of volunteers from several organizations"）。

**来源链接**
- https://docs.ag2.ai/latest/
- https://pypi.org/project/ag2/
- https://classic.docs.ag2.ai （AG2 Classic 文档）

---

## 3. CrewAI

**框架定位（官方原文要点）**
- **独立的（standalone）、轻量、高速的多智能体编排框架**，"built entirely from scratch—completely independent of LangChain or other agent frameworks"。
- 双范式：
  - **Crews**：面向自主性（autonomy）与协作智能的角色扮演 agent 团队；
  - **Flows**：面向生产的事件驱动工作流（精细控制、状态管理、条件分支，原生支持 Crews）。
- 内置 guardrails、memory、knowledge、observability；企业版为 **CrewAI AMP Suite**（统一控制平面、实时可观测、RBAC、on-prem/cloud 部署）。
- 官方强调性能：称相较 LangGraph 在特定 QA 任务快 **5.76x**（自述，需谨慎看待）。

**最新版本动态**
- `crewai` **1.15.22**，发布于 **2026-09-16**；License: MIT；Python >=3.10,<3.14。（来源 PyPI）
- 关键节点：**CrewAI 1.0.0 发布 2025-10-20**；2026 年高频迭代（1.15.x，几乎每周多版本）。
- 社区规模：官方称 **超过 100,000 名**开发者通过 learn.crewai.com 课程认证；已提供 `a2a`、`mcp` 等 extras（体现 A2A/MCP 协议化趋势）。

**来源链接**
- https://docs.crewai.com/
- https://pypi.org/project/crewai/

---

## 4. LlamaIndex

**框架定位（官方原文要点，含重要战略转向）**
- 定位为 **"the leading framework for building LLM-powered agents over your data with LLMs and workflows"**（面向数据的 agent 框架）。
- 核心概念：Agents（由工具增强的知识工作者）+ **Workflows（事件驱动，官方称"比其他基于图的方案灵活得多"）**；RAG 作为工具之一。
- **2026 战略重点转移（README 明确）**：公司重心转向**文档解析/抽取（LlamaParse）**；官方判断 **"agents are the new consumers of documents"（智能体是文档的新消费者）**。
  - LlamaParse 平台 = Parse（agentic OCR，130+ 格式）、LlamaAgents（部署式文档 agent）、Extract（结构化抽取）、Index（RAG）。
  - OSS 框架（llama-index-core + 300+ 集成包）仍作为开放工具集维护，但不再是主攻方向。

**最新版本动态**
- `llama-index` **0.14.25**，发布于 **2026-09-21**；License: MIT；Python >=3.10。（来源 PyPI）
- 版本节奏：2026 年维持 0.14.x 小步迭代；核心包为 `llama-index-core`，采用"核心 + 可插拔集成"架构。

**来源链接**
- https://docs.llamaindex.ai/en/stable/
- https://pypi.org/project/llama-index/

---

## 5. Microsoft Semantic Kernel → Microsoft Agent Framework（MAF）

**框架定位（官方原文要点，重大更名/演进展）**
- Semantic Kernel 原定位：**轻量级开源开发套件**，让开发者用 **C#、Python、Java** 轻松构建 AI agent 并集成最新模型，充当"中间件"，面向企业级解决方案（telemetry、hooks & filters、1.0+ 稳定 API 承诺）。
- **关键变更**：官方公告 **"Semantic Kernel is now Microsoft Agent Framework!"**——**Microsoft Agent Framework (MAF) 是 Semantic Kernel 的企业级后继者（enterprise-ready successor）**。
  - **MAF 已达 v1.0 生产就绪**：稳定 API、长期支持承诺、企业级多智能体编排、多模型提供商支持，并通过 **A2A 与 MCP 实现跨运行时互操作（cross-runtime interoperability）**。
  - 官方提供 Semantic Kernel → Agent Framework 迁移指南。
- SK 自身能力亮点：多智能体系统与编排（GroupChatOrchestration、RoundRobinGroupChatManager）、插件生态（Python/OpenAPI/MCP）、Process Framework（业务流程建模）、多模态（文本/视觉/音频）、丰富 LLM 与向量库支持。

**最新版本动态**
- `semantic-kernel`（Python SDK）**1.44.1**，发布于 **2026-08-06**；License: MIT；Python >=3.10（同时支持 C#/.NET、Java）。（来源 PyPI）
- 版本节奏：1.39.x（2026-01）→1.44.1（2026-08），稳定迭代；官方明确 SK 正由 MAF 承接。

**来源链接**
- https://learn.microsoft.com/en-us/semantic-kernel/overview/
- https://pypi.org/project/semantic-kernel/

---

## 6. OpenAI Agents SDK / Swarm

**框架定位（官方原文要点）**
- **"production-ready upgrade of our previous experimentation for agents, Swarm"**——即 Swarm 的生产级后继。
- 设计原则：**极少的原语（primitives）、最小抽象**，Python-first。核心原语：**Agents、Agents-as-tools / Handoffs、Guardrails**。
- 主要特性：Agents（含内置循环）、**Sandbox agents（隔离工作区、可恢复会话）**、**Realtime agents（gpt-realtime-2.1 语音、自动打断检测）**、Voice agents（STT→agent→TTS pipeline）、Function tools（自动 schema/Pydantic 校验）、**MCP server tool calling**、**Sessions（持久记忆层）**、Human-in-the-loop、内置 Tracing。
- 模型策略：默认使用 OpenAI **Responses API**；**provider-agnostic**，支持 OpenAI Responses / Chat Completions 及 **100+ 其他 LLM**（含 LiteLLM、any-llm 适配）。

**最新版本动态**
- `openai-agents` **0.22.3**，发布于 **2026-09-17**；License: MIT；Python >=3.10（支持 3.14）。（来源 PyPI）
- 版本节奏：首个 **0.1.0 发布 2025-06-27**；2026 年迭代密集（0.10.x→0.22.x），仍处 0.x。
- 生态：官方维护 JS/TS 版本；extras 覆盖 a2a、mcp、redis、sqlalchemy、temporal、litellm、docker/e2b 等。

**来源链接**
- https://openai.github.io/openai-agents-python/
- https://pypi.org/project/openai-agents/

---

## 横向对比速览（最新版本 / License / 一句话定位）

| 框架 | 最新版本 (发布日期) | License | 一句话定位 |
|---|---|---|---|
| LangGraph | 1.2.12 (2026-09-21) | MIT | 低层有状态智能体编排运行时，确定性+agentic 混合 |
| AG2 | 1.1.0 (2026-09-24) | Apache-2.0 | 协议驱动"AgentOS"，Network(Hub+channels)；Classic 独立维护 |
| CrewAI | 1.15.22 (2026-09-16) | MIT | 独立轻量多智能体框架，Crews(自主)+Flows(事件驱动) |
| LlamaIndex | 0.14.25 (2026-09-21) | MIT | 面向数据的 agent 框架；重心转向 LlamaParse 文档智能 |
| Semantic Kernel / MAF | 1.44.1 (2026-08-06) | MIT | 企业级 SDK（C#/Py/Java），演进为 Microsoft Agent Framework 1.0 |
| OpenAI Agents SDK | 0.22.3 (2026-09-17) | MIT | 极简原语 agent SDK（Swarm 生产级升级），provider-agnostic |

## 本步骤观察到的主要趋势信号（供后续综合分析引用）
1. **1.0 稳定化潮**：LangGraph(2025-10)、CrewAI(2025-10)、AG2(2026-07)、MAF(1.0) 相继进入 1.0/生产就绪。
2. **协议标准化**：MCP 与 A2A 成为跨框架共识（CrewAI、AG2、SK/MAF、OpenAI Agents SDK 均内建/支持）。
3. **架构分化**：出现"分层"叙事（harness / framework / runtime / platform，如 LangChain 栈）；AG2 与 AutoGen 正式分叉。
4. **从"对话编排"转向"运行时+工具+工作区"**：Sandbox agents、durable execution、persistence、memory 成为标配。
5. **定位重构**：厂商向"数据/文档/企业"垂直价值聚焦（LlamaIndex→LlamaParse、SK→MAF）。
