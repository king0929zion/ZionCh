## ZionChat v0.31.60

- 新增 `ZiCodeToolDispatcher`，落地 `repo.*` 工具链（读写 + PR）：
  - 读取：`repo.list_tree`、`repo.list_dir`、`repo.read_file`、`repo.search`、`repo.get_file_meta`
  - 写入：`repo.create_branch`、`repo.replace_range`、`repo.apply_patch`、`repo.commit_push`
  - PR：`repo.create_pr`、`repo.comment_pr`、`repo.merge_pr`
- `repo.apply_patch` 新增 unified diff 最小可用解析器，支持 add/update/delete 三类补丁并进入会话暂存区。
- `repo.commit_push` 使用 Git Data API 统一提交暂存变更：`blob -> tree -> commit -> update ref`。
- 新增 ZiCode 工具调用可审计记录落库：每次调用都会写入 `ZiCodeToolCall`（参数、状态、时间、结果/错误、提示文案）。
