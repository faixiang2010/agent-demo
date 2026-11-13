# Milvus RAG 示例

`RagDemo.java` 手动创建模型和客户端，不启动 Spring Boot。
参考：https://java2ai.com/integration/rag/retrieval-augmented-generation
启动后通过终端菜单选择操作：

- `1`：调用 `importKnowledge()`，抓取官网、分块并入库/更新，缺失的数据库与集合自动创建。
- `2`：调用 `queryKnowledge()`，输入问题后检索已有知识并生成回答，不抓取网页、不写入知识、不创建数据库或集合。数据库或集合不存在时提示先入库。
- `0`：主菜单中退出程序；连续查询中返回主菜单。
- 连续查询：选择 2 后可自行输入任意问题，回答后继续提示输入下一题；输入 `exit` 直接退出程序。空问题提示重新输入，查询失败也可继续查询，输入流结束时退出。
- 入库操作完成或失败后返回主菜单。

启动及退出不连接 Milvus、不调用模型、不执行入库。

## 配置

- 使用 JDK 17；本项目不兼容 JDK 8。
- 服务地址：`<your-milvus-host>:19530`，默认无认证、无 TLS。
- 数据库：`DEEPSEEK`；集合：`deepseek_home_knowledge_v1`，首次运行自动创建数据库、集合和索引并加载。
- 聊天模型：`qwen-plus`；向量模型：`text-embedding-v3`，1024 维。
- Key 已从 Demo1 复制并直接写入 `RagDemo.DASHSCOPE_API_KEY`，无需环境变量。
- 索引采用 `FLAT` 精确检索，相似度使用 `COSINE`，适用于少量示例数据。

Milvus 连接、数据库、集合和官网分块配置位于 application.yml 的 rag 节点。独立 main 使用 YamlPropertySourceLoader 显式读取，无需启动 Spring Boot。使用普通 Milvus store 依赖，不引入自动配置。
可选环境变量：`MILVUS_HOST`、`MILVUS_PORT`。数据库与集合名称在 YAML 中修改。
如启用认证，同时填写 `MILVUS_USERNAME` / `MILVUS_PASSWORD`；TLS 使用 `MILVUS_SSL=true`。
数据库不存在时程序自动创建；更换向量模型或维度时使用新集合并重新向量化。

## 部署与运行

使用你提供的官方发布文件：

```bash
wget https://github.com/milvus-io/milvus/releases/download/v3.0.2/milvus-standalone-docker-compose.yaml -O docker-compose.yaml
docker compose up -d
docker compose ps
curl -f http://localhost:9091/healthz
```

已核对发布文件：客户端端口为 19530，健康检查端口为 9091。
MinIO 的 9000/9001 端口及账号不是 Milvus 客户端配置。
服务就绪且本机能够访问 <your-milvus-host>:19530 后，IDE 刷新 Maven 依赖并运行 RagDemo.main。
选择 2 后在终端输入问题，例如：DeepSeek的核心产品线有哪些？或：DeepSeek有哪些公有云 Agent 服务？
程序在入库前检查 Milvus 健康状态；尚未部署完成时可能返回连接错误或未就绪提示。

项目 pom 排除了 other/**，普通 Maven 编译不验证该目录。PowerShell 单独编译运行：

```powershell
$env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' -q dependency:build-classpath '-Dmdep.outputFile=target/rag-classpath.txt'
$ragClasspath = (Get-Content 'target/rag-classpath.txt' -Raw).Trim()
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $ragClasspath -d target/rag-demo-classes src/main/java/other/RagDemo.java
& "$env:JAVA_HOME\bin\java.exe" -cp "target/rag-demo-classes;src/main/resources;$ragClasspath" other.RagDemo
```

检索阈值 0.50，最多 3 条。控制台展示来源、分数、原文与回答。
单独展示检索和 Advisor 检索各调用一次向量模型；空上下文时要求模型说明无法回答。
仅选择 1 时抓取 https://www.deepseek.com/ 的首页正文，去除脚本、样式和导航，按 800 字符切块、重叠 100 字符。每块记录来源、标题、块序号和抓取时间，正文快照位于 target/deepseek-home-knowledge.txt。只抓取指定首页，不递归导入外部链接。
Spring AI 1.1.2 Milvus add 使用 insert；示例先删除该 URL 的旧分块再重新入库，避免重复插入及过期分块残留。
只操作 DEEPSEEK 数据库的官网知识集合内相同 source 的文档，不删除数据库、集合或其他来源的文档，请单进程运行。
真实业务应另行设计导入更新流程，长文档需先切分成块。

仅验证抓取和分块可运行：在启动参数中传 --preview，程序不会连接 Milvus 或调用模型。

2026-10-08 已完成端到端验证：创建 DEEPSEEK 数据库并向 deepseek_home_knowledge_v1 写入 8 个官网分块，Milvus count(*) 查询确认数量为 8，相似度检索和 qwen-plus 问答成功。
