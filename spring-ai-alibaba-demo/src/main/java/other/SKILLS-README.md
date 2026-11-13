# Agent Framework Skills 调用示例

参考：https://java2ai.com/docs/frameworks/agent-framework/tutorials/skills/

## 入口与技能文件

- Java 入口：`other.SkillCallDemo.main`。
- 技能文件：`src/main/resources/skills/deepseek-company-qa/SKILL.md`。
- 模型：`qwen-plus`，Key 与 Demo1 一致，直接配置在示例代码中。
- 使用项目现有 Agent Framework 1.1.2.0 API，无需新增依赖。

运行时使用 `ClasspathSkillRegistry` 发现技能，`SkillsAgentHook` 将技能名称和描述注入系统提示，
并注册 `read_skill`。模型遇到DeepSeek问题时先读取完整 SKILL.md，然后遵循技能步骤。

通过 `groupedTools` 将 `search_deepseek_knowledge` 与 `deepseek-company-qa` 绑定。
工具不会在初始化时直接注册到 Agent.tools，只有模型读取对应 Skill 后才向模型披露。
工具查询现有 Milvus 知识，返回来源、抓取时间、原文与相似度，模型按技能要求组织回答。

该 SKILL.md 属于你的 Java 应用，示例运行时加载；不需要安装到 Codex 的技能目录。
此示例直接绑定 Java 检索工具，不需要先启动 MCP Server。

## 运行

IDE 使用 JDK 17，直接运行 `SkillCallDemo.main`。
在终端输入问题，可连续提问，输入 `exit` 退出。示例不配置聊天记忆，每次提供完整问题。

例如：`DeepSeek的核心产品线有哪些？分别提供什么能力？`

控制台会显示真实调用顺序：

```text
发现 Skill：deepseek-company-qa
[Skill 调用链] read_skill，参数：{"skill_name":"deepseek-company-qa"}
[Skill 调用链] search_deepseek_knowledge，参数：{"question":"..."}
Agent 回答：
核心回答 / 依据说明 / 资料来源
```

查询依赖 `application.yml` 中 `rag` 配置和现有 Milvus 知识库：
`<your-milvus-host>:19530`，数据库 `DEEPSEEK`，集合 `deepseek_home_knowledge_v1`。
如果尚未入库，先运行 RagDemo 并选择 1。Skill 示例只检索，不抓取官网或重新入库。

项目 pom 排除了 other/**，PowerShell 单独编译运行：

```powershell
$env:JAVA_HOME = 'D:\GreenSoft\java\jdk-17.0.14'
& 'D:\GreenSoft\apache-maven-3.9.11\bin\mvn.cmd' -q dependency:build-classpath '-Dmdep.outputFile=target/skill-classpath.txt'
$skillClasspath = (Get-Content 'target/skill-classpath.txt' -Raw).Trim()
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $skillClasspath -d target/skill-demo-classes src/main/java/other/RagDemo.java src/main/java/other/SkillCallDemo.java
& "$env:JAVA_HOME\bin\java.exe" -Dfile.encoding=UTF-8 -cp "target/skill-demo-classes;src/main/resources;$skillClasspath" other.SkillCallDemo
```

客户端传入 `--verify` 执行示例问题，并断言模型实际调用过 `read_skill` 和绑定的检索工具。
其他程序参数作为单次问题，回答后退出。不传参数则进入连续输入模式。

2026-10-08 已实际验证：技能发现、read_skill、工具动态披露、真实 Milvus 检索和千问回答均成功，进程正常退出。
