## ZionChat v0.31.72

- ZiCode 主执行链路改为模型驱动：由模型持续决策 `tool_call/final_answer`，不再走固定工作流编排。
- 新增 `ZiCodeModelAgent`，支持工具调用结果回灌、JSON envelope 解析重试与基础自愈循环。
- ZiCode 发送逻辑接入默认 ZiCode 模型配置；未配置时给出明确引导，不再静默失败。
- ZiCode 对话页继续保留 `/tool` 直调能力，并优化工具提示聚合展示。
