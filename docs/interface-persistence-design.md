# DeckGo Interface And Persistence Design

## 1. Overview

当前仓库的后端接口和持久化设计分成两套并行模型：

| Model | Main Endpoints | Persistence Style | Frontend Usage | Status |
| --- | --- | --- | --- | --- |
| Workflow Session Model | `/api/workflow-sessions/**` | `JPA Entity + Repository`，落到 `workflow_*` 表 | 首页创建、Studio 工作台主链路正在使用 | 当前主入口 |
| Project Studio Model | `/api/projects/**` | `JdbcTemplate` 直接读写，落到 `projects + V6 细分表` | `ProjectsPage` 只用到列表和创建；主工作台尚未切到这套接口 | 新模型，已能跑通后端链路 |
| Template Catalog | `/api/templates` | 文件读取，来自 `contracts/templates/catalog.json` | 项目创建页使用 | 非数据库持久化 |
| Health | `/api/health` | 无持久化 | 运维/探活 | 基础接口 |

这意味着当前项目不是“单一接口模型 + 单一数据模型”，而是处于从旧的工作流会话模型向新的项目级精细化模型演进的阶段。

## 2. Interface Design

### 2.1 Common Rules

- 接口统一挂在 `/api` 下。
- 参数校验失败时返回 `400` 或 `422`。
- 未找到资源时返回 `404`。
- 通用错误结构为：

```json
{
  "code": "validation_error",
  "message": "错误摘要",
  "errors": ["字段级错误列表"],
  "timestamp": "2026-03-29T00:00:00Z"
}
```

- 当前没有显式版本号，例如 `/api/v1/...`。

### 2.2 Basic Endpoints

| Method | Path | Request | Response | Persistence |
| --- | --- | --- | --- | --- |
| `GET` | `/api/health` | none | `{ status, timestamp }` | none |
| `GET` | `/api/templates` | none | `TemplateSummary[]` | 读取 `contracts/templates/catalog.json` |

`TemplateSummary` 结构：

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | `string` | 模板 ID |
| `name` | `string` | 模板名称 |
| `description` | `string` | 模板描述 |
| `slideKinds` | `string[]` | 支持的页面类型 |
| `defaultTheme` | `json` | 默认主题色配置 |

### 2.3 Workflow Session API

这套接口是当前前端首页和 Studio 工作台主链路实际在使用的接口面。

#### 2.3.1 Endpoints

| Method | Path | Request | Response | Main Tables |
| --- | --- | --- | --- | --- |
| `POST` | `/api/workflow-sessions` | `{ prompt }` | `WorkflowSessionResponse` | `projects`, `workflow_sessions`, `workflow_messages` |
| `GET` | `/api/workflow-sessions/{sessionId}` | none | `WorkflowSessionResponse` | `projects`, `workflow_*` |
| `POST` | `/api/workflow-sessions/{sessionId}/commands` | `{ command, selectedOptionIds?, freeformAnswer?, feedback? }` | `WorkflowSessionResponse` | `workflow_sessions`, `workflow_versions`, `workflow_pages`, `workflow_messages` |
| `POST` | `/api/workflow-sessions/{sessionId}/chat` | `{ message }` | `ChatResponse` | `workflow_messages`, `workflow_sessions` |
| `GET` | `/api/workflow-sessions/{sessionId}/progress` | SSE | `tool-progress` event stream | 内存态，不落库 |

#### 2.3.2 Command And Stage Semantics

`command` 当前枚举值：

- `SUBMIT_DISCOVERY`
- `APPLY_OUTLINE_FEEDBACK`
- `CONTINUE_TO_RESEARCH`
- `CONTINUE_TO_PLANNING`
- `CONTINUE_TO_DESIGN`

`currentStage` 当前枚举值：

- `DISCOVERY`
- `OUTLINE`
- `RESEARCH`
- `PLANNING`
- `DESIGN`

`status` 当前枚举值：

- `WAITING_USER`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

#### 2.3.3 Response Shape

`WorkflowSessionResponse` 是一个聚合快照，核心字段如下：

