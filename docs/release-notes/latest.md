## ZionChat v0.31.62

- 新增 `ZiCodePolicyService`：
  - `policy.get_toolspec`：返回可调用工具与约束
  - `policy.check_risk`：按路径/改动规模输出 `low | medium | high` 风险分级
  - 拦截本地 shell 类工具调用（ZiCode 模块内）
- 新增 `ZiCodeAgentOrchestrator`：
  - 执行循环：按计划顺序调度工具调用
  - 约束落地：优先确保 `ai/<task-id>` 分支
  - 自愈框架：失败日志 -> `ZiCodeReport` -> 补丁风险检查 -> 重新触发 workflow（可循环）
- 新增 `ZiCodeWorkflowTemplateService`：
  - 自动补齐 6 个工作流模板（`lint/test/web_build/pages_build_deploy/android_build/release`）
  - 模板统一产出并上传 `sandbox/report.json`
  - 缺失模板时自动走“补丁 -> 提交 -> PR”初始化流程（默认不直推主分支）
