## ZionChat v0.31.74

- 修复 ZiCode 编译失败：`ZiCodeModelAgent` 中 `toolHints` 聚合改为先转 `List` 后截断，消除 `takeLast` 类型错误。
- 修复 ZiCode 输入栏编译错误：补充 `WindowInsets.ime` 所需 import，恢复键盘顶起相关代码构建。
- 保持 `0.31.73` 的会话仓库绑定与默认模型清理逻辑不变，仅做稳定性修复。
