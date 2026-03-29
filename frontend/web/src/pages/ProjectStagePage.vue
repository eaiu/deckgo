<template>
  <main class="project-stage-screen">
    <section v-if="loading && !project" class="studio-loading">
      <p>正在加载背景调研结果...</p>
    </section>

    <section v-else-if="!project" class="studio-loading">
      <p>{{ error || "项目不存在" }}</p>
    </section>

    <div v-else class="project-stage-shell">
      <header class="panel project-stage-hero">
        <div class="project-stage-hero-main">
          <p class="eyebrow">Stage 01</p>
          <div class="project-stage-hero-top">
            <div class="project-stage-copy">
              <h1>{{ project.title || "未命名项目" }}</h1>
              <p class="project-stage-request">{{ project.requestText || project.topic }}</p>
            </div>

            <div class="stage-pill">
              <span class="stage-dot"></span>
              <strong>{{ stageLabel(project.currentStage) }}</strong>
            </div>
          </div>

          <div class="project-stage-meta">
            <span>模板 {{ project.stylePreset || project.templateId }}</span>
            <span v-if="project.pageCountTarget">目标 {{ project.pageCountTarget }} 页</span>
            <span>更新于 {{ formatDateTime(project.updatedAt) }}</span>
          </div>
        </div>

        <div class="project-stage-actions">
          <button class="ghost-button" type="button" @click="goHome">返回首页</button>
          <button class="primary-button" type="button" @click="loadProject" :disabled="loading">
            {{ loading ? "刷新中..." : "刷新" }}
          </button>
        </div>
      </header>

      <div class="project-stage-layout">
        <section class="project-stage-column">
          <article class="panel project-stage-card">
            <div class="project-stage-card-head">
              <div>
                <p class="eyebrow">Background</p>
                <h2>背景调研</h2>
              </div>
              <span class="project-stage-status">{{ project.requirementForm?.status || "UNKNOWN" }}</span>
            </div>

            <div class="project-stage-summary">
              <p>{{ primarySummary }}</p>
              <p v-if="secondarySummary" class="project-stage-summary-secondary">{{ secondarySummary }}</p>
            </div>

            <div v-if="backgroundSources.length" class="project-stage-source-list">
              <article v-for="source in backgroundSources" :key="`${source.url}-${source.title}`" class="project-stage-source-card">
                <div class="project-stage-source-head">
                  <strong>{{ source.title || sourceHost(source.url) }}</strong>
                  <span>{{ sourceHost(source.url) }}</span>
                </div>
                <p>{{ summarizeText(source.content, 180) }}</p>
                <a :href="source.url" target="_blank" rel="noreferrer">查看来源</a>
              </article>
            </div>

            <p v-else class="project-stage-empty">当前没有外部来源，已使用本地兜底背景摘要。</p>
          </article>

          <article class="panel project-stage-card">
            <div class="project-stage-card-head">
              <div>
                <p class="eyebrow">Messages</p>
                <h2>消息记录</h2>
              </div>
            </div>

            <div class="message-list">
              <article v-for="message in project.messages" :key="message.id" class="message-card" :class="messageClass(message.role)">
                <div class="message-meta">
                  <span>{{ messageRoleLabel(message.role) }} · {{ stageLabel(message.stage) }}</span>
                  <time :datetime="message.createdAt">{{ formatDateTime(message.createdAt) }}</time>
                </div>
                <p class="message-text">{{ message.contentMd }}</p>
              </article>
            </div>
          </article>
        </section>

        <aside class="project-stage-column project-stage-side">
          <article class="panel project-stage-card">
            <div class="project-stage-card-head">
              <div>
                <p class="eyebrow">Discovery</p>
                <h2>确认问题</h2>
              </div>
              <span class="project-stage-status">{{ discoveryQuestions.length }} 个问题</span>
            </div>

            <div v-if="discoveryQuestions.length" class="question-list">
              <article v-for="question in discoveryQuestions" :key="question.id" class="question-card">
                <h4>{{ question.prompt }}</h4>
                <ul class="project-stage-option-list">
                  <li v-for="option in question.options" :key="option.id">
                    <strong>{{ option.label }}</strong>
                    <span>{{ option.description }}</span>
                  </li>
                </ul>
              </article>
            </div>

            <p v-else class="project-stage-empty">背景调研已完成，待生成澄清问题。</p>
          </article>

          <article class="panel project-stage-card">
            <div class="project-stage-card-head">
              <div>
                <p class="eyebrow">Constraints</p>
                <h2>固定约束</h2>
              </div>
            </div>

            <div v-if="fixedItems.length" class="project-stage-fixed-list">
              <div v-for="item in fixedItems" :key="item.key" class="project-stage-fixed-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>

            <p v-else class="project-stage-empty">当前没有额外的固定约束。</p>
          </article>

          <article class="panel project-stage-card">
            <div class="project-stage-card-head">
              <div>
                <p class="eyebrow">Runs</p>
                <h2>阶段运行</h2>
              </div>
            </div>

            <div v-if="project.projectRuns.length" class="project-stage-run-list">
              <article v-for="run in project.projectRuns" :key="run.id" class="project-stage-run-card">
                <div class="project-stage-run-head">
                  <strong>{{ stageLabel(run.stage) }}</strong>
                  <span>{{ run.status }}</span>
                </div>
                <p>第 {{ run.attemptNo }} 次执行</p>
                <p>开始于 {{ formatDateTime(run.startedAt) }}</p>
                <p v-if="run.finishedAt">结束于 {{ formatDateTime(run.finishedAt) }}</p>
                <p v-if="run.outputRef" class="project-stage-run-note">{{ summarizeJson(run.outputRef) }}</p>
                <p v-if="run.errorMessage" class="project-stage-run-error">{{ run.errorMessage }}</p>
              </article>
            </div>

            <p v-else class="project-stage-empty">当前还没有阶段运行记录。</p>
          </article>
        </aside>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { fetchProject, type ProjectMessageDto, type ProjectStudioSnapshotDto } from "../api";

