# 本地服务启动指南（SAA Admin + Demo）

> 本文档说明如何启动 **Spring AI Alibaba Admin 后端 + 前端** 以及 **agent-demo 中的 spring-ai-alibaba-demo**。
> Admin 仓库与 `agent-demo` **同级存放**，Demo 在 `agent-demo/spring-ai-alibaba-demo/` 下。

---

## 一、总览（按此顺序启动，各开一个独立终端窗口）

| 顺序 | 服务 | 目录 | 端口 | 运行环境 |
|---|---|---|---|---|
| 0 | 基础设施（MySQL/Redis/ES/Nacos/RocketMQ） | 远程机器 | — | 确认远程机器已开机 |
| 1 | SAA Admin 后端 | `../spring-ai-alibaba-admin` | 8080 | JDK 17 + Maven |
| 2 | Admin 前端 | `../spring-ai-alibaba-admin/frontend/packages/main` | 8000 | Node 20 |
| 3 | spring-ai-alibaba-demo | `./spring-ai-alibaba-demo` | 8081 | JDK 17 + Maven |

> 💡 路径说明：`./` 表示 `agent-demo/` 当前目录，`../` 表示 `agent-demo/` 的上一级目录。

为什么是这个顺序：前端 8000 通过代理调用后端 8080，所以先后端再前端；demo 只依赖远程基础设施，顺序任意。

固定工具路径（请替换为你本地的实际安装路径）：

```powershell
# 这些变量在"运行前"章节的命令里会用到
$env:JAVA_HOME   = "<你的 JDK 17 根目录>"
$env:MAVEN_HOME  = "<你的 Maven 根目录>"
$env:NODE20_HOME = "<你的 Node 20 根目录>"
$env:PATH = "$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"
```

---

## 二、PowerShell 与 CMD 的关键区别（先看这个）

| 操作 | PowerShell | CMD |
|---|---|---|
| 切换目录 | `cd d:\path` | `cd /d d:\path` |
| 设置环境变量 | `$env:JAVA_HOME='D:\xxx'` | `set JAVA_HOME=D:\xxx` |
| PATH 前置目录 | `$env:PATH="D:\xxx;$env:PATH"` | `set PATH=D:\xxx;%PATH%` |
| 防止中文乱码 | `chcp 65001`（可选） | `chcp 65001`（建议先执行） |
| 命令连接 | PowerShell 5 不支持 `&&`，**分行写** | 支持 `&&` |

注意：用上面方式设置的环境变量只对**当前窗口**有效，关闭即失效；每个新窗口都要重新设置。

---

## 三、启动命令（每个服务一个窗口，启动后窗口不要关）

### 服务 1：SAA Admin 后端（8080）

PowerShell：

```powershell
chcp 65001
cd ..\spring-ai-alibaba-admin
$env:JAVA_HOME = "<你的 JDK 17 根目录>"
& "$env:MAVEN_HOME\bin\mvn.cmd" -pl spring-ai-alibaba-admin-server-start spring-boot:run
```

CMD：

```cmd
chcp 65001
cd /d ..\spring-ai-alibaba-admin
set JAVA_HOME=<你的 JDK 17 根目录>
mvn -pl spring-ai-alibaba-admin-server-start spring-boot:run
```

要点：必须在仓库**根目录**执行（不要 cd 进模块目录，否则 `${revision}` 无法解析）；看到 `Started SaaStudioAdmin` 即成功。

### 服务 2：Admin 前端（8000）

PowerShell：

```powershell
cd ..\spring-ai-alibaba-admin\frontend\packages\main
$env:PATH = "$env:NODE20_HOME;$env:PATH"
node -v
npm run dev
```

CMD：

```cmd
cd /d ..\spring-ai-alibaba-admin\frontend\packages\main
set PATH=<你的 Node 20 根目录>;%PATH%
node -v
npm run dev
```

要点：`node -v` 必须显示 `v20.x`；看到 `App listening at: http://localhost:8000` 即成功。

### 服务 3：spring-ai-alibaba-demo（8081）

PowerShell（在 `agent-demo/` 根目录下执行）：

```powershell
chcp 65001
cd .\spring-ai-alibaba-demo
$env:JAVA_HOME = "<你的 JDK 17 根目录>"
& "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run
```

CMD：

```cmd
chcp 65001
cd /d .\spring-ai-alibaba-demo
set JAVA_HOME=<你的 JDK 17 根目录>
mvn spring-boot:run
```

---

## 四、验证

| 验证项 | 地址 / 方式 |
|---|---|
| 后端健康 | http://localhost:8080/actuator/health → `"status":"UP"` |
| 管理界面（推荐） | http://localhost:8000/app ，账号 `saa` / `123456` |
| 后端内置旧界面 | http://localhost:8080/index.html |
| Swagger 文档 | http://localhost:8080/swagger-ui.html |
| demo 接口 | http://localhost:8081/ai/chat?prompt=你好 |

---

## 五、停止服务

在对应终端窗口按 `Ctrl + C` 即可。若窗口已关但端口仍被占用：

```powershell
# PowerShell：查占用 8080 的进程并结束（改端口号即可处理 8000/8081）
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -Expand OwningProcess -Unique | Stop-Process -Force
```

```cmd
:: CMD：先查 PID（最后一列），再结束
netstat -ano | findstr :8080
taskkill /PID <查到的PID> /F
```

---

## 六、首次运行 / 环境被清理后的准备（只需做一次）

```powershell
# 1) Admin 后端：在仓库根目录安装多模块到本地仓库
cd ..\spring-ai-alibaba-admin
$env:JAVA_HOME = "<你的 JDK 17 根目录>"
& "$env:MAVEN_HOME\bin\mvn.cmd" -DskipTests install

# 2) 前端：在 frontend 根目录安装依赖（全程使用 Node 20）
cd ..\spring-ai-alibaba-admin\frontend
$env:PATH = "$env:NODE20_HOME;$env:PATH"
npm run re-install
```

CMD 版本把 `$env:XXX = "..."` 换成 `set XXX=...` 即可。
