<template>
  <main class="h-screen bg-[#f8f9fa] text-slate-800">
    <section v-if="loading" class="flex h-full items-center justify-center text-sm text-slate-500">正在加载编辑器...</section>
    <section v-else-if="error" class="flex h-full items-center justify-center text-sm text-rose-600">{{ error }}</section>
    <section v-else-if="!project" class="flex h-full items-center justify-center text-sm text-slate-500">项目不存在</section>

    <div v-else class="flex h-full flex-col">
      <header class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white shadow-sm">
        <div class="flex h-full w-56 shrink-0 items-center justify-center border-r border-slate-200">
          <div class="flex w-[220px] rounded-xl border border-slate-200/50 bg-slate-100 p-1">
            <button
              v-for="item in surfaces"
              :key="item.value"
              class="flex-1 rounded-lg py-1.5 text-xs font-semibold transition-all"
              :class="surface === item.value ? 'border border-slate-200/50 bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'"
              @click="surface = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="flex flex-1 flex-wrap items-center gap-3 px-6 font-semibold text-slate-800">
          {{ activePage?.pageCode || project.title }}
          <StageBadge v-if="activePage" label="research" :value="activePage.searchStatus" />
          <StageBadge v-if="activePage" label="summary" :value="activePage.summaryStatus" />
          <StageBadge v-if="activePage" label="planning" :value="activePage.draftStatus" />
          <StageBadge v-if="activePage" label="design" :value="activePage.designStatus" />
        </div>

        <div class="flex shrink-0 items-center gap-3 px-6">
          <button
            class="rounded-xl border px-4 py-2.5 text-sm font-semibold transition-all"
            :class="storyboardOpen ? 'border-blue-200 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-700 hover:bg-slate-100'"
            @click="storyboardOpen = !storyboardOpen"
          >
            便利贴
          </button>
          <button
            class="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40"
            :disabled="!hasPresentation"
            @click="presentationOpen = true"
          >
            放映
          </button>
          <button
            class="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40"
            :disabled="!stageAction"
            @click="handleStageAction"
          >
            {{ stageAction?.label || "批量动作" }}
          </button>
          <button class="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100" @click="goHome">
            返回
          </button>
        </div>
      </header>

      <div v-if="storyboardOpen" class="flex min-h-0 flex-1 overflow-hidden">
        <StoryboardPanel
          :pages="project.pages"
          :active-page-id="activePage?.id || null"
          :surface="surface"
          @close="storyboardOpen = false"
          @jump="handleStoryboardJump"
        />
      </div>

      <div v-else class="flex min-h-0 flex-1 overflow-hidden">
        <aside class="flex w-56 shrink-0 flex-col border-r border-slate-200 bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-100 p-4 text-sm">
            <span class="font-semibold text-slate-800">幻灯片</span>
            <span class="font-medium text-slate-400">共 {{ project.pages.length }} 张</span>
          </div>
          <div class="flex-1 space-y-3 overflow-y-auto p-3">
            <PageThumbnail
              v-for="page in project.pages"
              :key="page.id"
              :page="page"
              :active="page.id === activePage?.id"
              :surface="surface"
              :markup="thumbnailMarkup(page)"
              :placeholder="thumbnailPlaceholder(page)"
              @select="activePageId = page.id"
            />
          </div>
        </aside>

        <section class="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f3f4f6]">
          <div class="flex items-center justify-between gap-6 border-b border-slate-200 bg-white px-8 py-5">
            <div>
              <div class="text-xs uppercase tracking-wide text-slate-400">{{ activePage?.pageRole || "content" }} / {{ activePage?.partTitle || "未分组" }}</div>
              <div class="text-2xl font-semibold text-slate-800">{{ activePage?.pageCode || "未选择页面" }}</div>
            </div>
            <div v-if="surface === 'research'" class="flex flex-wrap justify-end gap-2">
              <span class="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium text-slate-600">搜索结果 · {{ pipelineSummary.total }}</span>
              <span class="rounded-full border px-2.5 py-1 text-[11px] font-medium" :class="pipelineSummary.readReady ? 'border-emerald-100 bg-emerald-50 text-emerald-700' : 'border-amber-100 bg-amber-50 text-amber-700'">
                全文完成 · {{ pipelineSummary.readReady }}/{{ pipelineSummary.total }}
              </span>
              <span class="rounded-full border px-2.5 py-1 text-[11px] font-medium" :class="pipelineSummary.vectorReady ? 'border-blue-100 bg-blue-50 text-blue-700' : 'border-amber-100 bg-amber-50 text-amber-700'">
                向量完成 · {{ pipelineSummary.vectorReady }}/{{ pipelineSummary.total }}
              </span>
            </div>
          </div>

          <div class="flex-1 overflow-auto p-8">
            <div v-if="surface === 'research'" class="space-y-6">
              <article class="space-y-4 rounded-[2rem] border border-slate-200 bg-white p-6 shadow-sm">
                <div class="flex items-center justify-between">
                  <div>
                    <div class="text-lg font-semibold text-slate-800">当前页资料池</div>
                    <div class="mt-1 text-sm text-slate-500">资料摘要、全文抓取和向量化状态会持续回写到这里。</div>
                  </div>
                  <div class="text-sm text-slate-500">文档 {{ pipelineSummary.readReady }} / 向量 {{ pipelineSummary.vectorReady }}</div>
                </div>
                <div v-if="searchSources.length" class="space-y-4">
                  <SearchResultCard
                    v-for="(item, index) in searchSources"
                    :key="`${index}-${String(item.title || item.url || 'source')}`"
                    :item="item"
                    :allow-retry="true"
                    @retry="handleRetrySearchResult(String((item as Record<string, unknown>).id || `source-${index + 1}`))"
                  />
                </div>
                <div v-else class="text-sm text-slate-400">当前页还没有资料池结果。右侧聊天栏会实时显示推进进度。</div>
              </article>

              <article class="space-y-4 rounded-[2rem] border border-slate-200 bg-white p-6 shadow-sm">
                <div class="flex items-center justify-between">
                  <div class="text-lg font-semibold text-slate-800">当前页 summary 预览</div>
                  <button class="rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50" @click="dataModalOpen = true">
                    在弹窗中编辑
                  </button>
                </div>
                <div v-if="activePage?.pageSummaryMd || activePage?.currentResearch" class="rounded-2xl border border-slate-100 bg-slate-50 p-4 text-sm leading-relaxed text-slate-600">
                  <pre class="overflow-x-auto whitespace-pre-wrap">{{ activePage?.pageSummaryMd || formatJson(activePage?.currentResearch) }}</pre>
                </div>
                <div v-else class="text-sm text-slate-400">当前页 summary 尚未生成。</div>
              </article>
            </div>

            <SvgCanvas v-else :markup="previewMarkup" :placeholder="surface === 'planning' ? '当前页策划稿尚未生成' : '当前页设计稿尚未生成'" />
          </div>
        </section>

        <aside class="flex w-[440px] shrink-0 flex-col border-l border-slate-200 bg-white shadow-sm">
          <div class="shrink-0 space-y-3 border-b border-slate-100 bg-white px-4 py-4">
            <div class="text-xs uppercase tracking-wide text-slate-400">页面动作</div>
            <div class="flex flex-wrap gap-2">
              <button
                v-if="surface === 'research'"
                class="rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
                :disabled="!researchAction"
                @click="researchAction && runStageCommand(researchAction.command)"
              >
                {{ researchAction?.label || "推进当前阶段" }}
              </button>
              <button
                v-if="surface === 'planning'"
                class="rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
                :disabled="!canAdvanceToDesign"
                @click="runStageCommand('CONTINUE_TO_DESIGN')"
              >
                推进到设计稿
              </button>
              <button
                v-if="surface === 'design' && activePage"
                class="rounded-xl bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
                @click="handleRedesign"
              >
                重设计当前页
              </button>
              <button
                class="rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
                :disabled="!activePage"
                @click="dataModalOpen = true"
              >
                手动编辑原数据
              </button>
            </div>
          </div>

          <div class="flex-1 overflow-y-auto bg-slate-50/50 p-5">
            <ActivityTimeline :events="events" :runs="project.projectRuns" :messages="visibleMessages" />
          </div>

          <div class="space-y-3 border-t border-slate-100 bg-white p-5">
            <div v-if="error" class="text-sm text-red-600">{{ error }}</div>
            <div class="flex items-end rounded-2xl border border-slate-200 bg-slate-50 p-2.5 transition-all focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-100">
              <textarea
                v-model="messageInput"
                rows="1"
                class="min-h-[44px] max-h-32 flex-1 resize-none border-none bg-transparent px-3 py-2.5 text-sm text-slate-700 outline-none"
                :placeholder="surface === 'research' ? '例如：补充当前页研究重点，或继续推进到策划阶段' : surface === 'planning' ? '例如：重生成这一页策划稿，强调数据对比' : '例如：重生成设计稿，保留结构但增强层次'"
                @keydown="handleChatKeydown"
              />
              <button
                class="p-2.5 text-blue-600 transition-colors hover:text-blue-700 disabled:cursor-not-allowed disabled:text-slate-300"
                :disabled="sendingMessage || !messageInput.trim() || !activePage"
                @click="handleSendMessage"
              >
                {{ sendingMessage ? "..." : "发送" }}
              </button>
            </div>
            <div class="flex items-center justify-between text-[11px] text-slate-400">
              <div>页面上下文已绑定</div>
              <div>按 Enter 发送，Shift + Enter 换行</div>
            </div>
          </div>
        </aside>
      </div>

      <PresentationPlayer
        v-if="presentationOpen"
        :pages="presentationPages"
        :index="presentationIndex"
        :surface="surface === 'design' ? 'design' : 'draft'"
        @close="presentationOpen = false"
        @update:index="presentationIndex = $event"
      />
      <DataModal
        :open="dataModalOpen"
        :page="activePage"
        :surface="surface"
        :title-draft="titleDraft"
        :summary-draft="summaryDraft"
        :outline-draft="outlineDraft"
        @close="dataModalOpen = false"
        @save-outline="handleSaveOutline"
        @save-summary="handleSaveSummary"
        @update:title-draft="titleDraft = $event"
        @update:summary-draft="summaryDraft = $event"
        @update:outline-draft="outlineDraft = $event"
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ActivityTimeline from "../components/ppt/ActivityTimeline.vue";
import { DataModal, SearchResultCard, StageBadge, SvgCanvas } from "../components/ppt/EditorBits.vue";
import PageThumbnail from "../components/ppt/PageThumbnail.vue";
import PresentationPlayer from "../components/ppt/PresentationPlayer.vue";
import StoryboardPanel from "../components/ppt/StoryboardPanel.vue";
import {
  connectProjectEventStream,
  getProject,
  getStudioProject,
  listProjectMessages,
  listProjectPages,
  patchPageSummary,
  redesignProjectPage,
  retryPageSearchResult,
  runProjectCommand,
  sendProjectMessage,
  type ProjectEvent,
  type ProjectPage,
  type StudioProject
} from "../api";

