# NEXUS // 深空任务控制台

基于 [tinyjs](https://tinyjs.app) 的桌面应用示例：**模拟登录 + 左侧菜单 / 右侧内容的后台式界面**，视觉为深空 HUD 风格（青蓝辉光、扫描线、星空粒子），数据来自后端真实系统遥测 + 部分模拟数据。

- 窗口：`1360 × 860`，可缩放，记忆窗口尺寸
- 运行时：tinyjs ≥ 0.73.0（txiki.js 26.11.0）
- 依赖：**零 npm 依赖**，纯 HTML / CSS / JavaScript

---

## 功能

### 登录

- 账号非空、访问密钥 ≥ 6 位即可进入（纯前端演示，不做真实鉴权）
- 预填演示账号：`commander` / `orbit-2049`
- 登录时播放三段启动序列，成功后 wipe 过渡进入控制台

### 四个内容页

| 页面 | 内容 |
|------|------|
| 总览 | CPU / 内存 / 在线节点 / 下行带宽 KPI、双线吞吐图表、CPU 与内存环形仪表、16 核负载条、任务队列、事件流 |
| 系统监控 | 主机档案（主机名 / 平台 / 架构 / 处理器 / 线程数 / 运行时长 / 负载 / 运行时版本 / PID）、4 个节点集群状态、CPU 核心阵列、网络接口 |
| 运行日志 | 实时日志流，按 ALL / INFO / WARN / ALERT 过滤，自动滚动开关，导出 `.log`，清空 |
| 终端设置 | 操作员档案、界面动效 / 紧凑布局 / 实时遥测开关（持久化）、会话信息、断开连接 |

### 交互与细节

- 图表悬停显示时间点读数，KPI 迷你趋势线
- 侧栏可折叠为图标栏（`76px`），折叠状态在本次会话内保持
- 应用菜单：`视图`（四页切换）、`账户`（断开连接）
- 关闭「界面动效」会停止 Canvas 星空动画并启用 `reduce-motion`
- 开启「紧凑布局」压缩间距，一屏显示更多数据

---

## 数据来源

**真实遥测**（由 tinyjs 后端 `tjs` API 采集）：

- CPU 总占用与每核占用（按 `tjs.system.cpus` 的 times 差分计算）
- 内存占用、网络接口列表、主机名、平台、架构、CPU 型号
- 系统运行时长、平均负载、tinyjs / QuickJS 版本、进程 PID

**模拟数据**（仅用于演示界面效果）：

- 4 个轨道节点的状态、延迟与负载
- 任务队列进度、事件流日志
- 上下行带宽曲线与部分 KPI 数值

> 这是 UI 演示项目，模拟数据不代表真实系统状态。

---

## 目录结构

```
tinyjs-demo/
├── tinyjs.json            # 应用清单：名称、窗口尺寸、版本、最小运行时版本
├── jsconfig.json          # 编辑器类型提示（配合 types/）
├── src/
│   ├── backend/
│   │   └── main.js        # 后端：系统采集、遥测 tick、页面可调用的 api
│   └── frontend/
│       ├── index.html     # 登录页 + 控制台结构 + SVG 图标 sprite
│       ├── style.css      # 视觉系统（配色、HUD 面板、响应式、紧凑模式）
│       └── app.js         # 页面逻辑：登录、遥测渲染、图表、日志、设置
├── assets/
│   └── icon.png           # 应用图标
├── types/
│   ├── tiny.d.ts          # 页面侧 tiny.* API 类型声明
│   └── tjs.d.ts           # 后端 tjs.* 类型声明
└── dist/                  # 构建产物（已忽略）
```

---

## 运行

需要先安装 tinyjs CLI（未加入 PATH 时可直接用安装目录下的可执行文件）。

```powershell
# 开发模式（热更新）
tinyjs dev

# Windows：CLI 未加入 PATH 时
& "$env:LOCALAPPDATA\tinyjs\tinyjs.cmd" dev
```

## 打包

```powershell
tinyjs build
```

产物输出到 `dist/`：

```
dist/nexus-console.exe   # 应用本体（单文件，免安装）
dist/launcher.exe        # 启动器
```

`dist/`、`.build/` 已在 `.gitignore` 中忽略。

---

## 后端 API

页面通过 `await tiny.api.call(name, params)` 调用 `src/backend/main.js` 中 `export const api` 暴露的方法。

| 方法 | 参数 | 返回 |
|------|------|------|
| `login` | `{ account, password }` | `{ user, since, token }`，校验失败抛错 |
| `logout` | — | `true` |
| `bootstrap` | — | 系统信息、历史曲线、队列、节点、日志、每核负载、设置、运行时长、会话 |
| `saveSettings` | `{ settings }` | `{ settings, stored }`，写入 `app.store` |
| `exportLogs` | `{ path, lines }` | `true`，写文件 |

后端每 `1600ms` 通过 `app.push('telemetry', payload)` 推送一次遥测；页面用 `tiny.api.on('telemetry', cb)` 订阅。

### 持久化

设置项保存在 `app.store` 的 `settings` 键：

```json
{ "motion": true, "compact": false, "telemetry": true }
```

---

## 已知限制

- 登录仅为界面演示，任意账号 + 6 位以上密钥都能通过
- 节点、队列、事件流、带宽均为模拟数据
- 尺寸按 `1360 × 860` 设计；窗口更小时内容区域会自动出现滚动
