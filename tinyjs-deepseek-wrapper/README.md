# DeepSeek Wrapper

基于 [tinyjs](https://tinyjs.app) 的**站点包装器（Site Wrapper）**示例：把 `https://chat.deepseek.com/` 直接包成桌面应用。网站 UI、登录态、对话历史全部沿用 DeepSeek 官方页面，只加了原生窗口壳、菜单、通知和导航分流。

- 窗口：`1280 × 800`，最小 `960 × 640`
- 运行时：tinyjs ≥ 0.73.0（txiki.js 26.11.0）
- 依赖：**零 npm 依赖**

---

## 它是什么

站点包装器是 tinyjs 支持的两种应用模式之一：

| 模式 | 主窗口内容 | 典型场景 |
|------|-----------|---------|
| 本地页面（如 `tinyjs-demo`） | 自己写的 HTML / CSS / JS | 需要完全自定义 UI 的应用 |
| **站点包装器（本项目）** | 远程 URL（`"url"` 字段） | 把已有 Web 应用快速包装成桌面端 |

好处是不用维护一份前端代码，网站更新时自动生效；代价是网站的 JavaScript 会在你的应用里运行，必须用 `api` gate 限制它能调用哪些原生 API。

---

## 安全设计（api gate）

[tinyjs.json](./tinyjs.json) 的 `api` 字段按 **origin 隔离权限**：

| Origin | 权限 |
|--------|------|
| `https://chat.deepseek.com` + `*.deepseek.com` 子域 | `wrapper` 预设（通知、徽章、find-in-page、窗口控制、文件存储、系统通知等） |
| **其他所有 origin** | 仅窗口控制（`win.close / minimize / maximize / zoom / startDrag`） |

这意味着：
- DeepSeek 自己的页面可以正常发通知、用 find-in-page、保存偏好
- 页面加载的第三方脚本（广告、分析、CDN 资源）就算被 XSS 劫持，也调不到敏感 API
- 导航到外部站点时自动降级为"只有关窗口按钮"的安全态

---

## 导航策略

后端 [src/main.js](./src/main.js) 中的 `onNavigate` 对所有导航做分流：

| 链接 | 行为 |
|------|------|
| `*.deepseek.com` 内部 | ✅ 应用内加载 |
| 外部 http(s) | 🔀 系统默认浏览器打开 |
| `mailto:` / `tel:` 等特殊协议 | 📧 交给系统处理 |

---

## 目录结构

```
tinyjs-deepseek-wrapper/
├── tinyjs.json            # 应用清单：url、api gate、窗口尺寸
├── src/
│   └── main.js            # 后端：onNavigate / onDownload / onWindowOpen
├── assets/
│   └── icon.png           # 应用图标（从 chat.deepseek.com 自动抓取）
└── dist/                  # 构建产物（已忽略）
```

站点包装器模式**不需要** `src/frontend/` 目录——主窗口直接加载 `tinyjs.json` 里 `"url"` 字段指定的网址。

---

## 运行

```powershell
# 开发模式
tinyjs dev

# 打包
tinyjs build
```

构建产物在 `dist/`：

```
dist/deepseek-wrapper.exe   # 应用本体（单文件，免安装）
dist/launcher.exe           # 启动器
```

首次启动会打开 DeepSeek 登录页，登录后 cookie 持久化在 WebView2 profile 中，下次启动自动保持登录。

---

## 配置说明

### 改网址

把 `tinyjs.json` 的 `"url"` 改成任意网址即可。`tinyjs wrap https://example.com` 可以从零生成一个新的包装器项目骨架。

### 加权限

如果某个功能不工作（比如 find-in-page 没反应），用 `TINYJS_DEBUG=1 tinyjs dev` 启动会在终端打印每次 api 调用被 gate 拒绝的 origin 和方法名，然后在 `origins` 对应的数组里加进去。

### 弹窗行为

`"popups": "window"` 让新窗口在同一应用内打开（而不是系统浏览器）。DeepSeek 登录流程会用到弹窗，保持这个配置。

### 下载行为

`"downloads": "ask"` 让每次下载弹出保存对话框。改成 `"auto"` 则直接下载到默认目录。

---

## 参考

- [站点包装器官方文档](https://tinyjs.app/docs/site-wrappers)
- [api gate 规则](https://tinyjs.app/docs/site-wrappers#api-gate)
- [wrapper 预设权限清单](https://tinyjs.app/docs/site-wrappers#wrapper-preset)