| Field | Meaning |
| --- | --- |
| `sessionId` | 会话 ID |
| `currentStage` | 当前阶段 |
| `status` | 当前状态 |
| `currentVersionId` | 当前版本头指针，指向 `workflow_versions` |
| `selectedTemplateId` | 会话选中的模板 |
| `lastError` | 最近一次失败原因 |
| `project` | 项目基础信息 |
| `messages` | 会话消息流 |
| `backgroundSummary` | 背景信息 JSON |
| `discoveryCard` | 需求澄清卡 JSON |
| `discoveryAnswers` | 用户回答 JSON |
| `outline` | 大纲 JSON |
| `pageResearch` | 逐页研究 JSON |
| `pages` | 当前版本下的页面列表 |
| `updatedAt` | 更新时间 |

#### 2.3.4 Interface Notes

- `/chat` 是编排式自然语言入口，内部通过工具调用推进阶段。
- `/progress` 通过 `SseEmitter` 在内存中维护订阅，不做持久化重放。
- `workflow_messages.tool_calls_json` 字段已建，但当前实现里真实工具步骤主要依赖 SSE，下库内容通常为空。

### 2.4 Project Studio API

这套接口代表新的项目级细粒度模型，接口响应更偏“项目快照”，持久化也更细。

#### 2.4.1 Endpoints

| Method | Path | Request | Response | Main Tables |
| --- | --- | --- | --- | --- |
| `GET` | `/api/projects` | none | `ProjectResponse[]` | `projects` |
| `GET` | `/api/projects/{projectId}` | none | `ProjectStudioSnapshot` | `projects` + `requirement_forms` + `outline_versions` + `project_pages` + `project_messages` + `project_stage_runs` |
| `POST` | `/api/projects` | `{ prompt, pageCountTarget?, stylePreset?, backgroundAssetPath?, workflowConstraints? }` | `ProjectStudioSnapshot` | `projects`, `requirement_forms`, `project_messages`, `project_events`, `project_stage_runs` |
| `POST` | `/api/projects/{projectId}/commands` | `{ command, scopeType?, targetPageId?, selectedOptionIds?, freeformAnswer?, feedback? }` | `ProjectStudioSnapshot` | `outline_versions`, `project_pages`, `research_*`, `page_brief_versions`, `draft_versions`, `design_versions`, `project_*_runs` |
| `POST` | `/api/projects/{projectId}/chat` | `{ message }` | `ProjectStudioChatResponse` | `project_messages`，必要时联动命令推进 |
| `GET` | `/api/projects/{projectId}/pages/{pageId}` | none | `ProjectPageSnapshot` | `project_pages` + 各版本表 |
| `POST` | `/api/projects/{projectId}/pages/{pageId}/redesign` | `{ instruction? }` | `ProjectPageSnapshot` | `design_versions`, `page_stage_runs`, `project_events` |

#### 2.4.2 Response Shape

`ProjectStudioSnapshot` 代表项目级聚合快照：

| Field | Meaning |
| --- | --- |
| `projectId` | 项目 ID |
| `title/topic/audience/templateId` | 项目基础信息 |
| `requestText` | 原始需求文本 |
| `currentStage` | 当前项目阶段 |
| `currentOutlineVersionId` | 当前大纲版本头指针 |
| `pageCountTarget` | 目标页数 |
| `stylePreset` | 风格预设 |
| `backgroundAssetPath` | 背景资源路径 |
| `workflowConstraints` | 工作流约束 JSON |
| `requirementForm` | 当前需求表单快照 |
| `currentOutline` | 当前大纲快照 |
| `pages` | 所有页面快照 |
| `messages` | 项目消息流 |
| `projectRuns` | 项目级阶段运行记录 |
| `createdAt/updatedAt` | 时间戳 |

`ProjectPageSnapshot` 代表页面头状态 + 当前生效版本聚合：

| Field | Meaning |
| --- | --- |
| `pageCode/pageRole/partTitle/sortOrder` | 页面标识与排序 |
| `currentBriefVersionId/currentResearchSessionId/currentDraftVersionId/currentDesignVersionId` | 当前头指针 |
| `outlineStatus/searchStatus/summaryStatus/draftStatus/designStatus` | 各阶段状态 |
| `artifactStaleness` | 下游产物是否过期 |
| `currentBrief/currentResearch/currentDraftSvg/currentDesignSvg` | 当前实际内容 |
| `citations` | 页面引用列表 |

