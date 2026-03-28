# DeckGo Backend

这里是 DeckGo 的后端主编排层，技术栈是 `Spring Boot + Spring AI + Flyway + PostgreSQL`。

它负责：

- 项目管理
- 工作流会话管理
- 多阶段 AI 编排
- `PagePlan` 与 SVG 页面持久化
- 模板目录读取
- 聊天式 orchestrator 和进度推送

推荐先看：

1. `src/main/resources/application.yml`
2. `src/main/java/com/deckgo/backend/common`
3. `src/main/java/com/deckgo/backend/project`
4. `src/main/java/com/deckgo/backend/workflow`
5. `src/main/java/com/deckgo/backend/ai`
