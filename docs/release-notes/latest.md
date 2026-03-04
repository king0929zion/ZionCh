## ZionChat v0.31.71

- 新增全屏仓库浏览页 `zicode_repo_browser`，支持多层级目录懒加载。
- ZiCode 右上文件夹按钮改为跳转仓库浏览页，不再弹旧工作区面板。
- 新增 GitHub 服务层目录与文件读取接口：
  - `listRepoDir(workspace, pat, ref, path)`
  - `readRepoFile(workspace, pat, ref, path)`
- 支持文件内容预览；空目录/404/PAT 失效/空仓库场景统一友好错误提示。
