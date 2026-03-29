# DeckGo

DeckGo 是一个面向 AI PPT 生成的 Spring Boot + Spring AI 工作台。

当前主链路是：

`需求输入 -> 背景调研 -> 大纲策划 -> 逐页资料 -> 页面策划稿 -> 最终 SVG 页面集`

当前仓库结构：

- `backend/`：`Spring Boot + Spring AI`，负责项目级工作流、持久化与 API
- `frontend/web/`：`Vue 3 + Vite` 的项目工作台
- `frontend/packages/`：前端共享 TS 包
- `contracts/`：`PagePlan` Schema 与模板目录
- `scripts/`：本地开发脚本
- `docs/`：架构、开发和数据文档

当前默认产品入口：

- 首页只保留一个需求输入框
- 创建后进入 `/projects/:projectId`
- 当前默认先展示项目首阶段：背景调研、需求单、问题卡与阶段运行记录
- 项目主模型统一为 `projects + requirement_forms + outline_versions + project_pages + research/draft/design` 这一套表
- 旧的 `workflow-sessions` 运行时链路已经移除，数据库中也通过迁移清理

如果你第一次进入这个仓库，建议按下面顺序阅读：

1. `docs/interface-persistence-design.md`
2. `README.md`