#### 2.4.3 Interface Notes

- 这套接口把“项目”和“页面”视为一级对象，而不是单个会话。
- 项目级 `/chat` 目前本质上仍是对命令接口的语义包装。
- `scopeType` 和 `targetPageId` 已出现在命令层，但当前主实现仍以项目级推进为主，仅页面重设计是明确的页面级操作。

### 2.5 Frontend Mapping Status

| Frontend Page | Actual Endpoint Family | Note |
| --- | --- | --- |
| `HomePage.vue` | `/api/workflow-sessions` | 首页输入需求后直接创建 workflow session |
| `StudioPage.vue` | `/api/workflow-sessions` | 当前主工作台全部依赖 workflow session API |
| `ProjectsPage.vue` | `GET /api/projects`, `POST /api/projects`, `GET /api/templates` | 只做列表和创建 |

当前有一个实现现状需要注意：

- 前端 `frontend/web/src/api.ts` 中 `createProject()` 的返回类型仍声明为 `ProjectDto`。
- 后端 `POST /api/projects` 实际返回的是 `ProjectStudioSnapshot`。
- 当前页面代码没有消费返回体细节，所以暂时没有暴露为运行时错误，但这是接口契约已经发生变化、前端类型尚未同步的迹象。

## 3. Persistence Design

### 3.1 Technical Basis

- 数据库：PostgreSQL
- 迁移：Flyway
- JPA 策略：`ddl-auto: validate`
- JSON 持久化：大量使用 `jsonb`
- 向量能力：`create extension if not exists vector`，`source_chunks.embedding` 为 `vector`

迁移演进大致如下：

| Migration | Meaning |
| --- | --- |
| `V1` | 初始项目、旧 deck 版本、渲染任务模型 |
| `V2` | 引入 `workflow_sessions / workflow_versions / workflow_pages / workflow_messages` |
| `V3` | 为 workflow 模型补充背景、研究等上下文字段，并调整阶段命名 |
| `V4` | 为 workflow chat 增加 `tool_calls_json` 和 `message_type` |
| `V5` | 删除旧 `deck_versions / artifacts / render_jobs` 管线 |
| `V6` | 引入新的项目级精细化表结构 |

### 3.2 Design Principles

当前持久化设计有三个核心特征：

1. `projects` 是两套模型共享的根对象。
2. 新模型采用“追加版本表 + 头指针字段”的方式管理当前生效内容。
3. AI 过程数据和中间产物大量以 `jsonb` 保存，便于迭代 agent 输出结构。

### 3.3 Shared Root Table

#### `projects`

| Column Group | Main Fields |
| --- | --- |
| 基础信息 | `id`, `title`, `topic`, `audience`, `template_id` |
| Studio 扩展头字段 | `request_text`, `current_stage`, `current_outline_version_id`, `page_count_target`, `style_preset`, `background_asset_path`, `workflow_constraints_json` |
| 时间字段 | `created_at`, `updated_at` |

设计说明：

- 这是整个系统的根表。
- 旧 workflow 模型通过 `project_id` 挂接到它。
- 新 studio 模型也围绕它展开。
- `current_outline_version_id` 目前是软引用，不是数据库外键。

### 3.4 Workflow Session Model Tables

#### `workflow_sessions`

| Key Fields | Meaning |
| --- | --- |
| `id`, `project_id` | 会话与所属项目 |
| `current_version_id` | 当前版本头指针，指向 `workflow_versions` |
| `status`, `current_stage` | 会话状态 |
| `selected_template_id` | 当前模板 |
| `background_json`, `discovery_json`, `discovery_answers_json`, `outline_json`, `page_research_json` | 阶段上下文 |
| `last_error` | 最近错误 |

这是旧模型的核心聚合根。它直接保存多阶段 JSON，而不是拆表。

#### `workflow_messages`