const route = useRoute();
const router = useRouter();

const project = ref<StudioProject | null>(null);
const activePageId = ref("");
const surface = ref<"research" | "planning" | "design">("research");
const loading = ref(true);
const refreshing = ref(false);
const sendingMessage = ref(false);
const error = ref("");
const messageInput = ref("");
const storyboardOpen = ref(false);
const presentationOpen = ref(false);
const presentationIndex = ref(0);
const dataModalOpen = ref(false);
const titleDraft = ref("");
const outlineDraft = ref("");
const summaryDraft = ref("");
const events = ref<ProjectEvent[]>([]);
let disconnect: (() => void) | null = null;
let refreshInFlight: Promise<void> | null = null;
let refreshPending = false;

const surfaces = [
  { value: "research", label: "研究" },
  { value: "planning", label: "策划" },
  { value: "design", label: "设计稿" }
] as const;

const activePage = computed<ProjectPage | null>(() => {
  return project.value?.pages.find((page) => page.id === activePageId.value) ?? project.value?.pages[0] ?? null;
});

const previewMarkup = computed(() => {
  if (!activePage.value) return "";
  if (surface.value === "design") return activePage.value.currentDesignSvg || activePage.value.currentDraftSvg || "";
  if (surface.value === "planning") return activePage.value.currentDraftSvg || activePage.value.currentDesignSvg || "";
  return activePage.value.currentDesignSvg || activePage.value.currentDraftSvg || "";
});

