<template>
  <main v-if="session" class="studio-shell" :class="{ 'has-pages': showRightPanel }">
    <!-- ═══ LEFT: Chat Sidebar ═══ -->
    <aside class="panel studio-chat">
      <header class="chat-header">
        <p class="eyebrow">Studio</p>
        <h1 class="chat-title">{{ session.project.title }}</h1>
        <div class="stage-pill">
          <span class="stage-dot"></span>
          <strong>{{ workflowStageLabel(session.currentStage) }}</strong>
        </div>
      </header>

      <div class="chat-thread">
        <div class="chat-scroll">
          <!-- ── Messages ── -->
          <article
            v-for="message in session.messages"
            :key="message.id"
            class="chat-bubble"
            :class="`role-${message.role.toLowerCase()}`"
          >
            <div class="bubble-top">
              <span class="bubble-role">{{ messageRoleLabel(message.role) }}</span>
              <span class="bubble-time">{{ formatTime(message.createdAt) }}</span>
            </div>
            <!-- Tool call steps -->
            <div v-if="message.toolCalls?.length" class="tool-steps">
              <div
                v-for="step in message.toolCalls"
                :key="step.id"
                class="tool-step"
                :class="{ expanded: expandedSteps.has(step.id) }"
                @click="toggleStep(step.id)"
              >
                <div class="tool-step-head">
                  <span class="tool-step-icon" :class="step.status">
                    {{ step.status === 'completed' ? '\u2713' : step.status === 'failed' ? '\u2717' : '\u25CF' }}
                  </span>
                  <span class="tool-step-label">{{ toolStepLabel(step.toolName) }}</span>
                  <span v-if="step.durationMs" class="tool-step-time">{{ step.durationMs }}ms</span>
                  <span class="tool-step-chevron">&#9660;</span>
                </div>
                <div v-if="expandedSteps.has(step.id)" class="tool-step-body">
                  <p v-if="step.summary">{{ step.summary }}</p>
                  <div v-if="step.subSteps?.length" class="tool-sub-steps">
                    <div v-for="sub in step.subSteps" :key="sub.label" class="tool-sub-step" :class="sub.status">
                      <span class="sub-step-icon"/>
                      <span>{{ sub.label }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <p class="bubble-text">{{ messageText(message) }}</p>
            <div class="bubble-foot">
              <span>{{ workflowStageLabel(message.stage) }}</span>
            </div>
          </article>

          <!-- ── Embedded: Discovery Question Card ── -->
          <template v-if="session.currentStage === 'DISCOVERY' && session.discoveryCard && discoveryQuestions.length">
            <div class="chat-embed">
              <div class="chat-embed-head">
                <span class="chat-embed-icon">&#9776;</span>
                <strong>内容需求单</strong>
                <div class="chat-embed-nav">
                  <button
                    type="button"
                    class="embed-nav-btn"
                    :disabled="currentQuestionIdx <= 0"
                    @click="currentQuestionIdx--"
                  >&larr;</button>
                  <button
                    type="button"
                    class="embed-nav-btn"
                    :disabled="currentQuestionIdx >= discoveryQuestions.length - 1"
                    @click="currentQuestionIdx++"
                  >&rarr;</button>
                </div>
                <span class="chat-embed-counter">{{ currentQuestionIdx + 1 }}/{{ discoveryQuestions.length }}</span>
              </div>
              <div class="chat-embed-body">
                <p class="chat-embed-qlabel">问题 {{ currentQuestionIdx + 1 }}</p>
                <h3 class="chat-embed-qtitle">{{ discoveryQuestions[currentQuestionIdx]?.prompt }}</h3>
                <div class="chat-embed-options">
                  <button
                    v-for="(opt, oi) in discoveryQuestions[currentQuestionIdx]?.options ?? []"
                    :key="opt.id"
                    type="button"
                    class="embed-option"
                    :class="{ active: discoverySelections[discoveryQuestions[currentQuestionIdx].id] === opt.id }"
                    @click="discoverySelections[discoveryQuestions[currentQuestionIdx].id] = opt.id"
                  >
                    <span class="embed-option-letter">{{ String.fromCharCode(65 + oi) }}</span>
                    {{ opt.label }}
                  </button>
                </div>
              </div>
              <div class="chat-embed-foot">
                <span class="embed-hint" :data-hint="discoveryEmbedHint"></span>
                <span class="embed-hint">需求单已提交，可翻页查看</span>
                <button
                  type="button"
                  class="embed-submit-btn"
                  :class="{ 'is-submitted': discoverySubmitted }"
                  :data-label="discoveryEmbedSubmitLabel"
                  :aria-label="discoveryEmbedSubmitLabel"
                  :disabled="loading"
                  @click="submitDiscovery"
                >{{ loading ? "提交中..." : "已提交" }}</button>
              </div>
            </div>
          </template>

          <!-- ── Embedded: Outline Status ── -->
          <template v-if="session.currentStage === 'OUTLINE' && session.outline">
            <div class="chat-status-line">
              <span class="chat-status-icon done">&#10003;</span>
              <span>大纲已生成，发送或点击"继续"进入下一步。</span>
            </div>
          </template>

          <!-- ── Embedded: Research Status ── -->
          <template v-if="session.currentStage === 'RESEARCH'">
            <div class="chat-status-line">
              <span class="chat-status-icon done">&#10003;</span>
              <span>逐页研究已完成（{{ researchSourceCount }} 条来源），可继续生成策划稿。</span>
            </div>
          </template>

          <!-- ── Embedded: Planning/Design Page Card ── -->
          <template v-if="(session.currentStage === 'PLANNING' || session.currentStage === 'DESIGN') && currentPage">
            <div class="chat-page-card">
              <span class="chat-page-icon">&#9998;</span>
              <span class="chat-page-num">P{{ currentPage.orderIndex }}</span>
              <span class="chat-page-dot">&middot;</span>
              <span class="chat-page-name">{{ currentPage.title }}</span>
              <span class="chat-page-badge">{{ session.currentStage === 'DESIGN' ? 'FINAL' : 'DRAFT' }}</span>
            </div>
          </template>

          <!-- ── Real-time progress (while request in-flight) ── -->
          <div v-if="loading" class="chat-progress">
            <div class="typing-dots"><span/><span/><span/></div>
            <div v-if="liveProgress" class="progress-tool">
              <span class="progress-spinner"/>
              <span>{{ liveProgress.description }}</span>
            </div>
            <div v-if="liveProgress?.subSteps?.length" class="progress-sub-steps">
              <div
                v-for="sub in liveProgress.subSteps"
                :key="sub.label"
                class="progress-sub"
                :class="sub.status"
              >
                <span class="sub-icon"/>
                <span>{{ sub.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ── Composer ── -->
      <div class="chat-composer">
        <textarea
          v-model="chatInput"
          class="chat-input"
          rows="2"
          placeholder="输入你的想法... 按 Enter 发送"
          @keydown.enter.exact.prevent="sendChat"
        />
        <div class="chat-actions">
          <button
            v-if="session.pages.length"
            class="chat-export-btn"
            @click="exportAllSvg"
          >导出全部 SVG</button>
          <button
            class="chat-send-btn"
            :disabled="loading || !chatInput.trim()"
            @click="sendChat"
          >{{ loading ? '处理中...' : '发送' }}</button>
        </div>

        <div v-if="error" class="chat-error">
          <strong>操作失败</strong>
          <p>{{ error }}</p>
        </div>

        <p class="chat-hint">按 Enter 发送，Shift + Enter 换行</p>
      </div>
    </aside>

    <!-- ═══ CENTER: Canvas ═══ -->
    <section class="panel studio-canvas">
      <!-- DISCOVERY: Background -->
      <div v-if="session.currentStage === 'DISCOVERY'" class="canvas-content">
        <div class="canvas-center-card">
          <p class="canvas-kicker">Background</p>
          <h2>{{ session.discoveryCard?.title ?? session.project.title }}</h2>
          <p class="canvas-desc">{{ summarizeText(backgroundNarrative, 400) }}</p>
          <div v-if="session.backgroundSummary?.sources?.length" class="canvas-sources">
            <p class="canvas-sources-label">{{ session.backgroundSummary.sources.length }} 条参考来源</p>
            <div class="canvas-source-grid">
              <a
                v-for="source in session.backgroundSummary.sources.slice(0, 4)"
                :key="`${source.url}-${source.title}`"
                class="canvas-source"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
              >
                <strong>{{ source.title }}</strong>
                <span>{{ sourceHost(source.url) }}</span>
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- OUTLINE: Outline Board -->
      <div v-else-if="session.currentStage === 'OUTLINE'" class="canvas-content">
        <div class="canvas-outline-head">
          <h2>{{ session.outline?.title ?? "大纲结构" }}</h2>
          <span class="canvas-badge">{{ outlinePageCount }} 页</span>
        </div>
        <div class="canvas-outline-board">
          <article
            v-for="section in session.outline?.sections ?? []"
            :key="section.id"
            class="outline-section-card"
            :class="{ active: isOutlineSectionActive(section.id) }"
          >
            <div class="outline-section-head">
              <h3>{{ section.title }}</h3>
              <span class="outline-section-count">{{ section.pages.length }} 页</span>
            </div>
            <div class="outline-section-pages">
              <button
                v-for="page in section.pages"
                :key="page.id"
                type="button"
                class="outline-page-btn"
                :class="{ active: page.id === selectedPageId }"
                @click="selectedPageId = page.id"
              >
                <strong>{{ page.title }}</strong>
                <span>{{ summarizeText(page.intent, 60) }}</span>
              </button>
            </div>
          </article>
        </div>
      </div>

      <!-- RESEARCH: Search Results -->
      <div v-else-if="session.currentStage === 'RESEARCH'" class="canvas-content">
        <div class="canvas-research-head">
          <h2>资料搜集</h2>
          <span class="canvas-badge">{{ session.pageResearch?.length ?? 0 }} 页 &middot; {{ researchSourceCount }} 条来源</span>
        </div>
        <div class="canvas-research-grid">
          <article
            v-for="item in session.pageResearch ?? []"
            :key="item.pageId"
            class="research-card"
            :class="{ active: item.pageId === selectedPageId }"
            @click="selectedPageId = item.pageId"
          >
            <div class="research-card-head">
              <strong>{{ item.title }}</strong>
              <span>{{ item.sources.length }} 条</span>
            </div>
            <p>{{ summarizeText(item.findings, 140) }}</p>
            <div v-if="item.queries.length" class="research-card-tags">
              <span v-for="q in item.queries.slice(0, 2)" :key="q" class="research-tag">{{ q }}</span>
            </div>
          </article>
        </div>
      </div>

      <!-- PLANNING / DESIGN: SVG Canvas -->
      <div v-else class="canvas-content canvas-preview">
        <template v-if="currentPage">
          <div class="canvas-preview-head">
            <div>
              <p class="canvas-kicker">{{ session.currentStage === 'DESIGN' ? 'FINAL CANVAS' : 'DRAFT CANVAS' }}</p>
              <h2>{{ currentPage.title }}</h2>
            </div>
            <span class="canvas-badge">{{ layoutLabel(currentPage.pagePlan.layout) }}</span>
          </div>
          <div class="canvas-slide-area">
            <div v-if="currentSvg" class="canvas-slide-frame" v-html="currentSvg"></div>
            <div v-else class="canvas-slide-loading">
              <div class="slide-loading-inner">
                <span class="progress-spinner large"/>
                <p>正在生成第 {{ currentPage.orderIndex }} 页...</p>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="canvas-empty-state">
          <p>{{ loading ? '页面生成中...' : '当前阶段还没有可展示的页面。' }}</p>
        </div>
      </div>
    </section>

    <!-- ═══ RIGHT: Page List (RESEARCH+ only) ═══ -->
    <aside v-if="showRightPanel" class="panel studio-pages">
      <div class="pages-header">
        <h2>幻灯片</h2>
        <span class="pages-count">共 {{ pageCount }} 页</span>
      </div>

      <div class="pages-scroll">
        <!-- Thumbnails (PLANNING/DESIGN) -->
        <template v-if="showPageThumbnails">
          <button
            v-for="page in session.pages"
            :key="page.id"
            type="button"
            class="page-thumb"
            :class="{ active: page.id === selectedPageId, generating: !thumbnailSvg(page) }"
            @click="selectedPageId = page.id"
          >
            <span class="page-thumb-num">{{ page.orderIndex }}</span>
            <div v-if="thumbnailSvg(page)" class="page-thumb-preview" v-html="thumbnailSvg(page)"></div>
            <div v-else class="page-thumb-skeleton">
              <span class="progress-spinner small"/>
            </div>
            <div class="page-thumb-meta">
              <strong>{{ page.title }}</strong>
              <span>{{ thumbnailSvg(page) ? layoutLabel(page.pagePlan.layout) : '生成中...' }}</span>
            </div>
          </button>
        </template>

        <!-- Research page list -->
        <template v-else-if="session.currentStage === 'RESEARCH'">
          <button
            v-for="item in session.pageResearch ?? []"
            :key="item.pageId"
            type="button"
            class="page-thumb"
            :class="{ active: item.pageId === selectedPageId }"
            @click="selectedPageId = item.pageId"
          >
            <div class="page-thumb-meta">
              <strong>{{ item.title }}</strong>
              <span>{{ item.sources.length }} 条来源</span>
            </div>
          </button>
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
  ToolProgressEvent,
  WorkflowCommandType,
  WorkflowMessage,
  WorkflowMessageRole,
  WorkflowSessionSnapshot,
  WorkflowSessionStatus,
  WorkflowStage
} from "@deckgo/deck-core";
import { workflowStageLabel } from "@deckgo/deck-core";
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { fetchWorkflowSession, sendChatMessage, sendWorkflowCommand, subscribeProgress } from "../api";

const route = useRoute();

const timeFormatter = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit"
});

