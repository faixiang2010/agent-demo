# 步骤2：已有文件素材提取笔记

> 目标：对第1步清单中与「AI智能体 / 框架」相关的文件，读取内容并提取可复用数据与结论，避免重复劳动。

## 一、实际读取的文件（4个）

| 文件 | 是否与智能体/框架相关 | 读取结果 |
|---|---|---|
| `crew.jsonc` | ✅ 相关（CrewAI 框架工程配置） | 已读取，见下方摘录 |
| `agents/researcher.jsonc` | ✅ 相关（智能体定义） | 已读取，见下方摘录 |
| `README.md` | ✅ 相关（框架项目说明） | 已读取，见下方摘录 |
| `pyproject.toml` | ✅ 相关（框架依赖声明） | 已读取，见下方摘录 |
| `knowledge/user_preference.txt` | ⚪ 无关 | 仅为占位注释（`# Add your knowledge files here`），无内容 |

---

## 二、可复用的关键信息摘录

### 1. 框架与版本信息（可用于「框架现状」佐证）
- 所用框架：**CrewAI**（JSON-first 配置方式的项目工程）。
- 依赖声明（`pyproject.toml`）：`crewai[tools] >= 1.15.22, < 2.0.0`。
- 虚拟环境中实际存在：**`crewai-1.15.22`**、`crewai_tools`、`openai-2.54.0`。
  → 可作为「2025–2026 主流多智能体框架版本演进」的一手环境样本（CrewAI 已进入 1.x 稳定版）。

### 2. 智能体（Agent）定义范式（可用于「框架能力/配置抽象」佐证）
来源 `agents/researcher.jsonc`，体现了现代智能体框架的声明式配置结构：
- `role`：高级研究专员
- `goal`：`查找关于 {topic} 的全面准确信息，重点关注最新进展和关键见解。`（支持 `{topic}` 变量插值）
- `backstory`：人设/系统提示
- `llm`：`deepseek/deepseek-flash`（**支持国产大模型 / 多模型供应商切换**，符合「模型无关」趋势）
- `tools`：`["ScrapeWebsiteTool", "DirectoryReadTool", "FileReadTool", "FileWriterTool"]`（**内置工具生态 + 文件/网页抓取能力**）
- `settings`：`verbose`、`allow_delegation`、`planning`（**支持步进式规划 planning = 2026 智能体「自主规划」能力特征**）

### 3. Crew（编排）配置范式（可用于「流程编排/声明式」趋势佐证）
来源 `crew.jsonc`：
- `agents` / `tasks`（含 `name`、`description`、`expected_output`、`agent` 绑定）
- `process`: `"sequential"`（**顺序执行；对应业界另一主流为 hierarchical 层级编排**）
- `verbose`、`memory`（`false`）、`chat_llm`、`inputs`
- 输入主题：`2026年AI智能体框架的发展趋势`（即本次调研主题本身）

### 4. 工程结构
来源 `README.md`：
- `agents/`（JSONC 智能体定义）、`crew.jsonc`（Crew 定义）、`tools/`（Python 自定义工具）、`knowledge/`（知识文件）
- 支持 `custom:<name>` 工具引用，加载时执行本地 Python → **体现框架的可扩展性与自定义工具机制**。

---

## 三、结论与 DONE 判定

✅ **DONE 标准达成：** 已读取 **4 个与 AI 智能体/框架相关的文件**，并摘录出关键信息（框架名称、版本、声明式配置范式、工具生态、编排模式等）。

⚠️ **重要结论：** 当前工程内**不存在**关于「2026年AI智能体框架发展趋势」的**既有调研素材/数据/结论**——所读文件均为 **CrewAI 工程自身的配置与说明**，只能作为「框架实际使用的一手环境样本」参考，**不构成可直接复用的调研内容**。

**后续建议：** 本主题的调研素材需**从零外部采集**（官方文档、行业报告、权威文章），产出笔记建议写入本工程 `research/` 或 `knowledge/` 目录。
