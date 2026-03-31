<template>
  <main class="min-h-screen bg-[#f8f9fa]">
    <section v-if="loading" class="mx-auto flex min-h-screen max-w-7xl items-center justify-center px-6 text-sm text-slate-400">
      正在准备初始化工作台...
    </section>

    <section v-else-if="error" class="mx-auto flex min-h-screen max-w-7xl items-center justify-center px-6 text-sm text-rose-600">
      {{ error }}
    </section>

    <section v-else-if="!project || !form" class="mx-auto flex min-h-screen max-w-7xl items-center justify-center px-6 text-sm text-slate-400">
      初始化数据不存在
    </section>

    <div v-else class="flex h-screen flex-col bg-[#f8f9fa]">
      <header class="z-10 flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 shadow-sm">
        <button class="flex items-center gap-2 text-sm font-medium text-slate-500 transition-colors hover:text-slate-800" @click="goHome">
          返回
        </button>
        <div class="font-semibold text-slate-800">项目初始化</div>
        <button
          class="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
          :disabled="!canConfirm || confirming"
          @click="handleConfirm"
        >
          {{ confirming ? "开始生成大纲..." : "生成大纲" }}
        </button>
      </header>

      <div class="flex min-h-0 flex-1 gap-6 overflow-hidden p-6">
        <section class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm">
          <div class="space-y-4 border-b border-slate-100 px-6 py-5">
            <div class="flex items-start justify-between gap-4">
              <div>
                <div class="text-xs uppercase tracking-wide text-slate-400">联网资料池</div>
                <div class="mt-1 text-xl font-semibold text-slate-800">首轮搜索结果与入库状态</div>
              </div>
              <div class="flex flex-wrap justify-end gap-2">
                <span class="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium text-slate-600">搜索结果 · {{ form.sources.length }}</span>
                <span class="rounded-full border border-emerald-100 bg-emerald-50 px-2.5 py-1 text-[11px] font-medium text-emerald-700">已完成摘要</span>
              </div>
            </div>
            <div class="text-sm leading-relaxed text-slate-500">
              背景摘要、来源线索和需求确认会在这个工作台完成。右侧时间线会持续显示最新 agent 事件和消息。
            </div>
          </div>

          <div class="min-h-0 flex-1 space-y-4 overflow-y-auto bg-slate-50/50 p-6">
            <article v-for="source in form.sources" :key="source.id" class="space-y-3 rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm">
              <div class="flex items-start justify-between gap-3">
                <div class="space-y-2 min-w-0">
                  <a :href="source.url" target="_blank" rel="noreferrer" class="block break-words text-base font-semibold text-blue-600 hover:underline">
                    {{ source.title }}
                  </a>
                  <div class="break-all text-xs text-emerald-600">{{ source.url }}</div>
                </div>
                <span class="shrink-0 rounded-full border border-slate-200 bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600">
                  Source
                </span>
              </div>
              <p class="whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-600 [overflow-wrap:anywhere]">
                {{ source.snippet || "当前没有摘要内容。" }}
              </p>
            </article>

            <div v-if="!form.sources.length" class="flex h-full items-center justify-center text-sm text-slate-400">
              当前还没有搜索结果，稍后会自动刷新。
            </div>
          </div>
        </section>

        <aside class="flex min-h-0 w-[460px] shrink-0 flex-col overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm">
          <div class="min-h-0 flex-1 space-y-6 overflow-y-auto bg-slate-50/50 p-5">
            <ActivityTimeline :events="events" :runs="project.projectRuns" :messages="project.messages" />

            <section class="space-y-5 rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm">
              <div class="flex items-center justify-between border-b border-slate-100 pb-3">
                <div class="flex items-center gap-2 text-sm font-semibold text-blue-600">
                  内容需求单
                </div>
                <div class="flex items-center gap-3">
                  <span class="text-xs text-slate-400">
                    {{ activeStep?.answered ? "已回答" : "待补充" }}
                  </span>
                  <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-500">
                    {{ currentRequirementPosition }}/{{ Math.max(requirementSteps.length, 1) }}
                  </span>
                </div>
              </div>

              <div class="space-y-4">
                <div class="flex items-start justify-between gap-3">
                  <div class="space-y-1">
                    <div class="text-lg font-semibold text-slate-800">{{ activeStep?.title || "等待问题生成" }}</div>
                    <div v-if="activeStep?.description" class="text-sm leading-relaxed text-slate-500">{{ activeStep.description }}</div>
                  </div>
                  <span
                    class="rounded-full px-3 py-1 text-xs font-medium"
                    :class="activeStep?.answered ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'"
                  >
                    {{ activeStep?.answered ? "已回答" : "待回答" }}
                  </span>
                </div>

                <div v-if="activeStep?.kind === 'page_count'" class="space-y-4">
                  <div class="grid grid-cols-2 gap-3">
                    <button
                      v-for="option in pageCountOptions"
                      :key="option.code"
                      class="rounded-xl px-4 py-3 text-left transition-all"
                      :class="selectedPageCount === option.value ? 'bg-blue-600 text-white shadow-sm' : 'border border-slate-200 hover:border-blue-400 hover:text-blue-600'"
                      @click="selectPageCount(option.value)"
                    >
                      <div class="font-semibold">{{ option.label }}</div>
                      <div class="mt-2 text-xs" :class="selectedPageCount === option.value ? 'text-blue-50/90' : 'text-slate-500'">{{ option.description }}</div>
                    </button>
                  </div>
                  <div class="space-y-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-4">
                    <div class="text-sm font-medium text-slate-700">自定义页数</div>
                    <div class="flex gap-2">
                      <input v-model="customPageCount" type="number" min="1" step="1" class="flex-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-500" placeholder="例如：12" />
                      <button class="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800" @click="saveCustomPageCount">保存</button>
                    </div>
                  </div>
                </div>

                <div v-else-if="activeStep?.kind === 'style_preset'" class="space-y-4">
                  <div class="grid grid-cols-1 gap-3">
                    <button
                      v-for="template in templates"
                      :key="template.id"
                      class="rounded-xl px-4 py-3 text-left transition-all"
                      :class="selectedStylePreset === template.id ? 'bg-slate-900 text-white shadow-sm' : 'border border-slate-200 hover:border-slate-400'"
                      @click="selectStylePreset(template.id)"
                    >
                      <div class="font-semibold">{{ template.name }}</div>
                      <div class="mt-2 text-xs" :class="selectedStylePreset === template.id ? 'text-slate-200' : 'text-slate-500'">
                        {{ template.description }}
                      </div>
                    </button>
                  </div>
                  <div class="space-y-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-4">
                    <div class="text-sm font-medium text-slate-700">自定义风格要求</div>
                    <div class="flex gap-2">
                      <input v-model="customStylePreset" class="flex-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-500" placeholder="例如：苹果发布会风格、银灰留白" />
                      <button class="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800" @click="saveCustomStylePreset">保存</button>
                    </div>
                  </div>
                  <label class="flex cursor-pointer items-center justify-between rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-4 transition-colors hover:border-blue-400 hover:bg-blue-50/40">
                    <div>
                      <div class="text-sm font-medium text-slate-700">{{ uploadedBackground || "上传背景资源" }}</div>
                      <div class="mt-1 text-xs text-slate-500">当前前端入口已预留，后续接背景资源上传接口。</div>
                    </div>
                    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">可选</span>
                    <input class="hidden" type="file" @change="handleBackgroundUpload" />
                  </label>
                </div>

                <div v-else-if="activeStep" class="space-y-4">
                  <div class="grid grid-cols-1 gap-3">
                    <button
                      v-for="option in activeStep.options"
                      :key="option.optionCode"
                      class="rounded-xl px-4 py-3 text-left transition-all"
                      :class="selectedQuestionOption(activeStep.questionCode) === option.optionCode ? 'bg-blue-600 text-white shadow-sm' : 'border border-slate-200 hover:border-blue-400 hover:text-blue-600'"
                      @click="selectQuestionOption(activeStep.questionCode, option.optionCode)"
                    >
                      <div class="font-medium">{{ option.label }}</div>
                      <div v-if="option.description" class="mt-2 text-xs" :class="selectedQuestionOption(activeStep.questionCode) === option.optionCode ? 'text-blue-50/90' : 'text-slate-500'">
                        {{ option.description }}
                      </div>
                    </button>
                  </div>
                  <div class="space-y-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-4">
                    <div class="text-sm font-medium text-slate-700">自定义答案</div>
                    <div class="flex gap-2">
                      <input v-model="customQuestionAnswers[activeStep.questionCode]" class="flex-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-500" placeholder="如果推荐项不合适，可以直接填写" />
                      <button class="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800" @click="saveCustomQuestionAnswer(activeStep.questionCode)">保存</button>
                    </div>
                  </div>
                </div>
              </div>

              <div class="flex items-center justify-between border-t border-slate-100 pt-4">
                <button
                  class="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                  :disabled="activeRequirementIndex === 0 || !requirementSteps.length"
                  @click="activeRequirementIndex = Math.max(0, activeRequirementIndex - 1)"
                >
                  上一项
                </button>

                <div class="flex items-center gap-2">
                  <button
                    v-for="(step, index) in requirementSteps"
                    :key="step.key"
                    class="h-2.5 rounded-full transition-all"
                    :class="index === activeRequirementIndex ? 'w-8 bg-blue-600' : step.answered ? 'w-2.5 bg-emerald-400' : 'w-2.5 bg-slate-300'"
                    :aria-label="`跳转到 ${step.title}`"
                    @click="activeRequirementIndex = index"
                  />
                </div>

                <button
                  class="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                  :disabled="!requirementSteps.length || activeRequirementIndex >= requirementSteps.length - 1"
                  @click="activeRequirementIndex = Math.min(requirementSteps.length - 1, activeRequirementIndex + 1)"
                >
                  下一项
                </button>
              </div>
            </section>
          </div>

          <div class="space-y-3 border-t border-slate-100 bg-white p-4">
            <div class="flex flex-wrap gap-2">
              <button
                v-for="label in suggestedActions"
                :key="label"
                class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-600 transition-colors hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700"
                @click="sendSuggestion(label)"
              >
                {{ label }}
              </button>
            </div>

            <div class="flex items-end rounded-xl border border-slate-200 bg-slate-50 p-2 transition-all focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-100">
              <textarea
                v-model="chatInput"
                rows="1"
                class="min-h-[44px] max-h-32 flex-1 resize-none border-none bg-transparent px-2 py-2.5 text-sm text-slate-700 outline-none"
                placeholder="例如：补充受众限制，或者要求重新做项目级搜索"
                @keydown="handleChatKeydown"
              />
              <button
                class="p-2.5 text-blue-600 transition-colors hover:text-blue-700 disabled:cursor-not-allowed disabled:text-slate-300"
                :disabled="sendingMessage || !chatInput.trim()"
                @click="handleSendMessage"
              >
                {{ sendingMessage ? "..." : "发送" }}
              </button>
            </div>

            <div class="flex items-center justify-between text-[11px] text-slate-400">
              <div>Question steps {{ requirementSteps.length }}</div>
              <div>Enter 发送，Shift + Enter 换行</div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import ActivityTimeline from "../components/ppt/ActivityTimeline.vue";
