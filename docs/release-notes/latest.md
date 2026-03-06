## ZionChat v0.34.0

- 全新重做 `ZiCode` 模块，不复用旧实现，改为独立 `zicode` 数据层与 GitHub 服务，结构更清晰、后续更容易继续扩展。
- 新增 ZiCode 仓库列表页、项目会话页、文件浏览页与设置页，视觉统一贴合主设置页：纯白背景、`#F1F1F1` 容器灰、大圆角、顶部半透明模糊，并继续锁定全模块只使用灰白黑体系。
- ZiCode 支持 GitHub Token 配置、仓库拉取、新建仓库、项目内多会话、文件树浏览、文件底部预览，以及带 shimmer 的工具调用状态展示。
- 侧栏与设置页已重新接入 ZiCode 入口，文件按钮图标同步替换为新的统一规范资源。
- 优化 GitHub Actions 构建速度：新增并发取消、浅克隆、Gradle 缓存清理与更稳定的 Kotlin 增量缓存 key，同时开启 Gradle configuration cache 与 VFS watch。
- 修复 ZiCode 新界面的 Compose 导入与交互接线问题，保证新的仓库卡片和项目对话输入区能顺利通过远端编译。
