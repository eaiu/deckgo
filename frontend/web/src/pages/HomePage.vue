<template>
  <main class="home-screen">
    <div class="home-shell">
      <header class="home-header">
        <div class="home-brand">
          <span class="home-brand-mark" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M24 8C16.268 8 10 14.268 10 22C10 29.732 16.268 36 24 36C28.692 36 32.845 33.693 35.385 30.144" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
              <path d="M24 16C20.686 16 18 18.686 18 22C18 25.314 20.686 28 24 28C26.011 28 27.79 27.012 28.875 25.494" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
              <path d="M32.5 12.5C37.747 15.575 41 21.26 41 27.5" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
              <path d="M36 6C42.255 9.867 46 16.809 46 24.5" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
            </svg>
          </span>
          <div class="home-brand-copy">
            <strong>DeckGo</strong>
            <span>AI 演示文稿工作台</span>
          </div>
        </div>

        <button class="home-header-action" type="button" @click="scrollToProjects">
          我的历史项目
        </button>
      </header>

      <section class="home-hero">
        <div class="home-hero-copy">
          <p class="home-kicker">从一个主题开始，推进成一份可以展示的内容</p>
          <h1 class="home-tagline">让想法，更好地被表达</h1>
          <p class="home-subtitle">
            输入你的主题、目标或核心观点，DeckGo 会把零散思路推进成一份清晰的演示文稿。
          </p>
        </div>

        <form class="home-composer" @submit.prevent="handleSubmit">
          <div class="home-mode-switch">
            <button
              v-for="mode in contentModes"
              :key="mode.label"
              :class="['home-mode-button', { 'is-active': mode.active, 'is-static': !mode.active }]"
              type="button"
              :aria-disabled="!mode.active"
            >
              <span>{{ mode.label }}</span>
              <small v-if="mode.note">{{ mode.note }}</small>
            </button>
          </div>

          <div class="home-composer-body">
            <label class="home-input-wrap">
              <span class="sr-only">描述你的主题或想法</span>
              <textarea
                ref="promptInputRef"
                v-model="prompt"
                class="home-input"
                rows="6"
                placeholder="描述你的主题或想法，例如：为一个 AI 产品写一份融资路演提纲"
              />
            </label>
          </div>

          <div class="home-composer-footer">
            <div class="home-composer-tools">
              <button class="home-utility-button" type="button" aria-disabled="true">
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M9.5 13.5L15 8C16.657 6.343 19.343 6.343 21 8C22.657 9.657 22.657 12.343 21 14L12 23C9.239 25.761 4.761 25.761 2 23C-0.761 20.239 -0.761 15.761 2 13L11.5 3.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span>上传文件</span>
                <small>即将支持</small>
              </button>

              <button class="home-utility-button home-utility-button-muted" type="button" aria-disabled="true">
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 3L20 7.5V16.5L12 21L4 16.5V7.5L12 3Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
                  <path d="M12 8.5L15.5 10.5V14.5L12 16.5L8.5 14.5V10.5L12 8.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
                </svg>
                <span>主题</span>
              </button>

              <p v-if="error" class="form-error home-form-error">{{ error }}</p>
            </div>

            <button class="home-create-button" type="submit" :disabled="loading || !prompt.trim()">
              <span>{{ loading ? "正在创建" : "开始创建" }}</span>
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M5 12H19" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                <path d="M13 6L19 12L13 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        </form>

        <section class="home-quickstart" aria-label="快速开始">
          <div class="home-quickstart-heading">
            <p>快速开始</p>
            <span>从这里挑一个方向，快速生成你的首个主题</span>
          </div>

          <div class="home-shortcuts">
            <button
              v-for="item in quickStartPrompts"
              :key="item.title"
              type="button"
              class="home-shortcut"
              @click="applyQuickStart(item.prompt)"
            >
              <span class="home-shortcut-tag">{{ item.tag }}</span>
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
            </button>
          </div>
        </section>
      </section>

      <section ref="projectsSectionRef" class="home-projects" id="history-projects">
        <div class="home-section-heading">
          <div>
            <p class="home-section-kicker">我的历史项目</p>
            <h2>继续查看最近创建过的内容</h2>
            <p class="home-section-copy">这里只展示你已经创建过的项目，当前版本先不支持从这里直接继续编辑。</p>
          </div>
          <span class="home-section-count">{{ sortedProjects.length }}</span>
        </div>

        <div v-if="projectsLoading" class="home-project-state">
          <p>正在加载历史项目…</p>
        </div>

        <div v-else-if="projectsError" class="home-project-state home-project-state-error">
          <p>{{ projectsError }}</p>
        </div>

        <div v-else-if="sortedProjects.length === 0" class="home-project-state">
          <p>暂无历史项目。先从上面的输入框开始创建你的第一份演示文稿。</p>
        </div>

        <div v-else class="home-project-grid">
          <article v-for="project in sortedProjects" :key="project.id" class="home-project-card">
            <div class="home-project-card-top">
              <span class="home-project-template">模板 {{ project.templateId }}</span>
              <time class="home-project-time" :datetime="project.updatedAt">
                {{ formatUpdatedAt(project.updatedAt) }}
              </time>
            </div>

            <div class="home-project-card-body">
              <h3>{{ project.title || "未命名项目" }}</h3>
              <p>{{ project.topic || "这个项目还没有可展示的主题摘要。" }}</p>
            </div>

            <div class="home-project-meta">
              <span>演示文稿</span>
              <span>仅展示</span>
            </div>
          </article>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { createWorkflowSession, fetchProjects, type ProjectDto } from "../api";

