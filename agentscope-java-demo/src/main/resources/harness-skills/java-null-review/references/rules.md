# 审查规则

规则编号：NULL-REVIEW-7319。

- Map 本身可能是 null，调用 get 前需要确认 Map 存在。
- Map.get 在 key 不存在时可以返回 null；对返回值直接调用 toString 会产生空指针异常。
- 示例安全写法：先检查 map，再取出 value；value 为 null 时使用默认字符串。
- 示例：Object value = map == null ? null : map.get("name"); String name = value == null ? "未命名" : value.toString();
- 把“可能发生”与“已经运行并确认发生”区分开；本技能只静态分析。
