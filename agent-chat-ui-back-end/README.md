# Agent Chat UI Backend

LangGraph 后端服务器，配合 [agent-chat-ui](../agent-chat-ui) 前端使用。

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置环境变量

创建 `.env` 文件：

```env
DEEPSEEK_API_KEY=sk-your-api-key-here
DEEPSEEK_MODEL=deepseek-flash
MODEL_NAME=deepseek-flash
PORT=2024
TEMPERATURE=0.7
```

### 3. 启动后端服务

```bash
# Windows
set PYTHONUTF8=1
python -m langgraph_cli dev --port 2024

# 或使用启动脚本
start.bat
```

服务将在 `http://localhost:2024` 启动。

### 4. 启动前端

```bash
cd ../agent-chat-ui
pnpm dev
```

## 项目结构

```
agent-chat-ui-back-end/
├── app_graph.py      # LangGraph Agent 定义（含工具）
├── langgraph.json    # LangGraph CLI 配置
├── start.bat         # Windows 启动脚本
├── requirements.txt  # Python 依赖
├── .env              # 环境变量配置
└── README.md
```

## 内置工具

| 工具 | 说明 |
|------|------|
| `get_current_time` | 获取当前日期和时间 |
| `calculator` | 数学表达式计算 |
| `search_knowledge` | 知识库搜索 |

## 环境变量

| 变量 | 必需 | 说明 | 默认值 |
|------|------|------|--------|
| `DEEPSEEK_API_KEY` | ✅ | DeepSeek API 密钥 | - |
| `DEEPSEEK_MODEL` | ❌ | 模型名称 | `deepseek-flash` |
| `PORT` | ❌ | 服务端口 | `2024` |
| `TEMPERATURE` | ❌ | 生成温度 | `0.7` |

## 前端配置

前端 `.env` 需配置：

```env
NEXT_PUBLIC_API_URL=http://localhost:2024
NEXT_PUBLIC_ASSISTANT_ID=agent
```

## 故障排查

### Windows 编码错误

启动前设置 UTF-8：

```powershell
$env:PYTHONUTF8="1"
python -m langgraph_cli dev --port 2024
```

### 端口被占用

修改 `.env` 中的 `PORT` 和启动命令的 `--port` 参数。

## 许可证

MIT License