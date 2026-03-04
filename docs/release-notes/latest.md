## ZionChat v0.31.65

- 主对话页底部工具菜单收起动画优化为 iOS 风格分段：先横向内收离开两侧，再向下收起，退出更顺滑。
- About 页面顶部图标更新为当前应用图标（`@mipmap/ic_launcher`），与启动图标统一。
- ZiCode 工具能力扩展：
  - 新增 `mcp.list_servers`、`mcp.list_tools`、`mcp.call_tool`
  - 新增 `repo.list_branches`、`actions.get_latest_run`
  - `policy.get_toolspec` 同步包含新工具定义。
- ZiCode 聊天任务规划增强：
  - 当请求涉及 MCP/工具调用时，自动加入 `mcp.list_servers`
  - 触发工作流后自动补一条 `actions.get_latest_run` 追踪最新 run。
