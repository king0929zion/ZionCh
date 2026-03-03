## ZionChat v0.31.59

- 修复 ZiCode `Workspace` 弹层的编译问题：移除了 `onClick` 非 `@Composable` 上下文中的 `stringResource` 调用。
- 修复连通性检查结果类型推断问题，统一返回可折叠的 `Result`，避免 Kotlin 编译失败。
- 保持 `0.31.58` 的功能能力：多仓库 + PAT + 会话持久化 + 仓库连通性检测全部可用。
