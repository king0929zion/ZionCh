# ZiCode 归档备份

- 归档日期：2026-03-06
- 目的：在从主应用移除 `ZiCode` 全部代码前，保留原始实现、入口耦合点和资源文件，便于后续独立迁移、审计或恢复。
- 备份范围：
  - `app/src/main/java/com/zionchat/app/data/` 下全部 `ZiCode*.kt`
  - `app/src/main/java/com/zionchat/app/ui/screens/` 下全部 `ZiCode*.kt`
  - 与 `ZiCode` 耦合的 `AppContainer.kt`、`MainActivity.kt`、`AppRepository.kt`、`Models.kt`
  - 受影响 UI 入口文件：`ChatScreenSections.kt`、`DefaultModelScreen.kt`、`SettingsScreen.kt`
  - 相关资源：`ic_zicode.xml`、`ic_zicode_repo.xml`、中英文字符串资源
- 说明：本目录仅作源码归档，不参与 Android 构建。