import {
  connectProjectEventStream,
  confirmRequirements,
  fetchTemplates,
  getProject,
  getRequirementForm,
  getStudioProject,
  patchRequirementAnswer,
  sendProjectMessage,
  uploadBackground,
  updateProject,
  type ProjectEvent,
  type ProjectStage,
  type StudioProject,
  type RequirementFormData,
  type TemplateOption
} from "../api";

type RequirementStep =
  | {
      key: "page_count_target";
      kind: "page_count";
      title: string;
      description: string;
      answered: boolean;
    }
  | {
      key: "style_preset";
      kind: "style_preset";
      title: string;
      description: string;
      answered: boolean;
    }
  | {
      key: string;
      kind: "question";
      questionCode: string;
      title: string;
      description?: string;
      answered: boolean;
      options: RequirementFormData["questions"][number]["options"];
    };

const route = useRoute();
const router = useRouter();

const project = ref<StudioProject | null>(null);
const form = ref<RequirementFormData | null>(null);
const templates = ref<TemplateOption[]>([]);
const loading = ref(true);
const refreshing = ref(false);
const confirming = ref(false);
const sendingMessage = ref(false);
const error = ref("");
const noteMd = ref("");
const chatInput = ref("");
const events = ref<ProjectEvent[]>([]);
const activeRequirementIndex = ref(0);
const customPageCount = ref("");
const customStylePreset = ref("");
const customQuestionAnswers = reactive<Record<string, string>>({});
const uploadedBackground = ref("");
let disconnect: (() => void) | null = null;
let refreshInFlight: Promise<void> | null = null;
let refreshPending = false;