const visibleMessages = computed(() =>
  (project.value?.messages ?? []).filter((message) => !message.targetPageId || message.targetPageId === activePage.value?.id)
);
const presentationPages = computed(() => (project.value?.pages ?? []).filter((page) => page.currentDraftSvg || page.currentDesignSvg));
const searchSources = computed(() => {
  if (Array.isArray(activePage.value?.pageSearchResults)) {
    return activePage.value?.pageSearchResults ?? [];
  }
  const sources = activePage.value?.currentResearch?.sources;
  return Array.isArray(sources) ? sources : [];
});
const canAdvanceToResearch = computed(() => project.value?.currentStage === "OUTLINE");
const canAdvanceToPlanning = computed(() => project.value?.currentStage === "RESEARCH");
const canAdvanceToDesign = computed(() => project.value?.currentStage === "PLANNING");
const hasPresentation = computed(() => (project.value?.pages ?? []).some((page) => page.currentDesignSvg || page.currentDraftSvg));
const researchAction = computed(() => {
  if (canAdvanceToResearch.value) {
    return { label: "推进到研究", command: "CONTINUE_TO_RESEARCH" };
  }
  if (canAdvanceToPlanning.value) {
    return { label: "推进到策划", command: "CONTINUE_TO_PLANNING" };
  }
  return null;
});