| Key Fields | Meaning |
| --- | --- |
| `session_id` | 所属会话 |
| `role`, `stage` | 消息角色与阶段 |
| `content_json` | 消息正文 |
| `tool_calls_json` | 工具调用元数据 |
| `message_type` | `COMMAND` 或 `CHAT` |
| `created_at` | 时间戳 |

#### `workflow_versions`

| Key Fields | Meaning |
| --- | --- |
| `project_id`, `version_number` | 项目内版本号 |
| `source` | `PLANNING` / `DESIGN` |
| `template_id` | 模板 |
| `background_json`, `research_json`, `outline_json` | 版本上下文快照 |
| `note`, `created_at` | 备注与时间 |

#### `workflow_pages`

| Key Fields | Meaning |
| --- | --- |
| `workflow_version_id`, `order_index` | 所属版本与页序 |
| `title` | 页面标题 |
| `page_plan_json` | 页面策划 |
| `draft_svg`, `final_svg` | 草稿与最终 SVG |
| `created_at`, `updated_at` | 时间 |

Workflow 模型关系总结：

- `projects 1 - N workflow_sessions`
- `workflow_sessions 1 - N workflow_messages`
- `projects 1 - N workflow_versions`
- `workflow_versions 1 - N workflow_pages`

### 3.5 Project Studio Model Tables

新模型把项目、页面、研究、版本、运行日志拆成了更细的表。

#### 3.5.1 Requirement And Outline

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `requirement_forms` | 需求澄清表单，一项目一份 | `project_id unique`, `status`, `summary_md`, `ai_questions_json`, `answers_json`, `based_on_outline_version_id` |
| `outline_versions` | 大纲版本历史 | `project_id`, `version_no`, `status`, `parent_version_id`, `outline_json` |

设计说明：

- `requirement_forms` 负责承接 discovery 阶段的问答与背景摘要。
- `outline_versions` 是 append-only 历史表。
- `projects.current_outline_version_id` 指向当前生效大纲，但没有 FK 约束。

#### 3.5.2 Page Head And Version Tables

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `project_pages` | 页面主表，保存当前头状态 | `project_id`, `page_code`, `sort_order`, `current_*_id`, `outline_status`, `search_status`, `draft_status`, `design_status`, `artifact_staleness_json` |
| `page_brief_versions` | 页面 brief / plan 版本 | `page_id`, `version_no`, `section_title`, `title`, `content_outline_json`, `content_summary` |
| `draft_versions` | 草稿 SVG 版本 | `page_id`, `version_no`, `page_brief_version_id`, `research_session_id`, `draft_svg_markup` |
| `design_versions` | 最终设计稿版本 | `page_id`, `version_no`, `draft_version_id`, `style_pack_id`, `background_asset_path`, `design_svg_markup` |

设计说明：

- `project_pages` 是页面的“头表”。
- `page_brief_versions / draft_versions / design_versions` 是 append-only 版本表。
- `project_pages.current_brief_version_id` 等字段目前也是软引用，不是 FK。
- `artifact_staleness_json` 显式表达 research、draft、design 是否因上游变更而过期。

#### 3.5.3 Message, Event, Run Log

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `project_messages` | 面向前端展示的项目消息流 | `project_id`, `stage`, `scope_type`, `target_page_id`, `role`, `content_md`, `structured_payload_json` |
| `project_events` | 更偏系统事件日志 | `project_id`, `event_type`, `stage`, `scope_type`, `target_page_id`, `agent_run_id`, `payload_json` |
| `project_stage_runs` | 项目级阶段执行记录 | `project_id`, `stage`, `attempt_no`, `status`, `input_refs_json`, `output_ref_json`, `error_message` |
| `page_stage_runs` | 页面级阶段执行记录 | `project_id`, `page_id`, `stage`, `attempt_no`, `status`, `input_refs_json`, `output_ref_json`, `error_message` |

设计说明：

- `project_messages` 适合 UI 展示。
- `project_events` 适合审计和回放系统事件。
- `*_stage_runs` 适合记录 agent 调用输入输出与重试次数。