const session = ref<WorkflowSessionSnapshot | null>(null);
const loading = ref(false);
const error = ref("");
const selectedPageId = ref("");
const freeformAnswer = ref("");
const outlineFeedback = ref("");
const discoverySelections = reactive<Record<string, string>>({});
const currentQuestionIdx = ref(0);
const chatInput = ref("");
const liveProgress = ref<ToolProgressEvent | null>(null);
const expandedSteps = reactive(new Set<string>());
let progressSource: EventSource | null = null;
let pollTimer: ReturnType<typeof setInterval> | null = null;

const discoveryQuestions = computed(() =>
  session.value?.discoveryCard?.questions ?? []
);

const discoverySubmitted = computed(() => {
  const answers = session.value?.discoveryAnswers;
  if (!answers) return false;
  return Boolean((answers.selectedOptionIds?.length ?? 0) > 0 || answers.freeformAnswer?.trim());
});

const answeredDiscoveryQuestionCount = computed(() =>
  discoveryQuestions.value.reduce(
    (count, question) => count + (discoverySelections[question.id] ? 1 : 0),
    0
  )
);

const discoveryEmbedHint = computed(() => {
  if (loading.value) return "\u63d0\u4ea4\u4e2d\uff0c\u6b63\u5728\u751f\u6210\u5927\u7eb2...";
  if (discoverySubmitted.value) return "\u9700\u6c42\u5355\u5df2\u63d0\u4ea4\uff0c\u53ef\u7ffb\u9875\u67e5\u770b";
  if (answeredDiscoveryQuestionCount.value > 0) {
    return `\u5df2\u56de\u7b54 ${answeredDiscoveryQuestionCount.value}/${discoveryQuestions.value.length} \u4e2a\u95ee\u9898\uff0c\u53ef\u7ee7\u7eed\u7ffb\u9875\u8865\u5145`;
  }
  return "\u9009\u62e9\u7b54\u6848\u540e\u63d0\u4ea4\uff0c\u7cfb\u7edf\u4f1a\u7ee7\u7eed\u751f\u6210\u5927\u7eb2";
});

