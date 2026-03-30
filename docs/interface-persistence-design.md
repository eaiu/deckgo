# DeckGo Current Interface And Persistence Design

## Overview

当前项目已经收敛到单一主模型，但接口层现在开始拆分为两层：

- 项目主接口：`/api/projects/**`
- studio 编排接口：`/api/studio/projects/**`
- 持久化主模型：`projects + requirement_forms + outline_versions + project_pages + research/draft/design`
- 前端主入口：`/projects/:projectId`

旧的 `workflow-sessions` 运行时链路已经从代码中移除。历史 migration `V2 ~ V4` 仍保留在 Flyway 历史里，但运行时表已通过 `V7__drop_legacy_workflow_runtime.sql` 清理。

## Current API

### Common

- `GET /api/health`
- `GET /api/templates`

### Project

- `GET /api/projects`
  - 返回项目列表

- `POST /api/projects`
  - 请求体：
    ```json
    {
      "title": "季度经营复盘",
      "requestText": "围绕 AI 产品增长做季度经营复盘"
    }
    ```
  - 返回：`ProjectDetailResponse`
  - 动作：只创建最小项目头，不触发编排。其余约束与需求信息在 requirements/init 阶段继续补齐

- `GET /api/projects/{projectId}`
  - 返回项目详情

- `PUT /api/projects/{projectId}`
  - 更新项目元数据

- `DELETE /api/projects/{projectId}`
  - 删除项目

- `GET /api/projects/{projectId}/events/stream`
  - SSE 订阅项目事件流

### Requirements

- `GET /api/projects/{projectId}/requirements/form`
  - 读取 requirement form

- `POST /api/projects/{projectId}/requirements/answers:batch`
  - 批量写入问题答案

- `PATCH /api/projects/{projectId}/requirements/answers/{questionCode}`
  - 单题更新答案

- `POST /api/projects/{projectId}/requirements/confirm`
  - 将当前 requirement answers 转成首阶段确认动作，并推进到 outline

### Studio

- `POST /api/studio/projects`
  - 请求体：
    ```json
    {
      "prompt": "用户原始需求",
      "pageCountTarget": 12,
      "stylePreset": "clarity-blue",
      "backgroundAssetPath": null,
      "workflowConstraints": {}
    }
    ```
  - 返回：`ProjectStudioSnapshot`
  - 动作：
    1. 创建项目
    2. 执行首轮背景调研
    3. 生成需求确认问题卡
    4. 落库 requirement form、消息、事件、stage run

- `GET /api/studio/projects/{projectId}`
  - 返回项目级聚合快照

- `POST /api/studio/projects/{projectId}/commands`
  - 推进阶段：
    - `SUBMIT_DISCOVERY`
    - `APPLY_OUTLINE_FEEDBACK`
    - `CONTINUE_TO_RESEARCH`
    - `CONTINUE_TO_PLANNING`
    - `CONTINUE_TO_DESIGN`

- `POST /api/studio/projects/{projectId}/chat`
  - 当前是命令式推进的语义包装

- `GET /api/studio/projects/{projectId}/pages/{pageId}`
  - 读取单页快照

- `POST /api/studio/projects/{projectId}/pages/{pageId}/redesign`
  - 基于当前 draft 重新生成 design

## Current Frontend Entry

- `HomePage`
  - 输入 `prompt`
  - 调用 `POST /api/studio/projects`
  - 跳转到 `/projects/:projectId`

- `ProjectStagePage`
  - 展示首阶段背景调研、来源、问题卡、消息流、stage run

当前前端已经不再依赖 `/api/workflow-sessions/**`，并且 studio 页面改为从 `/api/studio/projects/**` 读取编排快照。

## Current Persistence Model

### Root

#### `projects`

根表，保存：

- 项目基础信息：`title`, `topic`, `audience`, `template_id`
- 项目工作流头字段：`request_text`, `current_stage`, `current_outline_version_id`
- 固定约束：`page_count_target`, `style_preset`, `background_asset_path`, `workflow_constraints_json`

### Requirement And Init Stage

#### `requirement_forms`

一项目一份，承接首阶段需求收敛：

- `summary_md`
- `outline_context_md`
- `fixed_items_json`
- `init_search_queries_json`
- `init_search_results_json`
- `init_corpus_digest_json`
- `ai_questions_json`
- `answers_json`

#### `project_messages`

项目消息流，前端可直接展示。

#### `project_events`

系统事件流，适合后续接 SSE 或回放。

#### `project_stage_runs`

项目级阶段运行记录。当前首阶段背景调研会写入：

- 输入摘要
- 输出摘要
- 开始/结束状态

### Outline And Page Head

#### `outline_versions`

大纲版本历史，append-only。

#### `project_pages`

页面头表，保存：

- 页面标识与排序
- 当前 brief/research/draft/design 头指针
- 页面级状态：
  - `outline_status`
  - `search_status`
  - `summary_status`
  - `draft_status`
  - `design_status`
- `artifact_staleness_json`

### Page Versions

#### `page_brief_versions`

页面 brief / page plan 版本。

#### `draft_versions`

草稿 SVG 版本。

#### `design_versions`

最终设计稿 SVG 版本。

### Research And Source Corpus

#### `research_sessions`

页面研究会话主表。

#### `research_sources`

研究来源卡片。

#### `research_session_sources`

研究会话与文档/分块映射。

#### `source_collections`

来源集合。

#### `source_documents`

标准化来源文档。

#### `source_chunks`

文档切块。当前 `embedding` 只是占位文本列，不强依赖 `pgvector`。

#### `url_content_cache`

URL 内容缓存。

#### `bocha_search_cache`

查询结果缓存。

#### `retrieval_runs`

检索执行记录。

#### `retrieval_candidates`

检索候选结果。

#### `citations`

最终引用落点。

### Other

#### `page_stage_runs`

页面级阶段运行记录。

#### `export_jobs`

导出任务表。当前 schema 已保留，业务尚未完整接通。

## Removed Legacy Runtime

下面这套旧模型已经不再是当前项目的一部分：

- `/api/workflow-sessions`
- `workflow_sessions`
- `workflow_messages`
- `workflow_versions`
- `workflow_pages`
- 前端旧 `StudioPage`

它们对应的是仓库早期的“会话级工作流”尝试，已经和当前要复刻的 `ppt-agent` 项目级模型冲突，所以已从运行时代码中移除。

## Recommended Direction

如果继续向 `ppt-agent` 靠拢，接下来应该做的是：

1. 把首阶段从“同步创建项目”继续演进成“异步 bootstrap + 事件流可视化”
2. 把当前 `DISCOVERY/OUTLINE/RESEARCH/PLANNING/DESIGN` 重新命名成更接近 `ppt-agent` 的项目主阶段
3. 让 `project_events` 成为前端 agent activity 的正式数据源
4. 把 requirement form 从“快照字段”进一步收敛成明确的初始化闸门
