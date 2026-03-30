<template>
  <main class="min-h-screen bg-[#eef2f6] text-slate-800">
    <div class="mx-auto max-w-5xl px-6 pb-20 pt-28">
      <h1 class="mb-12 text-center text-5xl font-bold tracking-tight text-slate-800">AI PPT 生成助手</h1>

      <section
        class="mx-auto mb-16 max-w-3xl rounded-[2rem] border border-slate-100 bg-white p-5 shadow-lg shadow-slate-200/50 transition-shadow hover:shadow-xl hover:shadow-slate-200/50"
        :class="error ? 'mb-4' : 'mb-20'"
      >
        <div class="flex items-start gap-4">
          <div class="pt-1 text-slate-400">
            <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.8-3.8" />
            </svg>
          </div>
          <textarea
            v-model="requestText"
            rows="5"
            class="min-h-[160px] w-full resize-none bg-transparent text-lg leading-7 text-slate-700 outline-none placeholder:text-slate-400"
            placeholder="输入你的 PPT 需求，例如：生成一份关于 2024 年人工智能发展趋势的报告..."
            @keydown="handleComposerKeydown"
          />
        </div>

        <div class="mt-4 flex flex-col gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:items-end sm:justify-between">
          <div class="text-xs leading-5 text-slate-400">Enter 换行，Ctrl/Cmd + Enter 开始生成</div>
          <button
            class="inline-flex h-12 items-center justify-center gap-2 self-end rounded-xl bg-blue-600 px-6 font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
            :disabled="creating || !requestText.trim()"
            @click="handleCreateProject"
          >
            <svg
              v-if="creating"
              viewBox="0 0 24 24"
              class="h-5 w-5 animate-spin"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M21 12a9 9 0 1 1-2.64-6.36" />
            </svg>
            <svg v-else viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14" />
            </svg>
            开始生成
          </button>
        </div>
      </section>

      <p v-if="error" class="mx-auto mb-16 max-w-3xl text-sm text-red-600">{{ error }}</p>

      <section class="mx-auto max-w-4xl">
        <h2 class="mb-6 flex items-center gap-2 text-xl font-semibold text-slate-700">
          <svg viewBox="0 0 24 24" class="h-5 w-5 text-slate-400" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v6l3 2" />
          </svg>
          最近项目
        </h2>

        <div class="grid grid-cols-1 gap-6 md:grid-cols-3">
          <div
            v-if="loading"
            class="rounded-2xl border border-slate-100 bg-white p-5 text-sm text-slate-500 shadow-sm md:col-span-3"
          >
            正在读取最近项目...
          </div>
          <div
            v-else-if="!projects.length"
            class="rounded-2xl border border-slate-100 bg-white p-5 text-sm text-slate-500 shadow-sm md:col-span-3"
          >
            还没有项目，先创建一个任务。
          </div>

          <template v-else>
            <article
              v-for="project in projects"
              :key="project.projectId"
              class="group cursor-pointer rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition-all hover:border-blue-100 hover:shadow-md"
              @click="openProject(project)"
            >
              <div class="relative mb-4 aspect-video w-full overflow-hidden rounded-xl border border-slate-100 bg-slate-50 transition-colors group-hover:border-blue-100">
                <div v-if="project.previewSvgMarkup" class="absolute inset-0 bg-white [&_svg]:h-full [&_svg]:w-full" v-html="project.previewSvgMarkup" />
                <div v-else class="absolute inset-0 flex items-center justify-center transition-colors group-hover:bg-blue-50/50">
                  <svg viewBox="0 0 24 24" class="h-9 w-9 text-slate-300 transition-colors group-hover:text-blue-300" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16l4-2 4 2 4-2 4 2V8z" />
                  </svg>
                </div>
                <span
                  class="absolute right-3 top-3 rounded-md px-2 py-1 text-[10px] font-medium text-white backdrop-blur-md"
                  :class="previewTone(project)"
                >
                  {{ previewLabel(project) }}
                </span>
              </div>

              <h3 class="line-clamp-2 font-medium text-slate-800 transition-colors group-hover:text-blue-600">
                {{ project.title }}
              </h3>
              <p class="mt-1.5 text-sm text-slate-400">{{ formatDate(project.updatedAt) }}</p>
            </article>
          </template>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { createProject, listProjects, type ProjectSummary } from "../api";

const router = useRouter();

const requestText = ref("");
const projects = ref<ProjectSummary[]>([]);
const loading = ref(false);
const creating = ref(false);
const error = ref("");

onMounted(loadProjects);

async function loadProjects() {
  loading.value = true;
  error.value = "";
  try {
    projects.value = await listProjects();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "最近项目读取失败";
  } finally {
    loading.value = false;
  }
}

async function handleCreateProject() {
  if (!requestText.value.trim() || creating.value) {
    return;
  }

  creating.value = true;
  error.value = "";
  try {
    const project = await createProject(requestText.value);
    requestText.value = "";
    openProject(project);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "项目创建失败";
  } finally {
    creating.value = false;
  }
}

function openProject(project: ProjectSummary) {
  if (project.currentStage === "DISCOVERY") {
    router.push(`/projects/${project.projectId}/start`);
    return;
  }
  router.push(`/projects/${project.projectId}/editor`);
}

function handleComposerKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    event.preventDefault();
    void handleCreateProject();
  }
}

function previewLabel(project: ProjectSummary) {
  if (project.previewSurface === "design" || project.currentStage === "DESIGN") return "首页设计稿";
  if (project.previewSurface === "planning" || project.currentStage === "PLANNING") return "首页策划稿";
  return "研究工作台";
}

function previewTone(project: ProjectSummary) {
  if (project.previewSurface === "design" || project.currentStage === "DESIGN") return "bg-emerald-500/90";
  if (project.previewSurface === "planning" || project.currentStage === "PLANNING") return "bg-blue-500/90";
  return "bg-slate-800/60";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}
</script>
