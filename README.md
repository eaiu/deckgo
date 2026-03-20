# DeckGo

DeckGo 是一个面向学习和实践的大模型 PPT 生成项目。

当前主路线已经调整为 SVG-first：

`需求输入 -> 背景调研 -> 资料整理 -> 大纲策划 -> 页面策划稿 -> 最终 SVG 页面集`

当前仓库继续保持中立根目录结构：

- `backend/`：`Spring Boot + Spring AI`，负责项目、工作流、AI 编排、版本与 API
- `frontend/web/`：`Vue 3 + Vite` 的 SVG 创作工作台
- `frontend/renderer/`：保留的 `Node + PptxGenJS` 渲染器，用于现有 DeckSpec/PPTX 路线
- `frontend/packages/`：前端与渲染器共享 TS 包
- `contracts/`：`DeckSpec`、`PagePlan`、模板目录和示例契约
- `scripts/`：本地开发脚本
- `docs/`：需求基线、架构说明、ADR 与实施日志

当前主产品路径不再以 `DeckSpec` 编辑器为默认入口，而是：

- 首页只保留一个需求输入框
- 创建后进入 SVG 创作页
- 左侧是聊天与阶段侧栏
- 中间是当前页 SVG 画布
- 右侧是页面缩略列表

现有 `DeckSpec -> PPTX` 能力和相关代码仍然保留在仓库里，作为兼容与后续扩展基础，不在这轮被删除。

如果你第一次进入这个仓库，建议按下面顺序阅读：

1. `docs/项目要求.md`
2. `docs/project-plan.md`
3. `docs/architecture-overview.md`
4. `docs/dev-setup.md`
5. `docs/database-schema.md`
