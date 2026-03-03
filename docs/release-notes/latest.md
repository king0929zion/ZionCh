## ZionChat v0.31.52

- 移除未使用的液态玻璃残留代码：删除 `ui/components/liquid` 下未引用的 3 个文件，减少无效编译负担。
- 清理未使用依赖：移除 `io.github.kyant0:backdrop` 与 `io.github.kyant0:shapes`。
- 清理未调用组件：删除 `TopFadeScrim.kt` 中未被使用的 `BottomFadeScrim`。
- 保持现有功能入口与路由不变（含 MCP/AutoSoul/设置子页）。
