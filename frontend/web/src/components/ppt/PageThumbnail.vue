<template>
  <button
    class="relative cursor-pointer overflow-hidden rounded-xl border-2 text-left transition-all"
    :class="active ? 'border-blue-500 shadow-md shadow-blue-100' : 'border-slate-100 hover:border-slate-300'"
    @click="$emit('select')"
  >
    <div v-if="markup" class="absolute left-1.5 top-1.5 z-10 rounded-md bg-slate-800/60 px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-md">
      {{ page.sortOrder }}
    </div>
    <div
      v-if="markup"
      class="absolute right-1.5 top-1.5 z-10 rounded-md px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-md"
      :class="surfaceTone"
    >
      {{ surfaceLabel }}
    </div>

    <div class="relative aspect-video bg-slate-50">
      <template v-if="markup">
        <div class="absolute inset-0 bg-white [&_svg]:h-full [&_svg]:w-full" v-html="markup" />
        <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-950/80 via-slate-950/30 to-transparent px-3 pb-2 pt-8">
          <div class="text-[10px] uppercase tracking-wide text-white/70">{{ page.pageRole || "content" }}</div>
          <div class="mt-1 line-clamp-2 text-xs font-semibold leading-4 text-white">{{ page.partTitle || page.pageCode || "页面" }}</div>
        </div>
      </template>

      <template v-else-if="surface === 'research'">
        <div class="flex h-full flex-col p-3">
          <div class="flex items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2">
              <span class="rounded-md bg-slate-800/75 px-1.5 py-0.5 text-[10px] font-medium text-white">{{ page.sortOrder }}</span>
              <span class="truncate text-[10px] uppercase tracking-wide text-slate-400">{{ page.pageRole || "content" }}</span>
            </div>
            <span class="rounded-md px-1.5 py-0.5 text-[10px] font-medium text-white" :class="surfaceTone">{{ surfaceLabel }}</span>
          </div>
          <div class="mt-2 line-clamp-2 text-xs font-semibold leading-4 text-slate-700">{{ page.pageCode }}</div>
          <div class="mt-1 line-clamp-2 text-[11px] leading-4 text-slate-400">
            {{ page.partTitle || "无要点" }}
          </div>
        </div>
      </template>

      <template v-else>
        <div class="flex h-full flex-col items-center justify-center px-4 text-center">
          <div class="flex w-full items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2">
              <span class="rounded-md bg-slate-800/75 px-1.5 py-0.5 text-[10px] font-medium text-white">{{ page.sortOrder }}</span>
              <span class="truncate text-[10px] uppercase tracking-wide text-slate-400">{{ page.pageRole || "content" }}</span>
            </div>
            <span class="rounded-md px-1.5 py-0.5 text-[10px] font-medium text-white" :class="surfaceTone">{{ surfaceLabel }}</span>
          </div>
          <div class="mt-2 line-clamp-3 text-xs font-semibold leading-4 text-slate-700">{{ page.pageCode }}</div>
          <div class="mt-3 rounded-lg border border-dashed border-slate-200 bg-white/80 px-3 py-2 text-[11px] leading-4 text-slate-400">
            {{ placeholder }}
          </div>
        </div>
      </template>
    </div>

    <div class="border-t border-slate-100 px-3 py-2">
      <div class="flex flex-wrap gap-1.5">
        <span class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-medium text-slate-600">{{ page.outlineStatus }}</span>
        <span class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-medium text-slate-600">{{ page.searchStatus }}</span>
        <span class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-medium text-slate-600">{{ page.designStatus }}</span>
      </div>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { ProjectPage } from "../../api";

const props = defineProps<{
  page: ProjectPage;
  active: boolean;
  surface: "research" | "planning" | "design";
  markup?: string;
  placeholder: string;
}>();

defineEmits<{
  select: [];
}>();

const surfaceLabel = computed(() => {
  if (props.surface === "design") return "设计稿";
  if (props.surface === "planning") return "策划稿";
  return "研究";
});

const surfaceTone = computed(() => {
  if (props.surface === "design") return "bg-emerald-500/90";
  if (props.surface === "planning") return "bg-blue-500/90";
  return "bg-slate-800/60";
});
</script>
