# DeckGo

DeckGo 是一个 SVG-first 的 AI 演示文稿工作台。

当前主链路是：

`需求输入 -> 背景调研 -> 大纲策划 -> 逐页资料 -> 页面策划稿 -> 最终 SVG 页面集`

当前仓库结构：

- `backend/`：`Spring Boot + Spring AI`，负责项目、工作流、AI 编排、持久化与 API
- `frontend/web/`：`Vue 3 + Vite` 的 SVG 创作工作台
- `frontend/packages/`：前端共享 TS 包
- `contracts/`：`PagePlan` Schema 与模板目录
- `scripts/`：本地开发脚本
- `docs/`：架构、开发和数据文档

当前默认产品入口：

- 首页只保留一个需求输入框
- 创建后进入 `/studio/:sessionId`
- 左侧是聊天与阶段推进
- 中间是当前页 SVG 画布
- 右侧是页面列表与缩略预览

如果你第一次进入这个仓库，建议按下面顺序阅读：

1. `docs/项目要求.md`
2. `docs/project-plan.md`
3. `docs/architecture-overview.md`
4. `docs/dev-setup.md`
5. `docs/database-schema.md`
