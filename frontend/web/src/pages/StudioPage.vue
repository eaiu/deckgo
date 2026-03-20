<template>
  <main v-if="session" class="studio-shell">
    <aside class="panel studio-rail">
      <header class="studio-rail-header">
        <div class="studio-project-copy">
          <p class="eyebrow">Studio</p>
          <h1>{{ session.project.title }}</h1>
          <p class="studio-project-topic">{{ session.project.topic }}</p>
        </div>

        <div class="studio-project-meta">
          <div class="stage-pill">
            <span class="stage-dot"></span>
            <strong>{{ workflowStageLabel(session.currentStage) }}</strong>
          </div>
          <p class="studio-project-updated">最近更新 {{ formatTime(session.updatedAt) }}</p>
        </div>
      </header>

      <section class="studio-thread">
        <div class="studio-thread-scroll">
          <section class="studio-block">
            <div class="studio-block-title">
              <h2>对话线程</h2>
              <p>{{ session.messages.length }} 条消息，沉淀了这轮工作台里的每个判断</p>
            </div>

            <div class="studio-message-list">
              <article
                v-for="message in session.messages"
                :key="message.id"
                class="message-card studio-message"
                :class="`role-${message.role.toLowerCase()}`"
              >
                <div class="message-card-top">
                  <span class="message-role">{{ messageRoleLabel(message.role) }}</span>
                  <span class="message-time">{{ formatTime(message.createdAt) }}</span>
                </div>
                <p class="message-text">{{ messageText(message) }}</p>
                <div class="message-bubble-foot">
                  <span>{{ workflowStageLabel(message.stage) }}</span>
                  <span>{{ messageMetaLabel(message) }}</span>
                </div>
              </article>
            </div>
          </section>
        </div>
      </section>

      <section class="studio-composer">
        <div class="studio-composer-header">
          <div class="studio-composer-copy">
            <p class="studio-composer-kicker">Next Move</p>
            <h2>当前操作</h2>
            <p>{{ composerDescription }}</p>
          </div>
          <span class="studio-composer-stage">{{ workflowStageLabel(session.currentStage) }}</span>
        </div>

        <template v-if="session.currentStage === 'DISCOVERY' && session.discoveryCard">
          <textarea
            v-model="freeformAnswer"
            class="field-textarea studio-composer-textarea"
            rows="4"
            :placeholder="session.discoveryCard.freeformHint ?? '补充你的额外要求'"
          />
          <div class="studio-composer-footer single-action">
            <button class="primary-button" :disabled="loading" @click="submitDiscovery">
              {{ loading ? "提交中..." : "提交需求并生成大纲" }}
            </button>
          </div>
        </template>

        <template v-else-if="session.currentStage === 'OUTLINE'">
          <textarea
            v-model="outlineFeedback"
            class="field-textarea studio-composer-textarea"
            rows="4"
            placeholder="例如：把第二部分改成更偏方案说明，最后一页改成行动建议"
          />
          <div class="studio-composer-footer">
            <button class="ghost-button" :disabled="loading || !outlineFeedback.trim()" @click="applyOutlineFeedback">
              应用修改
            </button>
            <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_RESEARCH')">
              {{ loading ? "生成中..." : "继续资料搜集" }}
            </button>
          </div>
        </template>

        <template v-else-if="session.currentStage === 'RESEARCH'">
          <p class="studio-inline-note">逐页 research 已完成，可以继续收束为页面策划稿。</p>
          <div class="studio-composer-footer single-action">
            <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_PLANNING')">
              {{ loading ? "生成中..." : "继续生成策划稿" }}
            </button>
          </div>
        </template>

        <template v-else-if="session.currentStage === 'PLANNING'">
          <p class="studio-inline-note">页面结构已经成形，下一步只需要推进视觉表达。</p>
          <div class="studio-composer-footer single-action">
            <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_DESIGN')">
              {{ loading ? "生成中..." : "继续生成设计稿" }}
            </button>
          </div>
        </template>

        <template v-else>
          <p class="studio-inline-note">当前流程已经完成，可以在右侧切换页面查看最终设计稿。</p>
        </template>

        <div v-if="error" class="studio-error-card">
          <strong>操作失败</strong>
          <p>{{ error }}</p>
        </div>
      </section>
    </aside>

    <section class="panel studio-stage">
      <header class="studio-stage-header">
        <div class="studio-stage-copy">
          <p class="eyebrow">{{ workflowStageLabel(session.currentStage) }}</p>
          <h2>{{ stageTitle }}</h2>
          <p>{{ stageDescription }}</p>
        </div>

        <div class="studio-stage-tools">
          <div class="studio-stage-badges">
            <span class="studio-badge">{{ pageCount }} 页</span>
            <span class="studio-badge">{{ session.selectedTemplateId }}</span>
            <span class="studio-badge">{{ statusLabel(session.status) }}</span>
          </div>
        </div>
      </header>

      <div class="studio-stage-body">
        <section v-if="session.currentStage === 'DISCOVERY'" class="workspace-view">
          <div class="workspace-top-grid">
            <article class="workspace-card workspace-hero">
              <p class="workspace-kicker">Background Pulse</p>
              <h3>{{ session.discoveryCard?.title ?? "背景摘要" }}</h3>
              <p>{{ summarizeText(backgroundNarrative, 420) }}</p>
              <div class="workspace-note-box">
                <strong>当前任务</strong>
                <p>{{ session.discoveryCard?.description ?? "确认背景理解、页数和语气，再进入大纲阶段。" }}</p>
              </div>
            </article>

            <article class="workspace-card workspace-summary-card">
              <div class="workspace-summary-head">
                <h3>这轮要确认什么</h3>
                <span>{{ session.discoveryCard?.questions.length ?? 0 }} 个问题</span>
              </div>
              <div class="workspace-meta-grid">
                <div class="workspace-meta-item">
                  <span>背景来源</span>
                  <strong>{{ session.backgroundSummary?.sources?.length ?? 0 }} 条</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>项目受众</span>
                  <strong>{{ session.project.audience || "待确认" }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>当前状态</span>
                  <strong>{{ statusLabel(session.status) }}</strong>
                </div>
              </div>
              <div class="workspace-note-box compact">
                <strong>自由补充</strong>
                <p>{{ session.discoveryCard?.freeformHint ?? "如果有额外限制、风格偏好或页数要求，可以直接补充。" }}</p>
              </div>
            </article>
          </div>

          <div class="workspace-question-grid">
            <article
              v-for="(question, index) in session.discoveryCard?.questions ?? []"
              :key="question.id"
              class="workspace-card workspace-list-card"
            >
              <div class="workspace-summary-head">
                <h3>{{ question.prompt }}</h3>
                <span>问题 {{ index + 1 }}</span>
              </div>

              <div class="option-list">
                <button
                  v-for="option in question.options"
                  :key="option.id"
                  type="button"
                  class="option-button"
                  :class="{ active: discoverySelections[question.id] === option.id }"
                  @click="discoverySelections[question.id] = option.id"
                >
                  <strong>{{ option.label }}</strong>
                  <span>{{ option.description }}</span>
                </button>
              </div>
            </article>
          </div>

          <article v-if="session.backgroundSummary?.sources?.length" class="workspace-card workspace-list-card">
            <div class="workspace-summary-head">
              <h3>参考来源</h3>
              <span>{{ session.backgroundSummary.sources.length }} 条背景线索</span>
            </div>
            <div class="workspace-source-list">
              <a
                v-for="source in session.backgroundSummary.sources.slice(0, 6)"
                :key="`${source.url}-${source.title}`"
                class="workspace-source-card"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
              >
                <div class="workspace-source-meta">
                  <strong>{{ source.title }}</strong>
                  <span class="workspace-source-domain">{{ sourceHost(source.url) }}</span>
                </div>
                <p>{{ summarizeText(source.content, 140) }}</p>
              </a>
            </div>
          </article>
        </section>

        <section v-else-if="session.currentStage === 'OUTLINE'" class="workspace-view">
          <div class="workspace-top-grid">
            <article class="workspace-card workspace-hero">
              <p class="workspace-kicker">Narrative Spine</p>
              <h3>{{ session.outline?.title ?? "大纲结构" }}</h3>
              <p>{{ summarizeText(session.outline?.narrative, 360) }}</p>
              <div class="workspace-note-box">
                <strong>当前选中</strong>
                <p>{{ selectedOutlinePage ? summarizeText(selectedOutlinePage.intent, 150) : "从右侧或下方页面卡中选择一个章节页面查看重点。" }}</p>
              </div>
            </article>

            <article class="workspace-card workspace-summary-card">
              <div class="workspace-summary-head">
                <h3>结构概览</h3>
                <span>{{ outlinePageCount }} 页</span>
              </div>
              <div class="workspace-meta-grid">
                <div class="workspace-meta-item">
                  <span>章节数</span>
                  <strong>{{ session.outline?.sections.length ?? 0 }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>当前聚焦</span>
                  <strong>{{ selectedOutlineSection?.title ?? "未选择" }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>用户动作</span>
                  <strong>可直接改写</strong>
                </div>
              </div>
            </article>
          </div>

          <div class="outline-board">
            <article
              v-for="section in session.outline?.sections ?? []"
              :key="section.id"
              class="workspace-card outline-board-card"
              :class="{ active: isOutlineSectionActive(section.id) }"
            >
              <div class="outline-board-head">
                <div>
                  <p class="workspace-kicker">Section</p>
                  <h3>{{ section.title }}</h3>
                </div>
                <span class="outline-count">{{ section.pages.length }} 页</span>
              </div>

              <p>{{ section.revisionNote ? `修订提示：${section.revisionNote}` : "这一章节已经形成稳定叙事，可以继续微调页面意图。" }}</p>

              <div class="outline-page-grid">
                <button
                  v-for="page in section.pages"
                  :key="page.id"
                  type="button"
                  class="outline-page-card"
                  :class="{ active: page.id === selectedPageId }"
                  @click="selectedPageId = page.id"
                >
                  <strong>{{ page.title }}</strong>
                  <span>{{ summarizeText(page.intent, 84) }}</span>
                </button>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="session.currentStage === 'RESEARCH'" class="workspace-view">
          <div class="workspace-top-grid">
            <article class="workspace-card workspace-hero">
              <p class="workspace-kicker">Evidence Stack</p>
              <h3>逐页研究结果已回填</h3>
              <p>{{ selectedResearchItem ? summarizeText(selectedResearchItem.findings, 360) : "每一页都带着查询词、搜索深度和来源，方便继续收束为页面结构。" }}</p>
              <div class="workspace-note-box">
                <strong>当前页</strong>
                <p>{{ selectedResearchItem ? selectedResearchItem.title : "从右侧切换需要查看的 research 页面。" }}</p>
              </div>
            </article>

            <article class="workspace-card workspace-summary-card">
              <div class="workspace-summary-head">
                <h3>研究概览</h3>
                <span>{{ session.pageResearch?.length ?? 0 }} 页</span>
              </div>
              <div class="workspace-meta-grid">
                <div class="workspace-meta-item">
                  <span>需检索页面</span>
                  <strong>{{ researchedSearchCount }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>累计来源</span>
                  <strong>{{ researchSourceCount }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>选中页面</span>
                  <strong>{{ selectedResearchItem?.title ?? "未选择" }}</strong>
                </div>
              </div>
            </article>
          </div>

          <div class="workspace-column-grid">
            <article
              v-for="item in session.pageResearch ?? []"
              :key="item.pageId"
              class="workspace-card workspace-list-card research-panel"
              :class="{ active: item.pageId === selectedPageId }"
            >
              <div class="workspace-summary-head">
                <h3>{{ item.title }}</h3>
                <span>{{ item.sources.length }} 条来源</span>
              </div>

              <p>{{ summarizeText(item.findings, 180) }}</p>

              <div class="workspace-meta-grid">
                <div class="workspace-meta-item">
                  <span>检索方式</span>
                  <strong>{{ item.needsSearch ? "需要外部搜索" : "直接使用现有背景" }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>搜索深度</span>
                  <strong>{{ searchDepthLabel(item.searchDepth) }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>主要意图</span>
                  <strong>{{ summarizeText(item.searchIntent, 46) }}</strong>
                </div>
              </div>

              <ul v-if="item.queries.length" class="studio-bullet-list compact">
                <li v-for="query in item.queries.slice(0, 3)" :key="query">{{ query }}</li>
              </ul>
            </article>
          </div>
        </section>

        <section v-else class="workspace-view">
          <article class="workspace-card preview-card">
            <div class="preview-card-head">
              <div>
                <p class="workspace-kicker">{{ session.currentStage === "DESIGN" ? "Final Canvas" : "Planning Canvas" }}</p>
                <h3>{{ currentPage?.title ?? "当前阶段还没有可展示的页面" }}</h3>
              </div>
              <span v-if="currentPage" class="preview-stage-pill">{{ layoutLabel(currentPage.pagePlan.layout) }}</span>
            </div>

            <div class="studio-preview-shell">
              <div v-if="currentPage" class="slide-frame studio-slide-frame" v-html="currentSvg"></div>
              <div v-else class="workspace-empty-card">
                <p class="workspace-empty-copy">当前阶段还没有可展示的页面。</p>
              </div>
            </div>
          </article>

          <div v-if="currentPage" class="workspace-top-grid">
            <article class="workspace-card workspace-summary-card">
              <div class="workspace-summary-head">
                <h3>页面摘要</h3>
                <span>{{ currentPage.pagePlan.cards.length }} 个内容卡</span>
              </div>
              <p>{{ summarizeText(currentPage.pagePlan.goal, 220) }}</p>
              <div class="workspace-meta-grid">
                <div class="workspace-meta-item">
                  <span>布局</span>
                  <strong>{{ layoutLabel(currentPage.pagePlan.layout) }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>视觉语气</span>
                  <strong>{{ currentPage.pagePlan.visualTone || "沿用模板基调" }}</strong>
                </div>
                <div class="workspace-meta-item">
                  <span>讲述备注</span>
                  <strong>{{ currentPage.pagePlan.speakerNotes ? "已提供" : "暂无" }}</strong>
                </div>
              </div>
            </article>

            <article class="workspace-card page-plan-meta-card">
              <div class="workspace-summary-head">
                <h3>当前画布信息</h3>
                <span>Page {{ currentPage.orderIndex }}</span>
              </div>
              <div class="page-plan-meta-list">
                <div>
                  <span>项目模板</span>
                  <strong>{{ session.selectedTemplateId }}</strong>
                </div>
                <div>
                  <span>流程阶段</span>
                  <strong>{{ workflowStageLabel(session.currentStage) }}</strong>
                </div>
                <div>
                  <span>更新时间</span>
                  <strong>{{ formatTime(session.updatedAt) }}</strong>
                </div>
              </div>
            </article>
          </div>

          <div v-if="currentPage" class="page-plan-grid">
            <article
              v-for="card in currentPage.pagePlan.cards"
              :key="card.id"
              class="workspace-card page-plan-card"
              :class="cardEmphasisClass(card.emphasis)"
            >
              <div class="page-plan-card-head">
                <strong>{{ card.heading }}</strong>
                <span>{{ cardKindLabel(card.kind) }}</span>
              </div>
              <p>{{ summarizeText(card.body, 136) }}</p>
              <ul v-if="card.supportingPoints?.length" class="studio-bullet-list compact">
                <li v-for="point in card.supportingPoints.slice(0, 3)" :key="point">{{ point }}</li>
              </ul>
            </article>
          </div>
        </section>
      </div>
    </section>

    <aside class="panel studio-context">
      <div class="studio-context-header">
        <h2>{{ rightTitle }}</h2>
        <p>{{ rightDescription }}</p>
      </div>

      <div class="studio-context-scroll">
        <section class="context-progress-list">
          <article
            v-for="step in workflowSteps"
            :key="step.id"
            class="context-progress-card"
            :class="[workflowStageState(step.id), { active: step.id === session.currentStage }]"
          >
            <div class="workflow-card-top">
              <span class="workflow-card-index">0{{ step.order }}</span>
              <span class="workflow-card-status">{{ workflowStateLabel(step.id) }}</span>
            </div>
            <strong>{{ step.label }}</strong>
            <p>{{ step.description }}</p>
          </article>
        </section>

        <article class="context-project-card">
          <h3>项目摘要</h3>
          <dl class="context-project-list">
            <div>
              <dt>主题</dt>
              <dd>{{ session.project.topic }}</dd>
            </div>
            <div>
              <dt>受众</dt>
              <dd>{{ session.project.audience || "待确认" }}</dd>
            </div>
            <div>
              <dt>版本</dt>
              <dd>{{ session.currentVersionId || "尚未落盘" }}</dd>
            </div>
          </dl>
        </article>

        <template v-if="session.currentStage === 'DISCOVERY'">
          <article v-if="session.backgroundSummary?.sources?.length" class="context-project-card">
            <h3>背景来源</h3>
            <div class="context-source-list">
              <a
                v-for="source in session.backgroundSummary.sources.slice(0, 5)"
                :key="`${source.url}-${source.title}`"
                class="context-source-link"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
              >
                <strong>{{ source.title }}</strong>
                <span>{{ sourceHost(source.url) }}</span>
              </a>
            </div>
          </article>
        </template>

        <template v-else-if="session.currentStage === 'OUTLINE'">
          <div class="context-outline-list">
            <div v-for="section in session.outline?.sections ?? []" :key="section.id" class="context-outline-group">
              <button
                type="button"
                class="context-outline-trigger"
                :class="{ active: isOutlineSectionActive(section.id) }"
                @click="focusOutlineSection(section.id)"
              >
                <div>
                  <strong>{{ section.title }}</strong>
                  <span>{{ section.pages.length }} 页</span>
                </div>
              </button>

              <div class="context-outline-pages">
                <button
                  v-for="page in section.pages"
                  :key="page.id"
                  type="button"
                  class="context-outline-page"
                  :class="{ active: page.id === selectedPageId }"
                  @click="selectedPageId = page.id"
                >
                  <strong>{{ page.title }}</strong>
                  <span>{{ summarizeText(page.intent, 66) }}</span>
                </button>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="session.currentStage === 'RESEARCH'">
          <div class="context-outline-list">
            <button
              v-for="item in session.pageResearch ?? []"
              :key="item.pageId"
              type="button"
              class="context-outline-trigger"
              :class="{ active: item.pageId === selectedPageId }"
              @click="selectedPageId = item.pageId"
            >
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.sources.length }} 条来源</span>
              </div>
            </button>
          </div>

          <article v-if="selectedResearchItem?.sources.length" class="context-project-card">
            <h3>当前页来源</h3>
            <div class="context-source-list">
              <a
                v-for="source in selectedResearchItem.sources.slice(0, 5)"
                :key="`${source.url}-${source.title}`"
                class="context-source-link"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
              >
                <strong>{{ source.title }}</strong>
                <span>{{ sourceHost(source.url) }}</span>
              </a>
            </div>
          </article>
        </template>

        <template v-else>
          <article v-if="currentPage" class="context-project-card">
            <h3>当前页信息</h3>
            <dl class="context-project-list">
              <div>
                <dt>页面</dt>
                <dd>{{ currentPage.orderIndex }}. {{ currentPage.title }}</dd>
              </div>
              <div>
                <dt>布局</dt>
                <dd>{{ layoutLabel(currentPage.pagePlan.layout) }}</dd>
              </div>
              <div>
                <dt>内容卡</dt>
                <dd>{{ currentPage.pagePlan.cards.length }} 个</dd>
              </div>
            </dl>
          </article>

          <div v-if="showPageThumbnails" class="thumbnail-list studio-thumbnail-list">
            <button
              v-for="page in session.pages"
              :key="page.id"
              type="button"
              class="thumbnail-card studio-thumbnail-card"
              :class="{ active: page.id === selectedPageId }"
              @click="selectedPageId = page.id"
            >
              <div class="thumbnail-preview" v-html="thumbnailSvg(page)"></div>
              <div class="thumbnail-meta">
                <strong>{{ page.orderIndex }}. {{ page.title }}</strong>
                <span>{{ layoutLabel(page.pagePlan.layout) }}</span>
              </div>
            </button>
          </div>
        </template>
      </div>
    </aside>
  </main>

  <main v-else class="studio-loading">
    <p>{{ loading ? "正在加载工作台..." : error || "会话不存在" }}</p>
  </main>
</template>

<script setup lang="ts">
import type {
  OutlinePage,
  OutlineSection,
  PageCardKind,
  PageResearchItem,
  PageLayout,
  SvgPage,
  WorkflowCommandType,
  WorkflowMessage,
  WorkflowMessageRole,
  WorkflowSessionSnapshot,
  WorkflowSessionStatus,
  WorkflowStage
} from "@deckgo/deck-core";
import { workflowStageLabel } from "@deckgo/deck-core";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { fetchWorkflowSession, sendWorkflowCommand } from "../api";

const route = useRoute();

const timeFormatter = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit"
});

const workflowSteps: Array<{
  id: WorkflowStage;
  label: string;
  description: string;
  order: number;
}> = [
  { id: "DISCOVERY", label: "背景调研", description: "确认背景、页数和叙事方向。", order: 1 },
  { id: "OUTLINE", label: "大纲策划", description: "搭好叙事章节和页面意图。", order: 2 },
  { id: "RESEARCH", label: "资料搜集", description: "逐页回填事实、来源和证据。", order: 3 },
  { id: "PLANNING", label: "页面策划", description: "把每页拆成清晰的内容框架。", order: 4 },
  { id: "DESIGN", label: "最终设计稿", description: "完成视觉表达并交付画面。", order: 5 }
];

const session = ref<WorkflowSessionSnapshot | null>(null);
const loading = ref(false);
const error = ref("");
const selectedPageId = ref("");
const freeformAnswer = ref("");
const outlineFeedback = ref("");
const discoverySelections = reactive<Record<string, string>>({});

const currentStageIndex = computed(() =>
  workflowSteps.findIndex((step) => step.id === session.value?.currentStage)
);

const outlinePageCount = computed(() =>
  session.value?.outline?.sections.reduce((total, section) => total + section.pages.length, 0) ?? 0
);

const pageCount = computed(() => {
  if (session.value?.pages.length) {
    return session.value.pages.length;
  }
  if (outlinePageCount.value > 0) {
    return outlinePageCount.value;
  }
  return session.value?.pageResearch?.length ?? 0;
});

const currentPage = computed<SvgPage | null>(() => {
  if (!session.value?.pages.length) return null;
  return session.value.pages.find((page) => page.id === selectedPageId.value) ?? session.value.pages[0];
});

const currentSvg = computed(() => {
  if (!currentPage.value) return "";
  return session.value?.currentStage === "DESIGN"
    ? currentPage.value.finalSvg ?? currentPage.value.draftSvg ?? ""
    : currentPage.value.draftSvg ?? currentPage.value.finalSvg ?? "";
});

const selectedOutlineSection = computed<OutlineSection | null>(() => {
  if (!session.value?.outline?.sections?.length) return null;
  return (
    session.value.outline.sections.find((section) =>
      section.pages.some((page) => page.id === selectedPageId.value)
    ) ?? session.value.outline.sections[0]
  );
});

const selectedOutlinePage = computed<OutlinePage | null>(() => {
  if (!selectedOutlineSection.value) return null;
  return (
    selectedOutlineSection.value.pages.find((page) => page.id === selectedPageId.value) ??
    selectedOutlineSection.value.pages[0] ??
    null
  );
});

const selectedResearchItem = computed<PageResearchItem | null>(() => {
  if (!session.value?.pageResearch?.length) return null;
  return (
    session.value.pageResearch.find((item) => item.pageId === selectedPageId.value) ??
    session.value.pageResearch[0]
  );
});

const showPageThumbnails = computed(
  () => session.value?.currentStage === "PLANNING" || session.value?.currentStage === "DESIGN"
);

const researchedSearchCount = computed(
  () => session.value?.pageResearch?.filter((item) => item.needsSearch).length ?? 0
);

const researchSourceCount = computed(
  () => session.value?.pageResearch?.reduce((total, item) => total + item.sources.length, 0) ?? 0
);

const backgroundNarrative = computed(() => {
  if (!session.value?.backgroundSummary) return "系统会先汇总背景，再引导你确定页数、场景和表达方式。";
  return (
    session.value.backgroundSummary.answer ||
    session.value.backgroundSummary.topicUnderstanding ||
    session.value.backgroundSummary.summary
  );
});

const stageTitle = computed(() => {
  switch (session.value?.currentStage) {
    case "DISCOVERY":
      return "把主题摸清，再开始搭结构";
    case "OUTLINE":
      return "先把整套叙事顺下来";
    case "RESEARCH":
      return "把每一页需要的证据补齐";
    case "PLANNING":
      return "把内容拆成真正可设计的页面框架";
    case "DESIGN":
      return "把策划稿收束成最终视觉";
    default:
      return "工作流";
  }
});

const stageDescription = computed(() => {
  switch (session.value?.currentStage) {
    case "DISCOVERY":
      return "这一段不急着出稿，先把背景、篇幅、对象和叙事口径锁定。";
    case "OUTLINE":
      return "大纲阶段决定这套内容如何推进、每一页承担什么信息任务。";
    case "RESEARCH":
      return "Research 会把查询词、搜索深度和资料来源逐页绑定到内容结构上。";
    case "PLANNING":
      return "策划阶段把页面目标、布局和内容卡拆干净，方便后续视觉落地。";
    case "DESIGN":
      return "设计阶段只负责画面表达，不再回到结构层重复发散。";
    default:
      return "";
  }
});

const composerDescription = computed(() => {
  switch (session.value?.currentStage) {
    case "DISCOVERY":
      return "勾选系统问题并补充你的额外要求。";
    case "OUTLINE":
      return "可以继续改写大纲，也可以推进到资料搜集。";
    case "RESEARCH":
      return "研究结果已经齐了，现在继续生成策划稿。";
    case "PLANNING":
      return "策划稿已经成形，继续推进到设计稿。";
    case "DESIGN":
      return "设计流程已完成，可以在右侧切换页面复看。";
    default:
      return "";
  }
});

const rightTitle = computed(() => {
  switch (session.value?.currentStage) {
    case "DISCOVERY":
      return "参考与进度";
    case "OUTLINE":
      return "结构导航";
    case "RESEARCH":
      return "研究导航";
    case "PLANNING":
    case "DESIGN":
      return "页面轨道";
    default:
      return "当前上下文";
  }
});

const rightDescription = computed(() => {
  switch (session.value?.currentStage) {
    case "DISCOVERY":
      return "右侧保留背景来源和完整工作流节奏，方便随时回看。";
    case "OUTLINE":
      return "按章节和页面意图浏览这套 deck 的整体结构。";
    case "RESEARCH":
      return "逐页切换 research，查看每一页绑定了哪些来源。";
    case "PLANNING":
      return "在这里切换画布，同时保留当前页的结构摘要。";
    case "DESIGN":
      return "右侧就是最终页面导航，方便快速比对不同设计稿。";
    default:
      return "工作流信息";
  }
});

watch(
  () => [session.value?.pages, session.value?.outline?.sections, session.value?.pageResearch] as const,
  ([pages, sections, research]) => {
    const candidateIds = [
      ...(pages?.map((page) => page.id) ?? []),
      ...(sections?.flatMap((section) => section.pages.map((page) => page.id)) ?? []),
      ...(research?.map((item) => item.pageId) ?? [])
    ];

    if (!candidateIds.length) {
      selectedPageId.value = "";
      return;
    }

    if (!selectedPageId.value || !candidateIds.includes(selectedPageId.value)) {
      selectedPageId.value = candidateIds[0];
    }
  },
  { deep: true }
);

onMounted(loadSession);

async function loadSession() {
  loading.value = true;
  error.value = "";
  try {
    session.value = await fetchWorkflowSession(String(route.params.sessionId));
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载会话失败";
  } finally {
    loading.value = false;
  }
}

async function runCommand(
  command: WorkflowCommandType,
  extra: Partial<{ selectedOptionIds: string[]; freeformAnswer: string; feedback: string }> = {}
) {
  if (!session.value) return;
  loading.value = true;
  error.value = "";
  try {
    session.value = await sendWorkflowCommand(session.value.sessionId, {
      command,
      ...extra
    });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "执行命令失败";
  } finally {
    loading.value = false;
  }
}

async function submitDiscovery() {
  const selectedOptionIds = Object.values(discoverySelections).filter(Boolean);
  await runCommand("SUBMIT_DISCOVERY", {
    selectedOptionIds,
    freeformAnswer: freeformAnswer.value
  });
}

async function applyOutlineFeedback() {
  await runCommand("APPLY_OUTLINE_FEEDBACK", {
    feedback: outlineFeedback.value
  });
  outlineFeedback.value = "";
}

function workflowStageState(stage: WorkflowStage) {
  const stageIndex = workflowSteps.findIndex((step) => step.id === stage);
  if (stageIndex < 0 || currentStageIndex.value < 0) return "upcoming";
  if (stageIndex < currentStageIndex.value) return "complete";
  if (stageIndex === currentStageIndex.value) return "current";
  return "upcoming";
}

function workflowStateLabel(stage: WorkflowStage) {
  switch (workflowStageState(stage)) {
    case "complete":
      return "已完成";
    case "current":
      return "进行中";
    default:
      return "待推进";
  }
}

function isOutlineSectionActive(sectionId: string) {
  return selectedOutlineSection.value?.id === sectionId;
}

function focusOutlineSection(sectionId: string) {
  const targetSection = session.value?.outline?.sections.find((section) => section.id === sectionId);
  if (!targetSection?.pages.length) return;
  selectedPageId.value = targetSection.pages[0].id;
}

function thumbnailSvg(page: SvgPage) {
  return session.value?.currentStage === "DESIGN"
    ? page.finalSvg ?? page.draftSvg ?? ""
    : page.draftSvg ?? page.finalSvg ?? "";
}

function formatTime(value?: string | null) {
  if (!value) return "--:--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--:--";
  return timeFormatter.format(date);
}

function summarizeText(value: string | null | undefined, maxLength = 120) {
  const text = value?.trim();
  if (!text) return "暂无内容";
  return text.length > maxLength ? `${text.slice(0, maxLength).trim()}...` : text;
}

function sourceHost(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "source";
  }
}

function layoutLabel(layout: PageLayout) {
  switch (layout) {
    case "hero":
      return "Hero";
    case "two-column":
      return "双栏";
    case "three-column":
      return "三栏";
    case "comparison":
      return "对比";
    case "timeline":
      return "时间线";
    case "bento-grid":
      return "Bento";
    case "summary":
      return "总结页";
  }
}

function cardKindLabel(kind: PageCardKind) {
  switch (kind) {
    case "text":
      return "正文";
    case "metric":
      return "数据指标";
    case "chart":
      return "图表";
    case "table":
      return "表格";
    case "comparison":
      return "对比";
    case "timeline":
      return "时间线";
    case "quote":
      return "引述";
    case "image":
      return "图片";
    case "highlight":
      return "重点";
  }
}

function cardEmphasisClass(emphasis?: "high" | "medium" | "low") {
  switch (emphasis) {
    case "high":
      return "emphasis-high";
    case "medium":
      return "emphasis-medium";
    default:
      return "emphasis-low";
  }
}

function searchDepthLabel(depth: string) {
  switch (depth) {
    case "deep":
      return "深度检索";
    case "medium":
      return "中等检索";
    case "light":
      return "轻量检索";
    default:
      return depth || "未标注";
  }
}

function statusLabel(status: WorkflowSessionStatus) {
  switch (status) {
    case "WAITING_USER":
      return "等待确认";
    case "COMPLETED":
      return "已完成";
    case "FAILED":
      return "执行失败";
  }
}

function messageRoleLabel(role: WorkflowMessageRole) {
  switch (role) {
    case "USER":
      return "你";
    case "ASSISTANT":
      return "DeckGo";
    case "SYSTEM":
      return "系统";
  }
}

function messageMetaLabel(message: WorkflowMessage) {
  if (Array.isArray(message.content.selectedOptionIds) && message.content.selectedOptionIds.length > 0) {
    return `${message.content.selectedOptionIds.length} 个已选项`;
  }
  if (typeof message.content.freeformAnswer === "string" && message.content.freeformAnswer.trim()) {
    return "包含补充说明";
  }
  if (message.role === "ASSISTANT") {
    return "阶段反馈";
  }
  if (message.role === "SYSTEM") {
    return "系统记录";
  }
  return "用户输入";
}

function messageText(message: WorkflowMessage) {
  if (typeof message.content.text === "string" && message.content.text.trim()) {
    return message.content.text;
  }
  if (Array.isArray(message.content.selectedOptionIds) && message.content.selectedOptionIds.length > 0) {
    return message.content.selectedOptionIds.join(" / ");
  }
  if (typeof message.content.freeformAnswer === "string" && message.content.freeformAnswer.trim()) {
    return message.content.freeformAnswer;
  }
  return "已提交一条结构化消息";
}
</script>
