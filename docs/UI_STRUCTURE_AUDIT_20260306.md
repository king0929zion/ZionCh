# ZionChat UI 与结构扫描

## 当前 UI 风格

- 主视觉是浅色高留白方案，主题色偏 `#007AFF`，整体明显借鉴 iOS 风格而非标准 Material 3 动态色。
- 字体体系使用 `Source Sans 3` 资源文件，但 `Typography` 里多数文本仍落回 `FontFamily.Default`，主题定义与实际字体使用存在轻微分裂。
- 过渡动效以横向滑入滑出和淡入淡出为主，时长控制在 200ms 到 340ms，节奏偏克制。
- 交互反馈刻意关闭了全局 ripple，按钮和卡片更多依赖缩放、描边和背景变化表达点击态。

## 组件与效果

- 页面骨架以 Compose `NavHost + Surface + SettingsPage` 为主，说明项目已经形成统一页面容器。
- 聊天区采用气泡、附件网格、Markdown 渲染、Tag 卡片和 MCP 结果块混排，属于信息密度较高的复合界面。
- 设置页是卡片式分组列表，AutoSoul、MCP、模型服务等能力入口都集中在这里，信息架构清晰。
- 项目内存在 `LiquidGlassSwitch`、`TopFadeScrim`、`AppBottomSheet` 等自定义组件，说明团队在持续沉淀通用 UI 资产。

## 结构观察

- 当前目录已经按 `ui/theme`、`ui/components`、`ui/screens`、`data`、`autosoul` 做了基础分层，整体方向是对的。
- `ui/screens` 下文件数量较多且偏扁平，功能继续增长时更适合按领域再拆子目录。
- `AppRepository.kt` 同时承担配置存储、业务清洗和多域数据读写，单文件职责偏重，是后续最值得继续拆分的点。

## 本次优化

- 已将 `ZiCode` 相关源码、资源和耦合入口完整归档到 `docs/backups/zicode-20260306/`。
- 已从应用骨架层移除 `ZiCode` 的导航、设置入口、默认模型入口和资源引用。
- 已在 `AppRepository` 中加入废弃 `ZiCode` 持久化数据清理，减少升级后的残留配置和无效存储。
