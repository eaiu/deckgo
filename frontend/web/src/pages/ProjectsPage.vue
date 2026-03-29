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
        <span>需求描述</span>
        <textarea v-model="form.prompt" rows="4" placeholder="例如：为一个 AI 产品写一份面向管理层的产品方案汇报"></textarea>
      </label>
      <label>
        <span>风格预设</span>
        <select v-model="form.stylePreset">
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
        <p class="muted">SVG-first 项目</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { TemplateSummary } from "@deckgo/deck-core";
import { onMounted, reactive, ref } from "vue";
import { createProject, fetchProjects, fetchTemplates, type ProjectDto } from "../api";
import { TEMPLATE_CATALOG } from "@deckgo/template-kit";

const projects = ref<ProjectDto[]>([]);
const templates = ref<TemplateSummary[]>(TEMPLATE_CATALOG);
const form = reactive({
  prompt: "为一个 AI 产品写一份面向管理层的产品方案汇报",
  stylePreset: "clarity-blue"
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
  await createProject({ prompt: form.prompt, stylePreset: form.stylePreset });
  await loadProjects();
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadTemplates()]);
});
</script>
