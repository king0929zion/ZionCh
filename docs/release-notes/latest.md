## ZionChat Android 版本说明

- 移除未使用的 Vercel 部署与 Runtime APK 打包模块，减少无效依赖与维护成本。
- 设置页完成 UI 迭代：入口尺寸增大、模块底色统一为 `#F2F2F2`、核心文字加粗，提升可读性与点击效率。
- CI 构建链路提速：保持每次 push 自动 release，同时在 CI 快速构建模式下关闭 `release` 混淆、资源压缩、PNG crunch、zipAlign 与 release lint 检查。
- CI 构建卡顿修复：CI Release 构建禁用 `android.enableResourceOptimizations`，跳过 `:app:optimizeReleaseResources` 的超长耗时步骤。
- 编译告警治理：批量清理数据层 `?. / ?: / !!` 冗余与多处 Compose 弃用告警（`Divider`→`HorizontalDivider`、`FlowPreview`/序列化 `OptIn`、兼容路径抑制），显著降低 `compileReleaseKotlin` 警告噪音。
- 修复 GitHub Actions 中 `gradlew` 执行权限问题，并提升 Gradle 并行参数以缩短构建时间。
