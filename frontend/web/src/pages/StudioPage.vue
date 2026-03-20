<template>
  <main class="studio-shell" v-if="session">
    <aside class="panel studio-sidebar">
      <div class="sidebar-top">
        <div class="sidebar-header">
          <p class="eyebrow">Studio</p>
          <h2>{{ session.project.title }}</h2>
          <p class="sidebar-copy">{{ session.project.topic }}</p>
        </div>

        <div class="stage-pill">
          <span class="stage-dot"></span>
          <strong>{{ workflowStageLabel(session.currentStage) }}</strong>
        </div>
      </div>

      <section class="panel-section">
        <div class="section-title">
          <h3>对话记录</h3>
          <p>{{ session.messages.length }} 条消息</p>
        </div>

        <div class="message-list">
          <article
            v-for="message in session.messages"
            :key="message.id"
            class="message-card"
            :class="`role-${message.role.toLowerCase()}`"
          >
            <p class="message-meta">
              <span>{{ message.role }}</span>
              <span>{{ workflowStageLabel(message.stage) }}</span>
            </p>
            <p class="message-text">{{ messageText(message) }}</p>
          </article>
        </div>
      </section>

      <section class="panel-section" v-if="session.currentStage === 'DISCOVERY' && session.discoveryCard">
        <div class="section-title">
          <h3>{{ session.discoveryCard.title }}</h3>
          <p>{{ session.discoveryCard.description }}</p>
        </div>

        <div class="question-list">
          <article v-for="question in session.discoveryCard.questions" :key="question.id" class="question-card">
            <h4>{{ question.prompt }}</h4>
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

        <label class="field-label">
          <span>{{ session.discoveryCard.freeformHint }}</span>
          <textarea v-model="freeformAnswer" class="field-textarea" rows="4" />
        </label>

        <button class="primary-button" :disabled="loading" @click="submitDiscovery">
          {{ loading ? "提交中..." : "提交调研回答" }}
        </button>
      </section>

      <section class="panel-section" v-else-if="session.currentStage === 'RESEARCH' && session.researchSummary">
        <div class="section-title">
          <h3>资料整理</h3>
          <p>{{ session.researchSummary.summary }}</p>
        </div>

        <div class="bullet-card">
          <strong>关键假设</strong>
          <ul>
            <li v-for="item in session.researchSummary.assumptions" :key="item">{{ item }}</li>
          </ul>
        </div>

        <div class="bullet-card">
          <strong>对比点</strong>
          <ul>
            <li v-for="item in session.researchSummary.comparisonPoints" :key="item">{{ item }}</li>
          </ul>
        </div>

        <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_OUTLINE')">
          {{ loading ? "生成中..." : "继续生成大纲" }}
        </button>
      </section>

      <section class="panel-section" v-else-if="session.currentStage === 'OUTLINE' && session.outline">
        <div class="section-title">
          <h3>大纲策划</h3>
          <p>{{ session.outline.narrative }}</p>
        </div>

        <div class="outline-list">
          <article v-for="section in session.outline.sections" :key="section.id" class="outline-card">
            <h4>{{ section.title }}</h4>
            <ul>
              <li v-for="page in section.pages" :key="page.id">
                <strong>{{ page.title }}</strong>
                <span>{{ page.intent }}</span>
              </li>
            </ul>
            <p v-if="section.revisionNote" class="outline-note">修订备注：{{ section.revisionNote }}</p>
          </article>
        </div>

        <label class="field-label">
          <span>如果你想修改大纲，可以直接输入要求</span>
          <textarea v-model="outlineFeedback" class="field-textarea" rows="4" placeholder="例如：把第二部分改成更偏方案说明" />
        </label>

        <div class="inline-actions">
          <button class="ghost-button" :disabled="loading || !outlineFeedback.trim()" @click="applyOutlineFeedback">
            应用修改
          </button>
          <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_PAGE_PLAN')">
            {{ loading ? "生成中..." : "继续生成策划稿" }}
          </button>
        </div>
      </section>

      <section class="panel-section" v-else-if="session.currentStage === 'DRAFT'">
        <div class="section-title">
          <h3>策划稿</h3>
          <p>当前显示的是低保真草稿，用来确认结构、卡片分布和页面节奏。</p>
        </div>
        <button class="primary-button" :disabled="loading" @click="runCommand('CONTINUE_TO_FINAL_DESIGN')">
          {{ loading ? "生成中..." : "继续生成最终设计稿" }}
        </button>
      </section>

      <section class="panel-section" v-else>
        <div class="section-title">
          <h3>最终设计稿</h3>
          <p>当前会话已经完成，你可以在右侧切换页面缩略图，查看整套 SVG 演示稿。</p>
        </div>
      </section>

      <p v-if="error" class="form-error">{{ error }}</p>
    </aside>

    <section class="panel studio-canvas-pane">
      <div class="canvas-header">
        <div>
          <p class="eyebrow">Preview</p>
          <h3>{{ currentPage?.title ?? "等待生成页面" }}</h3>
        </div>
        <p class="canvas-caption">
          {{ currentPage ? `第 ${currentPage.orderIndex} 页` : "当前阶段还没有页面内容" }}
        </p>
      </div>

      <div class="canvas-stage">
        <div v-if="currentPage" class="slide-frame" v-html="currentSvg"></div>
        <div v-else class="empty-state">
          <p>当前阶段还没有可展示的页面。</p>
        </div>
      </div>
    </section>

    <aside class="panel studio-pages">
      <div class="section-title">
        <h3>页面列表</h3>
        <p>{{ session.pages.length }} 页</p>
      </div>

      <div class="thumbnail-list">
        <button
          v-for="page in session.pages"
          :key="page.id"
          type="button"
          class="thumbnail-card"
          :class="{ active: page.id === selectedPageId }"
          @click="selectedPageId = page.id"
        >
          <div class="thumbnail-preview" v-html="page.finalSvg ?? page.draftSvg ?? ''"></div>
          <div class="thumbnail-meta">
            <strong>{{ page.orderIndex }}. {{ page.title }}</strong>
            <span>{{ page.pagePlan.layout }}</span>
          </div>
        </button>
      </div>
    </aside>
  </main>

  <main v-else class="studio-loading">
    <p>{{ loading ? "正在加载会话..." : error || "会话不存在" }}</p>
  </main>
