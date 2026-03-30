<template>
  <div class="fixed inset-0 z-40 flex flex-col overflow-hidden bg-[#f4f5f7]">
    <div
      class="flex-1 overflow-auto p-12"
      style="background-image: radial-gradient(#cbd5e1 1.5px, transparent 1.5px); background-size: 24px 24px;"
    >
      <div class="mx-auto max-w-[2200px]">
        <div class="mb-6 flex items-center justify-between">
          <div class="text-sm font-semibold text-slate-700">便利贴</div>
          <button class="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50" @click="$emit('close')">
            关闭
          </button>
        </div>

        <div class="w-full overflow-x-auto pb-6">
          <div class="flex min-h-full w-max items-start gap-16">
            <aside class="sticky left-0 z-20 w-[300px] shrink-0 self-center">
              <div class="relative rounded-[2rem] border border-blue-400/30 bg-[#5ab0ff] p-6 text-white shadow-xl shadow-blue-200/50">
                <div class="mb-6 text-right text-[10px] font-bold uppercase tracking-widest opacity-80">Contents</div>
                <div class="space-y-4 pr-2">
                  <div
                    v-for="entry in rootEntries"
                    :key="entry.key"
                    class="flex items-start gap-3 text-sm font-bold leading-snug opacity-95"
                  >
                    <span
                      class="mt-0.5 shrink-0 rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide"
                      :class="entry.tone === 'section' ? 'bg-white/20 text-white' : 'bg-slate-900/15 text-white/80'"
                    >
                      {{ entry.tone === "section" ? "章节" : "固定" }}
                    </span>
                    <span>{{ entry.label }}</span>
                  </div>
                </div>
                <div class="mt-8 flex items-center justify-between border-t border-white/20 pt-5">
                  <span class="text-xs font-bold tracking-wide">PPT Structure</span>
                  <span class="text-xs opacity-80">{{ sections.length }} 个章节</span>
                </div>
                <div class="absolute -right-8 top-1/2 h-px w-8 border-t-2 border-dashed border-slate-300" />
              </div>
            </aside>

            <main class="relative flex flex-col gap-12">
              <div class="absolute bottom-[120px] left-[-2rem] top-[120px] w-px border-l-2 border-dashed border-slate-300" />

              <div v-for="page in prefixPages" :key="page.id" class="relative flex items-center gap-8">
                <div class="absolute -left-8 top-1/2 h-px w-8 border-t-2 border-dashed border-slate-300" />
                <PageCard
                  :page="page"
                  :display-order="displayOrderMap.get(page.id) || page.sortOrder"
                  :surface="surface"
                  :active="page.id === activePageId"
                  :role-label="fixedPageLabel(page.pageRole)"
                  @jump="emitJump"
                />
              </div>

              <div v-for="(section, sectionIndex) in sections" :key="section.key" class="relative flex items-center gap-12">
                <div class="absolute -left-8 top-1/2 h-px w-8 border-t-2 border-dashed border-slate-300" />

                <section class="flex min-h-[248px] w-[320px] shrink-0 flex-col rounded-[2rem] border-2 border-slate-100 bg-white p-7 shadow-sm">
                  <div class="flex items-start justify-between gap-4">
                    <span class="rounded-md border border-slate-200 bg-white px-3 py-1 text-xs font-bold text-slate-500 shadow-sm">章节</span>
                    <span class="text-5xl font-bold tracking-tighter text-slate-200">{{ String(sectionIndex + 1).padStart(2, "0") }}</span>
                  </div>
                  <div class="mt-8 text-2xl font-bold leading-snug text-slate-800">{{ section.title }}</div>
                  <div class="mt-auto flex items-center justify-between border-t border-slate-50 pt-5 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                    <span>Section</span>
                    <span>{{ section.pages.length }} Pages</span>
                  </div>
                </section>

                <div class="relative flex items-center gap-8">
                  <div class="absolute -left-12 top-1/2 h-px w-12 border-t-2 border-dashed border-slate-300" />

                  <div v-for="(page, pageIndex) in section.pages" :key="page.id" class="relative flex items-center gap-8">
                    <div v-if="pageIndex > 0" class="absolute -left-8 top-1/2 h-px w-8 border-t-2 border-dashed border-slate-300" />
                    <PageCard
                      :page="page"
                      :display-order="displayOrderMap.get(page.id) || page.sortOrder"
                      :surface="surface"
                      :active="page.id === activePageId"
                      role-label="内容页"
                      @jump="emitJump"
                    />
                  </div>
                </div>
              </div>

              <div v-for="page in suffixPages" :key="page.id" class="relative flex items-center gap-8">
                <div class="absolute -left-8 top-1/2 h-px w-8 border-t-2 border-dashed border-slate-300" />
                <PageCard
                  :page="page"
                  :display-order="displayOrderMap.get(page.id) || page.sortOrder"
                  :surface="surface"
                  :active="page.id === activePageId"
                  :role-label="fixedPageLabel(page.pageRole)"
                  @jump="emitJump"
                />
              </div>
            </main>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from "vue";
import type { ProjectPage } from "../../api";

const props = withDefaults(
  defineProps<{
    pages: ProjectPage[];
    activePageId?: string | null;
    surface?: "research" | "planning" | "design";
  }>(),
  {
    activePageId: null,
    surface: "research"
  }
);

