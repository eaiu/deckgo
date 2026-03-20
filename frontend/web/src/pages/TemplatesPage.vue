<template>
  <section class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">Templates</p>
        <h2>模板浏览</h2>
      </div>
      <button class="ghost-button" @click="loadTemplates">刷新</button>
    </div>

    <div class="card-grid">
      <article v-for="template in templates" :key="template.id" class="panel template-card">
        <div class="swatches">
          <span :style="{ background: template.defaultTheme.primary }"></span>
          <span :style="{ background: template.defaultTheme.secondary }"></span>
          <span :style="{ background: template.defaultTheme.accent }"></span>
        </div>
        <p class="eyebrow">{{ template.id }}</p>
        <h3>{{ template.name }}</h3>
        <p>{{ template.description }}</p>
        <ul class="kind-list">
          <li v-for="kind in template.slideKinds" :key="kind">{{ kind }}</li>
        </ul>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { TemplateSummary } from "@deckgo/deck-core";
import { onMounted, ref } from "vue";
import { fetchTemplates } from "../api";
import { TEMPLATE_CATALOG } from "@deckgo/template-kit";

const templates = ref<TemplateSummary[]>(TEMPLATE_CATALOG);

async function loadTemplates() {
  try {
    templates.value = (await fetchTemplates()) as TemplateSummary[];
  } catch {
    templates.value = TEMPLATE_CATALOG;
  }
}

onMounted(loadTemplates);
</script>