const router = useRouter();

const contentModes = [
  { label: "演示文稿", active: true, note: "" },
  { label: "社媒图文", active: false, note: "即将支持" },
  { label: "长图", active: false, note: "即将支持" }
] as const;

const quickStartPrompts = [
  {
    tag: "融资",
    title: "融资路演提纲",
    description: "面向投资人梳理问题、方案、市场与增长逻辑。",
    prompt: "为一款 AI 产品写一份面向投资人的融资路演提纲"
  },
  {
    tag: "方案",
    title: "产品方案汇报",
    description: "把需求背景、产品方案和落地节奏整理清楚。",
    prompt: "为一个企业协作产品写一份面向管理层的产品方案汇报"
  },
  {
    tag: "复盘",
    title: "季度业务复盘",
    description: "围绕目标、结果、问题和下一步动作组织内容。",
    prompt: "生成一份季度业务复盘演示文稿，包含目标达成、问题总结和下季度计划"
  },
  {
    tag: "培训",
    title: "培训课件结构",
    description: "用清晰章节把知识点组织成可讲授的内容。",
    prompt: "为新员工培训制作一份课件结构，主题是 AI 产品工作流入门"
  }
] as const;

const prompt = ref("");
const loading = ref(false);
const error = ref("");
const projects = ref<ProjectDto[]>([]);
const projectsLoading = ref(true);
const projectsError = ref("");
const promptInputRef = ref<HTMLTextAreaElement | null>(null);
const projectsSectionRef = ref<HTMLElement | null>(null);

const sortedProjects = computed(() =>
  [...projects.value].sort((left, right) => {
    const leftTime = new Date(left.updatedAt).getTime();
    const rightTime = new Date(right.updatedAt).getTime();
    return rightTime - leftTime;
  })
);

onMounted(loadProjects);

async function loadProjects() {
  projectsLoading.value = true;
  projectsError.value = "";

  try {
    projects.value = await fetchProjects();
  } catch {
    projects.value = [];
    projectsError.value = "暂时无法获取历史项目，你仍然可以直接开始创建新的演示文稿。";
  } finally {
    projectsLoading.value = false;
  }
}

function applyQuickStart(value: string) {
  prompt.value = value;
  error.value = "";
  nextTick(() => promptInputRef.value?.focus());
}

function scrollToProjects() {
  projectsSectionRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function formatUpdatedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "最近更新未知";
  }

  return `更新于 ${new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date)}`;
}

async function handleSubmit() {
  const trimmedPrompt = prompt.value.trim();
  if (!trimmedPrompt) {
    return;
  }

  loading.value = true;
  error.value = "";

  try {
    const session = await createWorkflowSession(trimmedPrompt);
    await router.push(`/studio/${session.sessionId}`);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "创建会话失败";
  } finally {
    loading.value = false;
  }
}
</script>