const pageCountOptions = [
  { code: "count-5-10", value: 8, label: "5-10 页", description: "适合短汇报或快速说明" },
  { code: "count-10-15", value: 12, label: "10-15 页", description: "适合标准结构化介绍" },
  { code: "count-15-20", value: 16, label: "15-20 页", description: "适合完整讲解和深入展开" }
];

const selectedPageCount = computed(() => project.value?.pageCountTarget ?? null);
const selectedStylePreset = computed(() => project.value?.stylePreset ?? project.value?.templateId ?? "");

const requirementSteps = computed<RequirementStep[]>(() => {
  const dynamicQuestions =
    form.value?.questions.map((question) => ({
      key: question.questionCode,
      kind: "question" as const,
      questionCode: question.questionCode,
      title: question.label,
      description: question.description,
      answered: selectedQuestionOption(question.questionCode) !== "",
      options: question.options
    })) ?? [];

  return [
    {
      key: "page_count_target",
      kind: "page_count",
      title: "页数目标",
      description: "这是整份 PPT 的总页数，包含封面、目录、内容页和结尾页。",
      answered: selectedPageCount.value !== null
    },
    {
      key: "style_preset",
      kind: "style_preset",
      title: "风格预设",
      description: "设计稿阶段会优先采用这里选定的 style preset。",
      answered: Boolean(selectedStylePreset.value)
    },
    ...dynamicQuestions
  ];
});

