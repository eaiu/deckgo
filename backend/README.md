# DeckGo Backend

这里是 DeckGo 的后端骨架，技术栈是 `Spring Boot + Spring AI + Flyway + PostgreSQL`。

它负责：

- 项目和版本管理
- `DeckSpec` 校验
- 模板目录读取
- AI 草案与修订接口
- 渲染任务和导出产物管理

推荐先看：

1. `src/main/resources/application.yml`
2. `src/main/java/com/deckgo/backend/common`
3. `src/main/java/com/deckgo/backend/project`
4. `src/main/java/com/deckgo/backend/ai`