const route = useRoute();
const router = useRouter();

const project = ref<ProjectStudioSnapshotDto | null>(null);
const loading = ref(false);
const error = ref("");

const backgroundSummary = computed(() => project.value?.requirementForm?.initSearchResults ?? null);
const backgroundSources = computed(() => backgroundSummary.value?.sources ?? []);
const discoveryQuestions = computed(() => project.value?.requirementForm?.aiQuestions?.questions ?? []);

const primarySummary = computed(() =>
  backgroundSummary.value?.answer
  || backgroundSummary.value?.summary
  || project.value?.requirementForm?.summaryMd
  || "背景调研已开始，但摘要尚未返回。"
);

const secondarySummary = computed(() =>
  backgroundSummary.value?.topicUnderstanding || project.value?.requirementForm?.outlineContextMd || ""
);

const fixedItems = computed(() => {
  const raw = project.value?.requirementForm?.fixedItems ?? {};
  return Object.entries(raw).map(([key, value]) => ({
    key,
    label: fixedItemLabel(key),
    value: renderValue(value)
  }));
});

onMounted(loadProject);

async function loadProject() {
  const projectId = String(route.params.projectId || "");
  if (!projectId) {
    error.value = "缺少项目 ID";
    return;
  }

  loading.value = true;
  error.value = "";

  try {
    project.value = await fetchProject(projectId);
  } catch (exception) {
    project.value = null;
    error.value = exception instanceof Error ? exception.message : "加载项目失败";
  } finally {
    loading.value = false;
  }
}

function goHome() {
  router.push("/");
}

function stageLabel(stage: string) {
  switch (stage) {
    case "DISCOVERY":
      return "背景调研";
    case "OUTLINE":
      return "大纲策划";
    case "RESEARCH":
      return "资料搜集";
    case "PLANNING":
      return "页面策划";
    case "DESIGN":
      return "最终设计";
    default:
      return stage;
  }
}

function messageRoleLabel(role: string) {
  switch (role) {
    case "USER":
      return "你";
    case "ASSISTANT":
      return "DeckGo";
    case "SYSTEM":
      return "系统";
    default:
      return role;
  }
}

function messageClass(role: ProjectMessageDto["role"]) {
  return role === "ASSISTANT" ? "role-assistant" : "";
}

function fixedItemLabel(key: string) {
  switch (key) {
    case "pageCountTarget":
      return "目标页数";
    case "stylePreset":
      return "风格预设";
    case "backgroundAssetPath":
      return "背景资源";
    case "workflowConstraints":
      return "工作流约束";
    default:
      return key;
  }
}

function renderValue(value: unknown) {
  if (value === null || value === undefined) return "--";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function summarizeJson(value: Record<string, unknown>) {
  const text = JSON.stringify(value);
  return text.length > 160 ? `${text.slice(0, 160)}...` : text;
}

function summarizeText(value: string | undefined, maxLength = 140) {
  if (!value) return "暂无摘要";
  return value.length > maxLength ? `${value.slice(0, maxLength).trim()}...` : value;
}

function sourceHost(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "source";
  }
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}
</script>