</template>

<script setup lang="ts">
import type { WorkflowCommandType, WorkflowMessage, WorkflowSessionSnapshot, SvgPage } from "@deckgo/deck-core";
import { workflowStageLabel } from "@deckgo/deck-core";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { fetchWorkflowSession, sendWorkflowCommand } from "../api";

const route = useRoute();
const session = ref<WorkflowSessionSnapshot | null>(null);
const loading = ref(false);
const error = ref("");
const selectedPageId = ref("");
const freeformAnswer = ref("");
const outlineFeedback = ref("");
const discoverySelections = reactive<Record<string, string>>({});

const currentPage = computed<SvgPage | null>(() => {
  if (!session.value?.pages.length) return null;
  return session.value.pages.find((page) => page.id === selectedPageId.value) ?? session.value.pages[0];
});

const currentSvg = computed(() => currentPage.value?.finalSvg ?? currentPage.value?.draftSvg ?? "");

watch(
  () => session.value?.pages,
  (pages) => {
    if (!pages?.length) {
      selectedPageId.value = "";
      return;
    }
    if (!selectedPageId.value || !pages.some((page) => page.id === selectedPageId.value)) {
      selectedPageId.value = pages[0].id;
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

async function runCommand(command: WorkflowCommandType, extra: Partial<{ selectedOptionIds: string[]; freeformAnswer: string; feedback: string }> = {}) {
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

function messageText(message: WorkflowMessage) {
  if (typeof message.content.text === "string" && message.content.text.trim()) {
    return message.content.text;
  }

  if (Array.isArray(message.content.selectedOptionIds) && message.content.selectedOptionIds.length > 0) {
    return `已选择：${message.content.selectedOptionIds.join(" / ")}`;
  }

  if (typeof message.content.freeformAnswer === "string" && message.content.freeformAnswer.trim()) {
    return message.content.freeformAnswer;
  }

  return "已提交一条结构化消息";
}
</script>
