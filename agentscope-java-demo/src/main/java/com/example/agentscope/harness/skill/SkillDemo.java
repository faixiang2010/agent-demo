package com.example.agentscope.harness.skill;

import io.agentscope.harness.agent.HarnessAgent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static com.example.agentscope.harness.DemoSupport.*;

/** Skill 是可复用的流程说明与参考文件，本例仍然只有一个 Agent。 */
public class SkillDemo {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !"--auto".equals(args[0])))
            throw new IllegalArgumentException("用法：SkillDemo [--auto]");
        var storage = storage("skill");
        // 从 resources 复制教学技能到 workspace/skills，框架会自动发现，无需手动注册工具。
        for (String file : new String[]{"SKILL.md", "references/rules.md"}) {
            String resource = "/harness-skills/java-null-review/" + file;
            try (InputStream input = SkillDemo.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("缺少技能资源：" + resource);
                write(storage.workspace().resolve("skills/java-null-review/" + file),
                        new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        var context = context();
        try (HarnessAgent agent = localBuilder("skill-teacher", model(), storage)
                .disableSubagents().disableDynamicSubagents()
                .sysPrompt("你是中文 Java 教学助手，称呼用户为老大。任务匹配技能时先用"
                        + " load_skill_through_path 加载技能，再按技能要求工作。")
                .build()) { // 不关闭工作区技能发现。
            System.out.println("老大，技能目录：" + storage.workspace().resolve("skills/java-null-review"));
            Trace trace = ask(agent, "请使用 java-null-review 技能审查下面代码，先加载技能并读取它的参考规则，"
                    + "按技能规定格式回答：\nString name = map.get(\"name\").toString();", context);
            trace.requireTool("load_skill_through_path");
            // 参考文件也可通过 load_skill_through_path 读取，不强制模型只能选 read_file。
            if (!trace.text.toString().contains("NULL-REVIEW-7319"))
                throw new IllegalStateException("回答没有参考规则编号，不能证明完成了参考资料读取。");
            System.out.println("[验证通过] 加载技能详情并读取参考规则，仍由同一个 Agent 完成审查。");
            if (args.length == 0) chat(agent, context);
        }
    }
}
