<template>
  <section class="page workspace-page">
    <div class="page-header">
      <div>
        <p class="eyebrow">Workspace</p>
        <h2>项目工作台</h2>
      </div>
      <div class="action-row">
        <button class="ghost-button" @click="requestDraft">AI 草案</button>
        <button class="ghost-button" @click="requestRevision">AI 修订</button>
        <button class="primary-button" @click="saveVersion">保存版本</button>
        <button class="primary-button" @click="exportDeck">导出 PPTX</button>
      </div>
    </div>

    <div class="workspace-grid">
      <aside class="panel timeline">
        <h3>版本时间线</h3>
        <button class="ghost-button small" @click="loadVersions">刷新版本</button>
        <ol>
          <li v-for="version in versions" :key="version.id" @click="selectVersion(version)">
            <strong>v{{ version.versionNumber }}</strong>
            <span>{{ version.specTitle }}</span>
            <small>{{ version.source }}</small>
          </li>
        </ol>
      </aside>

      <section class="panel editor-panel">
        <div class="panel-title">
          <h3>DeckSpec JSON</h3>
          <p>{{ summary }}</p>
        </div>
        <textarea v-model="editorText"></textarea>
      </section>

      <section class="panel preview-panel">
        <div class="panel-title">
          <h3>预览</h3>
          <p>预览层只服务于理解和确认，不是系统真相。</p>
        </div>
        <div class="preview-stack">
          <article v-for="slide in currentDeck.slides" :key="slide.id" class="preview-slide">
            <header>
              <p class="eyebrow">{{ slide.kind }}</p>
              <h4>{{ slide.title }}</h4>
            </header>
            <div v-for="block in slide.blocks" :key="block.id" class="preview-block">
              <template v-if="block.kind === 'text'">
                <p>{{ block.content.text }}</p>
              </template>
              <template v-else-if="block.kind === 'shape'">
                <p>{{ block.content.label }}</p>
              </template>
              <template v-else>
                <p>{{ block.kind }}</p>
              </template>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { SAMPLE_DECK, parseDeckSpec, prettyDeckSpec, summarizeDeck, type DeckSpec } from "@deckgo/deck-core";
import { onMounted, ref, computed } from "vue";
import { createDraft, createRenderJob, createRevision, createVersion, fetchProjects, fetchVersions, type DeckVersionDto } from "../api";

const projects = ref<{ id: string; title: string; templateId: string; audience: string; topic: string }[]>([]);
const activeProjectId = ref<string>("");
const versions = ref<DeckVersionDto[]>([]);
const currentDeck = ref<DeckSpec>(SAMPLE_DECK);
const editorText = ref(prettyDeckSpec(SAMPLE_DECK));

const summary = computed(() => summarizeDeck(currentDeck.value));

async function loadProjects() {
  try {
    projects.value = await fetchProjects();
    if (!activeProjectId.value && projects.value.length > 0) {
      activeProjectId.value = projects.value[0].id;
    }
  } catch {
    projects.value = [];
  }
}

async function loadVersions() {
  if (!activeProjectId.value) return;
  try {
    versions.value = await fetchVersions(activeProjectId.value);
    if (versions.value.length > 0) {
      selectVersion(versions.value[0]);
    }
  } catch {
    versions.value = [];
  }
}

function selectVersion(version: DeckVersionDto) {
  currentDeck.value = version.deckSpec as DeckSpec;
  editorText.value = prettyDeckSpec(currentDeck.value);
}

async function requestDraft() {
  const project = projects.value[0];
  if (!project) return;
  const response = await createDraft({
    topic: project.topic,
    audience: project.audience,
    goal: project.title,
    templateId: project.templateId,
    slideCountHint: 5
  }) as { deckSpec: DeckSpec };
  currentDeck.value = response.deckSpec;
  editorText.value = prettyDeckSpec(currentDeck.value);
}

async function requestRevision() {
  const baseVersion = versions.value[0];
  const project = projects.value[0];
  if (!baseVersion || !project) return;
  const response = await createRevision({
    projectId: project.id,
    baseVersionId: baseVersion.id,
    instruction: "请把收尾页改得更像下一步行动清单。"
  }) as { deckSpec: DeckSpec };
  currentDeck.value = response.deckSpec;
  editorText.value = prettyDeckSpec(currentDeck.value);
}

async function saveVersion() {
  const project = projects.value[0];
  if (!project) return;
  currentDeck.value = parseDeckSpec(editorText.value);
  await createVersion(project.id, {
    deckSpec: currentDeck.value,
    note: "从工作台保存",
    source: "MANUAL"
  });
  await loadVersions();
}

async function exportDeck() {
  const project = projects.value[0];
  const baseVersion = versions.value[0];
  if (!project || !baseVersion) return;
  await createRenderJob(project.id, {
    deckVersionId: baseVersion.id,
    format: "pptx"
  });
}

onMounted(async () => {
  await loadProjects();
  await loadVersions();
});
</script>
