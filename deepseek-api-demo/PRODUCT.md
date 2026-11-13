# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- 主要用户：软件工程师（自己及团队），从事源代码交付项目。
- 场景：日常调试 DeepSeek API 的各原子接口——验证请求/响应格式、排查参数问题、对比流式与非流式输出、测试视觉理解。
- 工作：在一个界面内点选接口 → 动态填参 → 调用 → 实时查看结果，无需逐个记忆 URL、方法与参数结构。

## Product Purpose

一个 DeepSeek API 全接口测试台。让开发者通过浏览器界面覆盖 DeepSeek 文档中的全部原子接口，动态输入参数，并以非流式或实时流式的方式查看结果。API Key 在界面上配置。

成功标准：无需离开界面、无需查文档，即可验证任意 DeepSeek 接口的行为。

## Positioning

- **全接口覆盖**：文档中所有原子接口均已实现，含对话补全、FIM 补全、Responses、Files、网页搜索/抓取、Anthropic 兼容端点。
- **实时流式**：兼容三种流式格式的实时输出——OpenAI SSE（`data: [DONE]` 结尾）、Responses 语义事件流、Anthropic 事件流。
- **按场景分类**：接口按主题/场景组织（基础信息、对话补全、多模态、补全、网页检索、Responses、文件管理、Anthropic 兼容），配示例与提示，降低上手门槛。

## Operating Context

- 本地运行（`http://localhost:8080`），个人开发环境。
- 后端以代理方式转发到 `https://api.deepseek.com`，规避浏览器 CORS 与 SSE 复杂度。
- API Key 在界面顶部配置，仅经内存/请求头传递。
- 后端接口元数据集中在 `src/main/resources/api-definitions.json`。

## Capabilities and Constraints

- **技术栈固定**：Java 21 + Spring Boot 3.3.5 WebFlux + 原生 HTML/CSS/JS 单页前端（零构建、零 npm 依赖）。前端静态资源打包进 Spring Boot 的 `static/` 目录。
- **构建环境固定**：Maven 使用 `D:\GreenSoft\apache-maven-3.9.11`，JDK 使用 `D:\GreenSoft\java` 下的版本（当前 JDK 21）。Python 使用系统自带。
- **接口元数据驱动**：接口的路径、方法、参数、示例、分类、提示均集中在 `api-definitions.json`，新增接口无需改动前端代码。
- **Key 不落盘**：API Key 仅经请求头传递到后端再转发，不写入磁盘、不写入日志。
- **UTF-8 全链路**：后端响应明确 `Content-Type: application/json;charset=UTF-8`，中文全程无乱码。
- **视觉理解**：`deepseek-flash` 支持图片输入，提供 base64 内联、外部 URL、Files `file_id` 三种方式；内置本地示例图 `static/img/vision-sample.png`（不依赖外网）。
- **模型限制**：视觉仅 `deepseek-flash` 支持（`deepseek-v4-pro` 为纯文本）；FIM 补全与对话前缀续写需 `/beta` 前缀且仅 `deepseek-v4-pro`。

## Evidence on Hand

- 已用真实 API Key 完成 29 项功能回归（含流式、思考模式、JSON 输出、工具调用、视觉理解、文件上传/查询/删除、Search/Fetch、Anthropic 兼容），全部通过。
- 内置示例图 `src/main/resources/static/img/vision-sample.png`（本地生成，21KB）。
- 覆盖 25 个场景、17 个方法+路径端点。

## Product Principles

1. **全接口覆盖优先**：文档中的原子接口一个不落。
2. **元数据驱动**：新增接口只改配置，不改前端逻辑。
3. **开箱即用**：零构建、零依赖，启动即用。
4. **安全默认**：API Key 不落盘、不写日志。
5. **中文全链路**：界面与数据全程中文、UTF-8。

## Accessibility & Inclusion

- 界面与文档为中文，全链路 UTF-8。
- 键盘焦点环、文本对比度满足 WCAG AA（已通过 impeccable 机械检测，0 项问题）。
