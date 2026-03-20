<template>
  <section class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">Projects</p>
        <h2>项目列表</h2>
      </div>
      <button class="ghost-button" @click="loadProjects">刷新</button>
    </div>

    <form class="panel form-grid" @submit.prevent="handleCreate">
      <label>
        <span>项目标题</span>
        <input v-model="form.title" placeholder="例如：DeckGo 首版路线图" />
      </label>
      <label>
        <span>主题</span>
        <input v-model="form.topic" placeholder="例如：DeckSpec 驱动的 AI PPT" />
      </label>
      <label>
        <span>受众</span>
        <input v-model="form.audience" placeholder="例如：初学工程师" />
      </label>
      <label>
        <span>模板</span>
        <select v-model="form.templateId">
          <option v-for="template in templates" :key="template.id" :value="template.id">
            {{ template.name }}
          </option>
        </select>
      </label>
      <button class="primary-button" type="submit">创建项目</button>
    </form>

    <div class="card-grid">
      <article v-for="project in projects" :key="project.id" class="panel project-card">
        <p class="eyebrow">{{ project.templateId }}</p>
        <h3>{{ project.title }}</h3>
        <p>{{ project.topic }}</p>
        <p class="muted">受众：{{ project.audience }}</p>
        <RouterLink class="ghost-button" to="/workspace">打开工作台</RouterLink>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { TemplateSummary } from "@deckgo/deck-core";
import { onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { createProject, fetchProjects, fetchTemplates, type ProjectDto } from "../api";
import { TEMPLATE_CATALOG } from "@deckgo/template-kit";

const projects = ref<ProjectDto[]>([]);
const templates = ref<TemplateSummary[]>(TEMPLATE_CATALOG);
const form = reactive({
  title: "DeckGo 项目框架",
  topic: "DeckSpec 驱动的 AI PPT 系统",
  audience: "初学工程师",
  templateId: "clarity-blue"
});

async function loadProjects() {
  try {
    projects.value = await fetchProjects();
  } catch {
    projects.value = [];
  }
}

async function loadTemplates() {
  try {
    templates.value = (await fetchTemplates()) as TemplateSummary[];
  } catch {
    templates.value = TEMPLATE_CATALOG;
  }
}

async function handleCreate() {
  await createProject({ ...form });
  await loadProjects();
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadTemplates()]);
});
</script>
