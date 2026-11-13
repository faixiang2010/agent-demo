# AgentExample 启动说明

## 项目信息

- **类名**: `other.AgentExample`
- **功能**: 构建一个 ReAct 智能体（天气预报助手），支持通过命令行参数切换 DashScope（通义千问）或 DeepSeek 模型

## 启动命令

在项目根目录下执行：

### 使用 DashScope（通义千问）— 默认

```bash
mvn compile exec:java --define exec.mainClass=other.AgentExample
```

或显式指定：

```bash
mvn compile exec:java --define exec.mainClass=other.AgentExample --define exec.args=dashscope
```

### 使用 DeepSeek

```bash
mvn compile exec:java --define exec.mainClass=other.AgentExample --define exec.args=deepseek
```

> **注意**：`exec:java` 不会自动编译项目，需要搭配 `compile` 阶段一起执行，确保代码已编译后再运行。

## 模型配置说明

| 提供商 | 模型 | API 地址 | 温度系数 |
|--------|------|----------|----------|
| dashscope | qwen-plus | DashScope 默认 | 0.7 |
| deepseek | deepseek-chat | https://api.deepseek.com | 0.7 |

## 智能体参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 智能体名称 | weather_agent | 天气预报助手 |
| 系统提示词 | 你是一个有用的天气预报助手。 | 定义智能体角色 |

## 前置条件

1. JDK 17+
2. Maven 已安装并配置
3. DashScope API Key 已配置（代码中已内置）
4. DeepSeek API Key 需在代码中替换 `your-deepseek-api-key` 为真实密钥