## ZionChat v0.31.64

- ZiCode 聊天新增 `Direct Tool` 模式：
  - 支持在输入框直接使用 `/tool <tool_name> <json>` 调用任意工具
  - 结果会即时回显到聊天流，并写入可审计调用历史。
- 会话欢迎提示补充了 `/tool` 用法示例，方便快速验证 `repo/actions/pages/policy` 全部能力。
- 持续保留上一版本能力：
  - 真实 Orchestrator 执行链
  - Tool Calls / Workflow Runs 可视化追踪
  - `policy.get_toolspec` 与 `policy.check_risk` 审计记录
  - ZiCode 模块禁用本地 shell，仅使用 GitHub 驱动执行。
