## ZionChat v0.31.63

- ZiCode 聊天页接入真实执行链：发送任务后不再仅 mock 回复，会调用 Orchestrator 执行工具计划并回写执行摘要。
- 工具调用可视化升级：聊天区新增实时执行态、`Tool Calls` 历史卡片、`Workflow Runs` 记录卡片，便于追踪每一步。
- `policy.*` 工具正式纳入 Dispatcher：
  - 新增 `policy.get_toolspec`
  - 新增 `policy.check_risk`
  - 两者均进入统一可审计调用记录。
- 重构 `ZiCodeAgentOrchestrator`：
  - 修复 workflow 轮询过快超时问题，改为长轮询等待完成
  - 自愈流程改为通过 `policy.check_risk` 工具评估补丁风险
  - 运行状态会持续写入 `ZiCodeRunRecord`。
- 继续保持约束：ZiCode 模块禁止本地 shell，仍以 GitHub API/Actions 为唯一执行路径。
