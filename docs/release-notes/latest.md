## ZionChat v0.31.61

- `ZiCodeToolDispatcher` 新增 `actions.*` 工具：
  - `actions.trigger_workflow`
  - `actions.get_run`
  - `actions.get_logs_summary`
  - `actions.list_artifacts`
  - `actions.download_artifact`
- 新增 `pages.*` 工具：
  - `pages.get_settings`
  - `pages.enable`
  - `pages.set_source`
  - `pages.get_deployments`
  - `pages.get_latest_url`
  - `pages.deploy`
- 新增构建失败日志摘要逻辑：从失败日志抽取 `error_summary` 与 `file_hints`，并结构化为 `ZiCodeReport` 返回。
- 保持 `0.31.60` 的 repo 读写与 PR 工具能力不变，全部继续写入 `ZiCodeToolCall` 审计记录。
