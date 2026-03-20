<template>
  <main class="home-screen">
    <section class="home-card">
      <p class="eyebrow">DeckGo</p>
      <h1>把想法变成一套可浏览的 SVG 演示稿</h1>
      <p class="home-copy">
        输入一个主题，我们会先做背景调研，再推进资料整理、大纲策划、页面策划稿和最终设计稿。
      </p>

      <form class="home-form" @submit.prevent="handleSubmit">
        <textarea
          v-model="prompt"
          class="home-input"
          placeholder="例如：为一个 AI SaaS 产品写一份面向管理层的产品介绍 PPT"
        />
        <div class="home-actions">
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="primary-button" type="submit" :disabled="loading || !prompt.trim()">
            {{ loading ? "创建中..." : "开始创建" }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { createWorkflowSession } from "../api";

const router = useRouter();
const prompt = ref("为一个 AI 项目写一份产品介绍 PPT");
const loading = ref(false);
const error = ref("");

async function handleSubmit() {
  loading.value = true;
  error.value = "";

  try {
    const session = await createWorkflowSession(prompt.value);
    await router.push(`/studio/${session.sessionId}`);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "创建会话失败";
  } finally {
    loading.value = false;
  }
}
</script>