const stageAction = computed(() => {
  if (canAdvanceToResearch.value) {
    return { label: "批量研究", command: "CONTINUE_TO_RESEARCH" };
  }
  if (canAdvanceToPlanning.value) {
    return { label: "批量策划", command: "CONTINUE_TO_PLANNING" };
  }
  if (canAdvanceToDesign.value) {
    return { label: "批量设计", command: "CONTINUE_TO_DESIGN" };
  }
  return null;
});

const pipelineSummary = computed(() => {
  const items = searchSources.value;
  const total = items.length;
  const readReady = items.filter((item) => {
    const status = String((item as Record<string, unknown>).read_status ?? "").toLowerCase();
    return status === "ready" || status === "reused";
  }).length;
  const vectorReady = items.filter((item) => String((item as Record<string, unknown>).vector_status ?? "").toLowerCase() === "ready").length;
  return { total, readReady, vectorReady };
});

onMounted(async () => {
  await scheduleRefresh(false);
  const projectId = String(route.params.projectId || "");
  if (projectId) {
    disconnect = connectProjectEventStream(projectId, {
      onEvent: async (event) => {
        events.value = [event, ...events.value].slice(0, 30);
        void scheduleRefresh(true);
      }
    });
  }
});

onBeforeUnmount(() => {
  disconnect?.();
});

watch(activePage, () => {
  syncDraftFields();
});

