## ZionChat v0.31.58

- 新增 ZiCode 数据层模型：`ZiCodeWorkspace`、`ZiCodeSession`、`ZiCodeMessage`、`ZiCodeToolCall`、`ZiCodeRunRecord`、`ZiCodeReport`、`ZiCodeSettings`。
- `AppRepository` 新增 ZiCode 专用 flows 与 CRUD：支持多仓库、会话、消息、运行记录与工具调用持久化。
- ZiCode 配置已支持 PAT 加密存储（复用 `SecureValueCipher`），并纳入敏感配置迁移流程。
- 新增 `ZiCodeGitHubService`，支持 `GET /user` 与仓库权限探测，用于连通性检查。
- ZiCode 页面接入 `Workspace` 底部弹层：可新增/切换仓库、输入 PAT、检测连接并保存为当前工作区。
- ZiCode 聊天页由本地临时状态升级为“工作区 + 会话 + 消息”持久化链路，支持按模型创建新会话并恢复历史消息。