const activeStep = computed(() => requirementSteps.value[activeRequirementIndex.value] ?? null);
const currentRequirementPosition = computed(() => (requirementSteps.value.length ? activeRequirementIndex.value + 1 : 0));
const canConfirm = computed(() => {
  return Boolean(selectedPageCount.value) && Boolean(selectedStylePreset.value) && requirementSteps.value.every((step) => step.answered);
});
const suggestedActions = computed(() => {
  if (form.value?.suggestedActions?.length) {
    return form.value.suggestedActions.map((item) => item.label);
  }
  return ["强调业务价值", "风格偏正式汇报", "更适合管理层汇报"];
});

onMounted(async () => {
  await scheduleRefresh(false);
  const projectId = String(route.params.projectId || "");
  if (projectId) {
    disconnect = connectProjectEventStream(projectId, {
      onEvent: async (event) => {
        events.value = [event, ...events.value].slice(0, 24);
        if (confirming.value) {
          return;
        }
        void scheduleRefresh(true);
      }
    });
  }
});

onBeforeUnmount(() => disconnect?.());

async function refreshAll(silent = false) {
  const projectId = String(route.params.projectId || "");
  if (!projectId) {
    error.value = "缺少项目 ID";
    return;
  }

  if (!silent) {
    loading.value = true;
  }
  refreshing.value = true;
  error.value = "";

  try {
    const [projectSummary, studioProject, requirementForm, templateList] = await Promise.all([
      getProject(projectId),
      getStudioProject(projectId),
      getRequirementForm(projectId),
      fetchTemplates()
    ]);

    if (projectSummary.currentStage !== "DISCOVERY") {
      router.replace(`/projects/${projectId}/editor`);
      return;
    }

    project.value = studioProject;
    form.value = requirementForm;
    templates.value = templateList;
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "初始化数据读取失败";
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
      await refreshAll(silent);
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

async function selectPageCount(value: number) {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !project.value) {
    return;
  }
  try {
    const code = value <= 8 ? "count-5-10" : value <= 12 ? "count-10-15" : "count-15-20";
    await patchRequirementAnswer(projectId, "page_count_target", [code]);
    await updateProject(projectId, {
      title: project.value.title,
      topic: project.value.requestText,
      audience: "待确认",
      requestText: project.value.requestText,
      pageCountTarget: value,
      templateId: project.value.templateId,
      stylePreset: project.value.stylePreset,
      backgroundAssetPath: project.value.backgroundAssetPath,
      workflowConstraints: null
    });
    await refreshAll(true);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "页数保存失败";
  }
}

async function saveCustomPageCount() {
  const value = Number(customPageCount.value);
  if (!Number.isInteger(value) || value <= 0) {
    error.value = "页数目标必须填写正整数";
    return;
  }
  await selectPageCount(value);
}

async function selectStylePreset(styleId: string) {
  const projectId = String(route.params.projectId || "");
  if (!projectId || !project.value) {
    return;
  }
  try {
    await patchRequirementAnswer(projectId, "style_preset", styleId);
    await updateProject(projectId, {
      title: project.value.title,
      topic: project.value.requestText,
      audience: "待确认",
      requestText: project.value.requestText,
      pageCountTarget: project.value.pageCountTarget,
      templateId: styleId,
      stylePreset: styleId,
      backgroundAssetPath: project.value.backgroundAssetPath,
      workflowConstraints: null
    });
    await refreshAll(true);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "风格保存失败";
  }
}

async function saveCustomStylePreset() {
  const value = customStylePreset.value.trim();
  if (!value) {
    error.value = "请先填写自定义风格要求";
    return;
  }
  await selectStylePreset(value);
}

async function selectQuestionOption(questionCode: string, optionCode: string) {
  const projectId = String(route.params.projectId || "");
  if (!projectId) {
    return;
  }
  try {
    form.value = await patchRequirementAnswer(projectId, questionCode, [optionCode]);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "答案保存失败";
  }
}

async function saveCustomQuestionAnswer(questionCode: string) {
  const projectId = String(route.params.projectId || "");
  const value = customQuestionAnswers[questionCode]?.trim();
  if (!projectId || !value) {
    error.value = "请先填写自定义答案";
    return;
  }
  try {
    form.value = await patchRequirementAnswer(projectId, questionCode, value);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "答案保存失败";
  }
}

function selectedQuestionOption(questionCode: string) {
  const raw = form.value?.answers?.questionAnswers as Record<string, unknown> | undefined;
  const selected = raw?.[questionCode];
  if (Array.isArray(selected)) {
    return typeof selected[0] === "string" ? selected[0] : "";
  }
  return typeof selected === "string" ? selected : "";
}

async function handleConfirm() {
  const projectId = String(route.params.projectId || "");
  if (!projectId || confirming.value) {
    return;
  }
  confirming.value = true;
  error.value = "";
  try {
    disconnect?.();
    disconnect = null;
    const snapshot = await confirmRequirements(projectId, noteMd.value.trim() || undefined);
    project.value = snapshot;
    router.replace(`/projects/${projectId}/editor`);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "生成大纲失败";
    if (!disconnect) {
      disconnect = connectProjectEventStream(projectId, {
        onEvent: async (event) => {
          events.value = [event, ...events.value].slice(0, 24);
          if (confirming.value) {
            return;
          }
          void scheduleRefresh(true);
        }
      });
    }
  } finally {
    confirming.value = false;
  }
}

async function handleSendMessage() {
  const projectId = String(route.params.projectId || "");
  const message = chatInput.value.trim();
  if (!projectId || !message || sendingMessage.value) {
    return;
  }
  sendingMessage.value = true;
  try {
    project.value = await sendProjectMessage(projectId, message, {
      scopeType: "PROJECT",
      targetPageId: null,
      uiSurface: "DISCOVERY"
    });
    chatInput.value = "";
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "消息发送失败";
  } finally {
    sendingMessage.value = false;
  }
}

function sendSuggestion(label: string) {
  chatInput.value = label;
  void handleSendMessage();
}

function handleBackgroundUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  const projectId = String(route.params.projectId || "");
  uploadedBackground.value = file.name;
  void (async () => {
    try {
      if (!projectId) {
        return;
      }
      const response = await uploadBackground(projectId, file);
      uploadedBackground.value = response.backgroundAssetPath;
      await refreshAll(true);
    } catch (exception) {
      error.value = exception instanceof Error ? exception.message : "背景资源上传失败";
    } finally {
      input.value = "";
    }
  })();
}

function handleChatKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    void handleSendMessage();
  }
}

function stageLabel(stage: ProjectStage) {
  if (stage === "DISCOVERY") return "初始化";
  if (stage === "OUTLINE") return "大纲";
  if (stage === "RESEARCH") return "研究";
  if (stage === "PLANNING") return "策划";
  if (stage === "DESIGN") return "设计";
  return stage;
}

function goHome() {
  router.push("/");
}
</script>
