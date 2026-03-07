## ZionChat v0.38.0

- 继续收口 `ZiCode`：默认模型页进一步资源化，补上 `ZiCode Agent` 的完整中英文适配，并把相关提醒文案统一回灰白黑体系，不再出现偏色 warning。
- `ZiCode` 的 GitHub Agent 再扩一轮：会话执行流新增 `Issues`、`Pull Requests`、`Artifacts` 上下文，支持读取 Issue / PR / 构建产物，并按提示词自动尝试创建 Issue、创建 PR。
- `ZiCode` 运行态文案完成双语化：工具状态、结果按钮、Agent fallback 总结、工具清单和设置页能力说明都会跟随应用语言切换，不再混杂中英文硬编码。
- `ZiCode` 的 GitHub 底层错误文案改成中性英文，英文环境下不会再冒出中文异常信息；README、Actions、Pages、Release 等执行摘要也一并收紧为统一语气。
- 继续优化远端构建体验：补充 `org.gradle.configuration-cache.parallel=true`，进一步压缩重复构建时的配置阶段开销。
