## ZionChat Android 版本说明

- 修复 CI 编译错误：`settingsBottomInsets` 扩展补充 `@Composable` 标记，恢复设置页相关构建通过。
- 设置页视觉统一升级：所有设置入口子页面 Header 统一改为半透明模糊玻璃效果，消除旧灰色页头不一致问题。
- 顶部功能按钮进一步增强可见性：仅对 Header 区域的返回/新增/保存圆形按钮加深阴影，白底页面边界更清晰。
- 交互样式一致性优化：`Save` 操作统一为白底圆形勾图标，避免文字按钮与页面风格割裂。
- 子页面文字层级提升：`Memories`、`Model Services`、`MCP Tools` 等设置子页面主文案统一加粗到接近主设置页风格。
- 组件细节优化：`Memories` 卡片与文字轻微放大，`Model Services` 供应商卡片灰色统一为主设置页同款 `#F1F1F1`。
- 底部可见性修复：补齐设置体系页面底部安全内边距，滚动内容可完整显示到导航栏区域。

- 设置体系进一步统一：补齐多处子页面 Header 白色风格，统一顶部背景色，消除剩余不一致。
- 顶部功能按钮阴影增强：白底圆形按钮加深并扩大阴影范围，在浅色页面中边界更清晰。
- 保存交互统一升级：将保存入口统一为白底圆形勾图标（含设置与配置相关页面），样式与头部按钮保持一致。
- 修复设置体系页头视觉：为返回/保存/新增等头部圆形按钮新增浅色大范围阴影，白底页面中按钮轮廓更清晰。
- 统一设置子页面的 Header 与内容区背景为白色系，消除顶部灰白色差。
- 修复 About 页面分组内条目间距，和主设置页一致地恢复模块间留白，不再“黏连”。
- 移除未使用的 Vercel 部署与 Runtime APK 打包模块，减少无效依赖与维护成本。
- 设置页完成新一轮视觉微调：文字取消加粗并小幅放大，同组模块之间改为空白分隔线露出背景，模块底色改为 `#F3F3F3`，圆角进一步增大，页面背景更白。
- 设置页入口图标统一微调放大（如 Personalization、Language 等），强化可识别性并保持整体版式平衡。
- 设置页继续细调：分组卡片圆角进一步加大，模块底色加深为更灰的 `#F1F1F1`，按下态同步加深以保持层次一致。
- 设置页模块高度再次微调下压：条目最小高度与上下内边距同步降低，列表视觉更紧凑。
- CI 构建链路提速：保持每次 push 自动 release，同时在 CI 快速构建模式下关闭 `release` 混淆、资源压缩、PNG crunch、zipAlign 与 release lint 检查。
- CI 构建卡顿修复：CI Release 构建禁用 `android.enableResourceOptimizations`，跳过 `:app:optimizeReleaseResources` 的超长耗时步骤。
- CI 并发策略修复：`concurrency.cancel-in-progress` 调整为 `false`，新提交会排队等待，不再自动取消上一个正在执行的构建。
- 编译告警治理：批量清理数据层 `?. / ?: / !!` 冗余与多处 Compose 弃用告警（`Divider`→`HorizontalDivider`、`FlowPreview`/序列化 `OptIn`、兼容路径抑制），显著降低 `compileReleaseKotlin` 警告噪音。
- 修复一次 Kotlin 编译回归：`listAntigravityModels` 的 `lastError` 改为可空安全处理，避免 `compileReleaseKotlin` 因可空类型实参报错失败。
- 修复 GitHub Actions 中 `gradlew` 执行权限问题，并提升 Gradle 并行参数以缩短构建时间。
