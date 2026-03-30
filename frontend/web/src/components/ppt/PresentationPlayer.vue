<template>
  <div class="fixed inset-0 z-50 overflow-hidden bg-slate-950 text-white">
    <div class="absolute inset-0 bg-[radial-gradient(circle_at_top,_rgba(59,130,246,0.22),_transparent_38%),radial-gradient(circle_at_bottom,_rgba(14,165,233,0.12),_transparent_32%)]" />
    <div class="relative flex h-full flex-col">
      <div class="flex items-center justify-between px-6 py-5">
        <div>
          <p class="text-xs uppercase tracking-[0.24em] text-white/50">Presentation</p>
          <h2 class="mt-2 text-lg font-semibold">{{ currentPage?.pageCode || "未命名页面" }}</h2>
        </div>
        <button class="rounded-full border border-white/10 bg-white/5 p-3 transition hover:bg-white/10" @click="$emit('close')">
          关闭
        </button>
      </div>

      <div class="flex flex-1 items-center justify-between gap-6 px-6 pb-6">
        <button
          class="rounded-full border border-white/10 bg-white/5 p-4 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
          :disabled="index <= 0"
          @click="$emit('update:index', index - 1)"
        >
          上一页
        </button>

        <div class="flex min-w-0 flex-1 flex-col items-center gap-6">
          <div class="flex flex-wrap justify-center gap-3 text-sm text-white/60">
            <span class="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 font-medium">#{{ currentPage?.sortOrder }}</span>
            <span class="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 font-medium">{{ currentPage?.partTitle || currentPage?.pageRole || "页面" }}</span>
          </div>
          <div class="aspect-video w-[min(92vw,calc((100vh-14rem)*16/9))] overflow-hidden rounded-[2rem] border border-white/10 bg-white shadow-[0_40px_120px_rgba(15,23,42,0.55)]">
            <div v-if="markup" class="h-full w-full [&_svg]:h-full [&_svg]:w-full" v-html="markup" />
            <div v-else class="flex h-full items-center justify-center text-slate-400">当前页面暂无可放映内容</div>
          </div>
        </div>

        <button
          class="rounded-full border border-white/10 bg-white/5 p-4 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
          :disabled="index >= pages.length - 1"
          @click="$emit('update:index', index + 1)"
        >
          下一页
        </button>
      </div>

      <div class="px-6 pb-6">
        <div class="rounded-[1.5rem] border border-white/10 bg-white/5 px-4 py-3 backdrop-blur">
          <div class="flex items-center justify-between gap-4 text-sm text-white/70">
            <div>{{ index + 1 }} / {{ pages.length }}</div>
            <div>Esc 关闭</div>
          </div>
          <div class="mt-3 flex gap-2 overflow-x-auto pb-1">
            <button
              v-for="(page, pageIndex) in pages"
              :key="page.id"
              class="shrink-0 rounded-xl border px-3 py-2 text-left text-xs transition"
              :class="pageIndex === index ? 'border-blue-400 bg-blue-500/20 text-white' : 'border-white/10 bg-white/5 text-white/60 hover:bg-white/10 hover:text-white'"
              @click="$emit('update:index', pageIndex)"
            >
              <div class="font-semibold">#{{ page.sortOrder }}</div>
              <div class="mt-1 max-w-[10rem] truncate">{{ page.pageCode }}</div>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from "vue";
import type { ProjectPage } from "../../api";

const props = defineProps<{
  pages: ProjectPage[];
  index: number;
  surface: "draft" | "design";
}>();

const emit = defineEmits<{
  close: [];
  "update:index": [value: number];
}>();

const currentPage = computed(() => props.pages[props.index] ?? null);
const markup = computed(() => {
  if (!currentPage.value) {
    return "";
  }
  return props.surface === "design"
    ? currentPage.value.currentDesignSvg || currentPage.value.currentDraftSvg || ""
    : currentPage.value.currentDraftSvg || currentPage.value.currentDesignSvg || "";
});

function handleKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    emit("close");
    return;
  }
  if (event.key === "ArrowLeft" || event.key === "PageUp") {
    event.preventDefault();
    if (props.index > 0) {
      emit("update:index", props.index - 1);
    }
    return;
  }
  if (event.key === "ArrowRight" || event.key === "PageDown" || event.key === " ") {
    event.preventDefault();
    if (props.index < props.pages.length - 1) {
      emit("update:index", props.index + 1);
    }
  }
}

onMounted(() => window.addEventListener("keydown", handleKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", handleKeydown));
</script>