const discoveryEmbedSubmitLabel = computed(() => {
  if (loading.value) return "\u63d0\u4ea4\u4e2d...";
  return discoverySubmitted.value ? "\u5df2\u63d0\u4ea4" : "\u63d0\u4ea4\u9700\u6c42";
});

const showRightPanel = computed(() => {
  const stage = session.value?.currentStage;
  return stage === "RESEARCH" || stage === "PLANNING" || stage === "DESIGN";
});

const outlinePageCount = computed(() =>
  session.value?.outline?.sections.reduce((total, section) => total + section.pages.length, 0) ?? 0
);

const pageCount = computed(() => {
  if (session.value?.pages.length) return session.value.pages.length;
  if (outlinePageCount.value > 0) return outlinePageCount.value;
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

const showPageThumbnails = computed(
  () => session.value?.currentStage === "PLANNING" || session.value?.currentStage === "DESIGN"
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

onUnmounted(() => {
  stopPolling();
  progressSource?.close();
});

async function loadSession() {
  loading.value = true;
  error.value = "";
  try {
    session.value = await fetchWorkflowSession(String(route.params.sessionId));
    // If backend is still processing, resume progress tracking
    if (session.value?.status === "PROCESSING") {
      startPolling();
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载会话失败";
  } finally {
    loading.value = false;
  }
}

function startPolling() {
  loading.value = true;
  // Subscribe to SSE for live progress
  if (!progressSource && session.value) {
    progressSource = subscribeProgress(session.value.sessionId, (event) => {
      liveProgress.value = event;
    });
  }
  // Poll session state every 3 seconds until no longer PROCESSING
  if (!pollTimer) {
    pollTimer = setInterval(async () => {
      try {
        const fresh = await fetchWorkflowSession(String(route.params.sessionId));
        session.value = fresh;
        if (fresh.status !== "PROCESSING") {
          stopPolling();
        }
      } catch {
        // Ignore poll errors
      }
    }, 3000);
  }
}

function stopPolling() {
  loading.value = false;
  liveProgress.value = null;
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (progressSource) {
    progressSource.close();
    progressSource = null;
  }
}

async function sendChat() {
  if (!session.value || !chatInput.value.trim()) return;
  error.value = "";
  liveProgress.value = null;
  const message = chatInput.value;
  chatInput.value = "";

  // Start polling + SSE immediately
  startPolling();

  try {
    const response = await sendChatMessage(session.value.sessionId, message);
    session.value = response.sessionSnapshot;
    stopPolling();
  } catch (exception) {
    // If request timed out but backend is still processing, keep polling silently
    if (exception instanceof DOMException && exception.name === "TimeoutError") {
      // Don't show error — polling will continue tracking progress
      return;
    }
    if (exception instanceof DOMException && exception.name === "AbortError") {
      return;
    }
    error.value = exception instanceof Error ? exception.message : "发送失败";
    stopPolling();
  }
}

function toggleStep(stepId: string) {
  if (expandedSteps.has(stepId)) {
    expandedSteps.delete(stepId);
  } else {
    expandedSteps.add(stepId);
  }
}

function toolStepLabel(toolName: string): string {
  const labels: Record<string, string> = {
    generateOutline: "生成大纲",
    reviseOutline: "修改大纲",
    generatePageResearch: "逐页资料搜集",
    generatePagePlans: "生成页面策划稿",
    generateFinalDesign: "生成最终设计稿",
    redesignPage: "修改页面设计",
    getSessionContext: "读取会话状态"
  };
  return labels[toolName] ?? toolName;
}

async function runCommand(
  command: WorkflowCommandType,
  extra: Partial<{ selectedOptionIds: string[]; freeformAnswer: string; feedback: string }> = {}
) {
  if (!session.value) return;
  loading.value = true;
  error.value = "";
  try {
    session.value = await sendWorkflowCommand(session.value.sessionId, { command, ...extra });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "执行命令失败";
  } finally {
    loading.value = false;
  }
}

async function submitDiscovery() {
  const selectedOptionIds = Object.values(discoverySelections).filter(Boolean);
  await runCommand("SUBMIT_DISCOVERY", { selectedOptionIds, freeformAnswer: freeformAnswer.value });
}

async function applyOutlineFeedback() {
  await runCommand("APPLY_OUTLINE_FEEDBACK", { feedback: outlineFeedback.value });
  outlineFeedback.value = "";
}

function exportAllSvg() {
  if (!session.value?.pages.length) return;
  import("jszip").then(({ default: JSZip }) => {
    const zip = new JSZip();
    for (const page of session.value!.pages) {
      const svg = page.finalSvg ?? page.draftSvg;
      if (!svg) continue;
      const name = `${String(page.orderIndex).padStart(2, "0")}-${page.title}.svg`;
      zip.file(name, svg);
    }
    zip.generateAsync({ type: "blob" }).then((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${session.value!.project.title}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    });
  });
}

function isOutlineSectionActive(sectionId: string) {
  return selectedOutlineSection.value?.id === sectionId;
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
    case "hero": return "Hero";
    case "two-column": return "双栏";
    case "three-column": return "三栏";
    case "comparison": return "对比";
    case "timeline": return "时间线";
    case "bento-grid": return "Bento";
    case "summary": return "总结页";
  }
}

function messageRoleLabel(role: WorkflowMessageRole) {
  switch (role) {
    case "USER": return "你";
    case "ASSISTANT": return "DeckGo";
    case "SYSTEM": return "系统";
  }
}

function messageText(message: WorkflowMessage) {
  if (typeof message.content.text === "string" && message.content.text.trim()) return message.content.text;
  if (Array.isArray(message.content.selectedOptionIds) && message.content.selectedOptionIds.length > 0) {
    return message.content.selectedOptionIds.join(" / ");
  }
  if (typeof message.content.freeformAnswer === "string" && message.content.freeformAnswer.trim()) {
    return message.content.freeformAnswer;
  }
  return "已提交一条结构化消息";
}
</script>