#### 3.5.4 Research And Citation

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `research_sessions` | 单页或项目级研究会话 | `project_id`, `page_id`, `scope_type`, `session_role`, `research_goal`, `query_plan_json`, `summary_md`, `key_findings_json`, `context_snapshot_json`, `candidate_sources_json`, `selected_citations_json`, `status` |
| `research_sources` | research 原始来源列表 | `research_session_id`, `title`, `url`, `snippet`, `provider_rank`, `raw_payload_json` |
| `research_session_sources` | research 与结构化来源/分块的映射 | `research_session_id`, `source_document_id`, `chunk_id`, `rank_no`, `excerpt_md`, `relevance_score`, `is_pinned` |
| `citations` | 页面引用落点 | `project_id`, `page_id`, `research_session_id`, `source_document_id`, `chunk_id`, `citation_label`, `excerpt_md` |

设计说明：

- `research_sessions` 是研究结果的主表。
- `citations` 允许从页面直接回溯到 source document/chunk。
- `research_session_sources` 上有唯一约束 `(research_session_id, chunk_id)`，同一 chunk 可被不同 session 复用。

#### 3.5.5 Source Corpus And Cache

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `source_collections` | 一组来源集合 | `project_id`, `page_id`, `collection_type`, `title` |
| `source_documents` | 标准化来源文档 | `collection_id`, `source_type`, `source_uri`, `url_cache_id`, `title`, `markdown_content`, `metadata_json`, `content_hash`, `status` |
| `source_chunks` | 文档切块 | `source_document_id`, `chunk_index`, `section_path`, `content_md`, `content_for_embedding`, `embedding`, `token_count` |
| `url_content_cache` | URL 级内容缓存 | `normalized_url unique`, `provider`, `title`, `markdown_content`, `metadata_json`, `content_hash`, `status`, `expires_at` |
| `bocha_search_cache` | 查询级搜索缓存 | `query_key unique`, `query_text`, `provider`, `result_json`, `result_count`, `expires_at` |

设计说明：

- 这部分是为“可追溯研究语料”和“后续 RAG / 检索”预留的。
- `source_chunks.embedding` 当前 schema 已支持向量，但现阶段写入为 `null`。
- `bocha_search_cache` 虽然表名是 `bocha`，当前实现写入的 `provider` 实际是 `tavily`。

#### 3.5.6 Retrieval And Export

| Table | Purpose | Key Fields | Current Status |
| --- | --- | --- | --- |
| `retrieval_runs` | 一次检索执行 | `project_id`, `research_session_id`, `query_text`, `retrieval_mode`, `status` | 已在 research 落库时写入 |
| `retrieval_candidates` | 检索候选结果 | `retrieval_run_id`, `source_document_id`, `chunk_id`, `score_*`, `selected` | 已写入 |
| `export_jobs` | 导出任务 | `project_id`, `export_format`, `status`, `file_path`, `resolved_manifest_json` | schema 已有，当前代码未使用 |

### 3.6 Stage To Table Write Path

新 Project Studio 模型的写入路径大致如下：

| Stage / Action | Main Writes |
| --- | --- |
| 创建项目 | `projects`, `requirement_forms`, `project_messages`, `project_events`, `project_stage_runs` |
| 提交 Discovery | 更新 `requirement_forms.answers_json`，写 `outline_versions`，同步 `project_pages`，写 `project_messages / project_events / project_stage_runs` |
| Outline 反馈修订 | 新增一条 `outline_versions`，重新同步 `project_pages`，标记下游 stale |
| Continue To Research | 写 `research_sessions`, `research_sources`, `source_collections`, `source_documents`, `source_chunks`, `url_content_cache`, `bocha_search_cache`, `retrieval_runs`, `retrieval_candidates`, `research_session_sources`, `citations`, `page_stage_runs` |
| Continue To Planning | 写 `page_brief_versions`, `draft_versions`, 更新 `project_pages` 当前头指针和状态，写 `page_stage_runs` |
| Continue To Design | 写 `design_versions`，更新 `project_pages.current_design_version_id`，写 `page_stage_runs` |
| 页面重设计 | 追加 `design_versions`，更新页面 design head，写 `page_stage_runs`, `project_events` |

### 3.7 Relationship Summary

可以把新模型理解成下面这组关系：

