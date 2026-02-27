## ZionChat Android 版本说明

- 移除未使用的 Vercel 部署与 Runtime APK 打包模块，减少无效依赖与维护成本。
- 设置页完成新一轮视觉微调：文字取消加粗并小幅放大，同组模块之间改为空白分隔线露出背景，模块底色改为 `#F3F3F3`，圆角进一步增大，页面背景更白。
- CI 构建链路提速：保持每次 push 自动 release，同时在 CI 快速构建模式下关闭 `release` 混淆、资源压缩、PNG crunch、zipAlign 与 release lint 检查。
- CI 构建卡顿修复：CI Release 构建禁用 `android.enableResourceOptimizations`，跳过 `:app:optimizeReleaseResources` 的超长耗时步骤。
- CI 并发策略修复：`concurrency.cancel-in-progress` 调整为 `false`，新提交会排队等待，不再自动取消上一个正在执行的构建。
- 编译告警治理：批量清理数据层 `?. / ?: / !!` 冗余与多处 Compose 弃用告警（`Divider`→`HorizontalDivider`、`FlowPreview`/序列化 `OptIn`、兼容路径抑制），显著降低 `compileReleaseKotlin` 警告噪音。
- 修复一次 Kotlin 编译回归：`listAntigravityModels` 的 `lastError` 改为可空安全处理，避免 `compileReleaseKotlin` 因可空类型实参报错失败。
- 修复 GitHub Actions 中 `gradlew` 执行权限问题，并提升 Gradle 并行参数以缩短构建时间。
