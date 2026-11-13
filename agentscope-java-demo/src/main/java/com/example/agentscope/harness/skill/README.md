# Skill 入门

参考：[Skill 文档](https://java.agentscope.io/v2/zh/docs/harness/skill)。使用项目现有 2.0.4。

本例是一位 Java 助手加载“空指针审查技能”，按技能规定的流程和格式分析代码。
**仍然只有一个 Agent。**Skill 是流程说明与参考资料，不是新建了另一个 Agent。

```text
src/main/resources/harness-skills/java-null-review/
├── SKILL.md                 名称、何时使用、处理步骤、输出格式
└── references/rules.md      空指针检查规则及规则编号
```

启动时将这两份教学资源复制到本轮 `workspace/skills/java-null-review/`。
框架自动发现技能；模型先看到描述，需要时调用 `load_skill_through_path` 加载详情，
再通过文件工具读取参考资料。参考资料也可以由 `load_skill_through_path` 加载，不只限于 `read_file`。

```powershell
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.skill.SkillDemo'
# 只看自动演示：
& .\scripts\run-demo.ps1 -MainClass 'com.example.agentscope.harness.skill.SkillDemo' -DemoArgs '--auto'
```

自动任务是检查 `map.get("name").toString()`。
观察技能加载工具、回答开头“【空指针审查技能】”、三个小节以及参考规则编号 `NULL-REVIEW-7319`。
代码检查加载工具事件和规则编号，避免只看模型是否声称使用了技能。
默认演示后可继续输入其他 Java 代码，`exit` 或 `0` 退出。

想修改技能，就编辑 `src/main/resources/harness-skills/java-null-review/SKILL.md` 或参考文件，重新运行。
每轮目录是 `.agentscope/harness-skill/run-<UUID>/`，不覆盖其他示例的技能。
没有接入 Git 技能市场、自学习或脚本执行，也没有把技能正文直接塞进用户问题。
Shell 和子 Agent 在本例关闭，只保留主题所需的文件工具及工作区技能加载。
模型配置与 UTF-8/JDK17 设置见 [运行指南](../../../../../../../../RUNNING.md)。