- `projects 1 - 1 requirement_forms`
- `projects 1 - N outline_versions`
- `projects 1 - N project_pages`
- `project_pages 1 - N page_brief_versions`
- `project_pages 1 - N draft_versions`
- `project_pages 1 - N design_versions`
- `project_pages 1 - N research_sessions`
- `projects 1 - N project_messages`
- `projects 1 - N project_events`
- `projects 1 - N project_stage_runs`
- `project_pages 1 - N page_stage_runs`
- `research_sessions 1 - N research_sources`
- `research_sessions 1 - N research_session_sources`
- `source_collections 1 - N source_documents`
- `source_documents 1 - N source_chunks`
- `citations` 同时关联 `project/page/research/source_document/chunk`

其中“当前版本头”的指向主要保存在：

- `projects.current_outline_version_id`
- `project_pages.current_brief_version_id`
- `project_pages.current_research_session_id`
- `project_pages.current_draft_version_id`
- `project_pages.current_design_version_id`

这些字段当前都属于应用层维护的软引用。

## 4. Current State And Design Judgment

### 4.1 What Is Stable

- `workflow-sessions` 是当前产品主工作台正在用的真实接口面。
- `projects + workflow_*` 这套模型已经能完整跑通“需求 -> 大纲 -> research -> plan -> design”。
- `projects + V6 细分表` 的新模型在后端已能跑通，并且具备更好的审计性和可追溯性。

### 4.2 What Is In Transition

- 同时存在两套接口和两套持久化模型。
- `projects` 是共享根表，导致业务概念上已经统一为“项目”，但运行时主链路仍有“workflow session”语义。
- 新模型使用 `JdbcTemplate`，旧模型使用 `JPA Repository`，形成混合访问层。

### 4.3 Design Strengths

- 新模型把页面级版本、研究来源、引用和运行日志拆开，适合后续做回放、审计和页面级重试。
- 大量使用 append-only 版本表，便于保留 AI 产物历史。
- `project_pages` 的 current-head 设计让读取当前页面快照非常直接。

### 4.4 Design Risks And Gaps

- 头指针大量使用软引用，没有 FK 约束，数据一致性依赖应用层。
- 旧模型和新模型并存，会增加维护成本和接口理解成本。
- `export_jobs`、`vector embedding` 等能力已建表，但当前还未真正跑起来。
- 前端对 `POST /api/projects` 的返回类型声明尚未跟上后端实现。

## 5. Recommended Next Cleanup

如果后续要继续收敛架构，建议按下面顺序推进：

1. 明确主模型，决定是继续以 `workflow-sessions` 为主，还是切换到 `projects`/studio 模型。
2. 若以 studio 模型为主，补齐前端页面和类型定义，减少双轨接口。
3. 为 `current_*_id` 这类头指针评估是否需要补 FK 约束。
4. 将 `export_jobs`、向量检索链路和真实召回逻辑补齐，避免 schema 长期“设计存在、实现缺位”。
5. 若旧 workflow 模型将被淘汰，规划迁移和清理窗口，避免长期并存。

## 6. Source Of Truth

这份文档是按当前代码实现整理的，完整真相仍以下列文件为准：

- `backend/src/main/java/com/deckgo/backend/project/controller/ProjectController.java`
- `backend/src/main/java/com/deckgo/backend/workflow/controller/WorkflowSessionController.java`
- `backend/src/main/java/com/deckgo/backend/workflow/chat/ChatController.java`
- `backend/src/main/java/com/deckgo/backend/workflow/chat/ChatProgressController.java`
- `backend/src/main/java/com/deckgo/backend/studio/service/ProjectStudioService.java`
- `backend/src/main/java/com/deckgo/backend/workflow/service/WorkflowSessionService.java`
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- `backend/src/main/resources/db/migration/V2__workflow_svg_schema.sql`
- `backend/src/main/resources/db/migration/V3__workflow_context_reorder.sql`
- `backend/src/main/resources/db/migration/V4__chat_orchestrator.sql`
- `backend/src/main/resources/db/migration/V5__remove_legacy_deckspec_pipeline.sql`
- `backend/src/main/resources/db/migration/V6__project_page_tracking.sql`