async function refreshProject(silent = false) {
  const projectId = String(route.params.projectId || "");
  if (!projectId) {
    error.value = "缺少项目 ID";
    return;
  }
  if (!silent) loading.value = true;
  refreshing.value = true;
  error.value = "";

  try {
    const [projectSummary, studioProject, pages, messages] = await Promise.all([
      getProject(projectId),
      getStudioProject(projectId),
      listProjectPages(projectId),
      listProjectMessages(projectId)
    ]);
    if (projectSummary.currentStage === "DISCOVERY") {
      router.replace(`/projects/${projectId}/start`);
      return;
    }
    project.value = {
      ...studioProject,
      pages,
      messages
    };
    activePageId.value = activePageId.value || pages[0]?.id || "";
    surface.value = studioProject.currentStage === "DESIGN" ? "design" : studioProject.currentStage === "PLANNING" ? "planning" : "research";
    syncDraftFields();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "编辑器加载失败";
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function scheduleRefresh(silent = true) {
  if (refreshInFlight) {
    refreshPending = true;
    return refreshInFlight;
  }

  refreshInFlight = (async () => {
    try {
      await refreshProject(silent);
    } finally {
      refreshInFlight = null;
      if (refreshPending) {
        refreshPending = false;
        void scheduleRefresh(true);
      }
    }
  })();

  return refreshInFlight;
}

async function runStageCommand(command: string) {
  const projectId = String(route.params.projectId || "");
  if (!projectId) return;
  try {
    project.value = await runProjectCommand(projectId, command);
    if (!activePageId.value) {
      activePageId.value = project.value.pages[0]?.id || "";
    }
    await scheduleRefresh(true);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "阶段推进失败";
  }
}

async function handleStageAction() {
  if (!stageAction.value) return;
  await runStageCommand(stageAction.value.command);
}

async function handleSendMessage() {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !messageInput.value.trim() || sendingMessage.value) return;
  sendingMessage.value = true;
  try {
    project.value = await sendProjectMessage(projectId, messageInput.value.trim(), {
      scopeType: activePage.value ? "PAGE" : "PROJECT",
      targetPageId: activePage.value?.id ?? null,
      uiSurface: surface.value.toUpperCase()
    });
    messageInput.value = "";
    await scheduleRefresh(true);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "消息发送失败";
  } finally {
    sendingMessage.value = false;
  }
}

async function handleRedesign() {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !activePage.value) return;
  try {
    const updated = await redesignProjectPage(projectId, activePage.value.id);
    if (!project.value) return;
    project.value = {
      ...project.value,
      pages: project.value.pages.map((page) => (page.id === updated.id ? updated : page))
    };
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "重设计失败";
  }
}

async function handleRetrySearchResult(sourceId: string) {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !activePage.value) return;
  try {
    const updated = await retryPageSearchResult(projectId, activePage.value.id, sourceId);
    if (!project.value) return;
    project.value = {
      ...project.value,
      pages: project.value.pages.map((page) => (page.id === updated.id ? updated : page))
    };
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "重试资料失败";
  }
}

function syncDraftFields() {
  titleDraft.value = activePage.value?.title || activePage.value?.pageCode || "";
  outlineDraft.value = formatJson(activePage.value?.currentBrief);
  summaryDraft.value = activePage.value?.pageSummaryMd || formatJson(activePage.value?.currentResearch);
}

function handleSaveOutline() {
  dataModalOpen.value = false;
}

function handleSaveSummary() {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !activePage.value) {
    dataModalOpen.value = false;
    return;
  }
  void (async () => {
    try {
      const updated = await patchPageSummary(projectId, activePage.value!.id, summaryDraft.value);
      if (!project.value) return;
      project.value = {
        ...project.value,
        pages: project.value.pages.map((page) => (page.id === updated.id ? updated : page))
      };
      dataModalOpen.value = false;
    } catch (exception) {
      error.value = exception instanceof Error ? exception.message : "保存 summary 失败";
    }
  })();
}

function thumbnailMarkup(page: ProjectPage) {
  if (surface.value === "design") return page.currentDesignSvg || page.currentDraftSvg || "";
  if (surface.value === "planning") return page.currentDraftSvg || page.currentDesignSvg || "";
  return "";
}

function thumbnailPlaceholder(page: ProjectPage) {
  if (surface.value === "research") return page.pageCode;
  if (surface.value === "planning") return page.currentDraftSvg ? "" : "策划稿尚未生成";
  return page.currentDesignSvg ? "" : "设计稿尚未生成";
}

function handleStoryboardJump(payload: { pageId: string; surface: "research" | "planning" | "design" }) {
  activePageId.value = payload.pageId;
  surface.value = payload.surface;
  storyboardOpen.value = false;
}

function handleChatKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    void handleSendMessage();
  }
}

function formatJson(value: unknown) {
  if (!value) return "当前阶段还没有结构化数据。";
  return JSON.stringify(value, null, 2);
}

function goHome() {
  router.push("/");
}
</script>
