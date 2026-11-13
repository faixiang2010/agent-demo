# Deep Agents UI Backend

基于 **LangGraph Server + DeepAgents** 构建的 AI Agent 后端服务。

## 技术栈

| 层 | 技术 |
|---|---|
| 服务框架 | LangGraph Server（`langgraph dev`） |
| Agent 框架 | DeepAgents（`create_deep_agent` + `FilesystemBackend`） |
| LLM | DeepSeek（`deepseek-flash`） |

## 项目结构

```
├── app/
│   ├── __init__.py
│   └── graphs.py       Agent 定义（3 个 Agent）
├── workspace/           FilesystemBackend 工作目录
├── langgraph.json       LangGraph Server 配置
├── requirements.txt
├── start.bat
├── .env.example         配置模板
└── .gitignore
```

## 快速启动

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置

复制 `.env.example` 为 `.env`，填入 DeepSeek API Key：

```bash
cp .env.example .env
```

`.env` 内容：

```ini
DEEPSEEK_API_KEY=sk-your-key-here
DEEPSEEK_MODEL=deepseek-flash
AGENT_WORK_DIR=./workspace
```

### 3. 启动

> **中文 Windows 注意**：`langgraph-api` 内读文件默认用系统编码（GBK），需强制 UTF-8，否则启动报 `UnicodeDecodeError`。以下方式均已内置该修复。

```bash
# 方式一：双击 start.bat（自动处理编码）

# 方式二：PowerShell
$env:PYTHONUTF8=1; langgraph dev --port 2024

# 方式三：PowerShell（langgraph 不在 PATH 时）
$env:PYTHONUTF8=1; python -m langgraph_cli dev --port 2024 --no-browser
```

### 4. 验证

- 健康检查：`GET http://localhost:2024/ok` → `{"ok": true}`
- API 文档：`http://localhost:2024/docs`

## 预置 Agent

| graph_id | 说明 |
|---|---|
| `agent` | 默认 Agent（→ `general-assistant`） |
| `general-assistant` | 通用助手 |
| `code-assistant` | 代码开发助手 |
| `data-analyst` | 数据分析助手 |

前端 `deep-agents-ui` 的 `assistantId` 配置为 `agent` 即可直接对接。