const emit = defineEmits<{
  close: [];
  jump: [payload: { pageId: string; surface: "research" | "planning" | "design" }];
}>();

type Section = {
  key: string;
  title: string;
  pages: ProjectPage[];
};

const orderedPages = computed(() => [...props.pages].sort((left, right) => left.sortOrder - right.sortOrder));

const splitPages = computed(() => {
  const prefix: ProjectPage[] = [];
  const content: ProjectPage[] = [];
  const suffix: ProjectPage[] = [];
  let seenContent = false;

  orderedPages.value.forEach((page) => {
    if ((page.pageRole || "").toLowerCase() === "content") {
      seenContent = true;
      content.push(page);
      return;
    }
    if (!seenContent) {
      prefix.push(page);
      return;
    }
    suffix.push(page);
  });

  return { prefix, content, suffix };
});

const prefixPages = computed(() => splitPages.value.prefix);
const suffixPages = computed(() => splitPages.value.suffix);

const sections = computed<Section[]>(() => {
  const grouped = new Map<string, ProjectPage[]>();
  splitPages.value.content.forEach((page) => {
    const title = (page.partTitle || "").trim() || "未命名章节";
    const items = grouped.get(title) || [];
    items.push(page);
    grouped.set(title, items);
  });
  return [...grouped.entries()].map(([title, pages], index) => ({
    key: `${index}-${title}`,
    title,
    pages
  }));
});

const displayOrderMap = computed(() => {
  const map = new Map<string, number>();
  let order = 1;
  prefixPages.value.forEach((page) => {
    map.set(page.id, order);
    order += 1;
  });
  sections.value.forEach((section) => {
    section.pages.forEach((page) => {
      map.set(page.id, order);
      order += 1;
    });
  });
  suffixPages.value.forEach((page) => {
    map.set(page.id, order);
    order += 1;
  });
  return map;
});

const rootEntries = computed(() => [
  ...prefixPages.value.map((page) => ({
    key: page.id,
    tone: "fixed" as const,
    label: fixedPageLabel(page.pageRole)
  })),
  ...sections.value.map((section, index) => ({
    key: section.key,
    tone: "section" as const,
    label: `${String(index + 1).padStart(2, "0")} ${section.title}`
  })),
  ...suffixPages.value.map((page) => ({
    key: page.id,
    tone: "fixed" as const,
    label: fixedPageLabel(page.pageRole)
  }))
]);

function fixedPageLabel(pageRole?: string | null) {
  const role = String(pageRole || "").toLowerCase();
  if (role === "cover") return "首页";
  if (role === "toc") return "目录";
  if (role === "end") return "结束页";
  return "固定页";
}

function emitJump(payload: { pageId: string; surface: "research" | "planning" | "design" }) {
  emit("jump", payload);
}

const PageCard = defineComponent({
  name: "StoryboardPageCard",
  props: {
    page: { type: Object as () => ProjectPage, required: true },
    roleLabel: { type: String, required: true },
    displayOrder: { type: Number, required: true },
    surface: { type: String as () => "research" | "planning" | "design", required: true },
    active: { type: Boolean, default: false }
  },
  emits: ["jump"],
  setup(cardProps, { emit: cardEmit }) {
    function jump(nextSurface: "research" | "planning" | "design") {
      cardEmit("jump", { pageId: cardProps.page.id, surface: nextSurface });
    }

    function jumpButton(label: string, value: "research" | "planning" | "design") {
      const isActive = cardProps.active && cardProps.surface === value;
      return h(
        "button",
        {
          class: `flex-1 rounded-2xl py-3 text-[10px] font-medium transition-colors ${
            isActive ? "bg-blue-50 text-blue-700" : "bg-slate-50 text-slate-400 hover:bg-blue-50 hover:text-blue-600"
          }`,
          onClick: () => jump(value)
        },
        label
      );
    }

    return () =>
      h(
        "section",
        {
          class: `flex min-h-[248px] w-[320px] shrink-0 flex-col rounded-[2rem] border-2 bg-white p-7 shadow-sm transition-all ${
            cardProps.active ? "border-blue-400 shadow-md shadow-blue-100" : "border-slate-100 hover:shadow-md"
          }`
        },
        [
          h("div", { class: "mb-5 flex items-start justify-between gap-4" }, [
            h("div", { class: "space-y-2" }, [
              h("div", { class: "text-sm font-bold text-slate-400" }, `#${cardProps.displayOrder}`),
              h("span", { class: "rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold text-slate-500" }, cardProps.roleLabel)
            ])
          ]),
          h("div", { class: "text-lg font-bold leading-snug text-slate-800" }, cardProps.page.pageCode || "未命名页面"),
          h(
            "div",
            { class: "mt-5 space-y-2.5" },
            (cardProps.page.partTitle ? [cardProps.page.partTitle] : ["补充当前页要点"]).map((item, index) =>
              h("div", { class: "text-xs font-medium leading-relaxed text-slate-500" }, `${index + 1}. ${item}`)
            )
          ),
          h("div", { class: "mt-auto flex justify-between gap-3 border-t border-slate-50 pt-4" }, [
            jumpButton("研究", "research"),
            jumpButton("策划", "planning"),
            jumpButton("设计稿", "design")
          ])
        ]
      );
  }
});
</script>
