<template>
  <div />
</template>

<script setup lang="ts">
// This file intentionally exports helper components below via named exports.
</script>

<script lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref } from "vue";
import type { ProjectPage } from "../../api";

function normalizeStatus(value: string) {
  return String(value || "").toUpperCase();
}

function fitRect(containerWidth: number, containerHeight: number, aspectRatio: number) {
  if (containerWidth <= 0 || containerHeight <= 0) {
    return null;
  }
  const containerRatio = containerWidth / containerHeight;
  if (containerRatio > aspectRatio) {
    const height = containerHeight;
    return {
      width: Math.floor(height * aspectRatio),
      height: Math.floor(height)
    };
  }
  const width = containerWidth;
  return {
    width: Math.floor(width),
    height: Math.floor(width / aspectRatio)
  };
}

export const StageBadge = defineComponent({
  name: "StageBadge",
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true }
  },
  setup(props) {
    const tone = computed(() => {
      const status = normalizeStatus(props.value);
      if (status === "READY" || status === "COMPLETED" || status === "CONFIRMED") {
        return "bg-emerald-50 text-emerald-700 border-emerald-100";
      }
      if (status === "PROCESSING" || status === "RUNNING") {
        return "bg-blue-50 text-blue-700 border-blue-100";
      }
      if (status === "STALE") {
        return "bg-amber-50 text-amber-700 border-amber-100";
      }
      if (status === "FAILED") {
        return "bg-rose-50 text-rose-700 border-rose-100";
      }
      return "bg-slate-50 text-slate-500 border-slate-200";
    });

    return () => h("span", { class: `rounded-full border px-2 py-1 text-[11px] font-medium ${tone.value}` }, `${props.label} · ${props.value}`);
  }
});

export const SvgCanvas = defineComponent({
  name: "SvgCanvas",
  props: {
    markup: { type: String, default: "" },
    placeholder: { type: String, required: true }
  },
  setup(props) {
    const frameRef = ref<HTMLElement | null>(null);
    const frameSize = ref({ width: 0, height: 0 });
    let observer: ResizeObserver | null = null;

    const fittedSize = computed(() => fitRect(frameSize.value.width, frameSize.value.height, 16 / 9));
    const canvasStyle = computed(() => {
      if (!fittedSize.value) {
        return { aspectRatio: "16 / 9", width: "100%" };
      }
      return {
        width: `${fittedSize.value.width}px`,
        height: `${fittedSize.value.height}px`
      };
    });

    onMounted(() => {
      if (!frameRef.value) {
        return;
      }
      const updateSize = () => {
        if (!frameRef.value) {
          return;
        }
        frameSize.value = {
          width: frameRef.value.clientWidth,
          height: frameRef.value.clientHeight
        };
      };
      updateSize();

      if (typeof ResizeObserver !== "undefined") {
        observer = new ResizeObserver(updateSize);
        observer.observe(frameRef.value);
      } else {
        window.addEventListener("resize", updateSize);
      }
    });

    onBeforeUnmount(() => {
      observer?.disconnect();
      observer = null;
    });

    return () =>
      h(
        "div",
        {
          ref: frameRef,
          class: "flex h-full min-h-[28rem] w-full items-center justify-center rounded-[2rem] bg-[radial-gradient(circle_at_top,_rgba(148,163,184,0.14),_transparent_55%)]"
        },
        props.markup
          ? h("div", {
              class: "overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl [&_svg]:h-full [&_svg]:w-full",
              style: canvasStyle.value,
              innerHTML: props.markup
            })
          : h(
              "div",
              {
                class: "flex items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-400 shadow-xl",
                style: canvasStyle.value
              },
              props.placeholder
            )
      );
  }
});

export const SearchResultCard = defineComponent({
  name: "SearchResultCard",
  props: {
    item: { type: Object as () => Record<string, unknown>, required: true },
    retrying: { type: Boolean, default: false },
    allowRetry: { type: Boolean, default: false }
  },
  emits: ["retry"],
  setup(props, { emit }) {
    function pill(label: string, value: string, tone: string) {
      return h("span", { class: `rounded-full border px-2.5 py-1 text-[11px] font-medium ${tone}` }, `${label} · ${value}`);
    }

    return () => {
      const readStatus = String(props.item.read_status ?? "pending").toLowerCase();
      const vectorStatus = String(props.item.vector_status ?? "pending").toLowerCase();
      const readTone =
        readStatus === "failed"
          ? "bg-rose-50 text-rose-700 border-rose-100"
          : readStatus === "ready" || readStatus === "reused"
          ? "bg-emerald-50 text-emerald-700 border-emerald-100"
          : "bg-amber-50 text-amber-700 border-amber-100";
      const vectorTone =
        vectorStatus === "ready"
          ? "bg-blue-50 text-blue-700 border-blue-100"
          : vectorStatus === "failed"
          ? "bg-rose-50 text-rose-700 border-rose-100"
          : "bg-amber-50 text-amber-700 border-amber-100";
      const showRetry = props.allowRetry && (readStatus === "failed" || vectorStatus !== "ready");
      const retryLabel = readStatus === "failed" ? "重试正文与向量化" : "重新向量化";

      return h("article", { class: "space-y-3 rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm" }, [
        h("div", { class: "flex items-start justify-between gap-3" }, [
          h("div", { class: "min-w-0 space-y-2" }, [
            h(
              "a",
              {
                href: String(props.item.url ?? "#"),
                target: "_blank",
                rel: "noreferrer",
                class: "block break-words text-base font-semibold text-blue-600 hover:underline"
              },
              String(props.item.title ?? "未命名来源")
            ),
            h("div", { class: "break-all text-xs text-emerald-600" }, String(props.item.url ?? ""))
          ]),
          props.item.query_purpose
            ? h(
                "span",
                { class: "shrink-0 rounded-full border border-slate-200 bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600" },
                String(props.item.query_purpose)
              )
            : null
        ]),
        h("div", { class: "flex flex-wrap gap-2" }, [
          pill("搜索", `R${String(props.item.search_rank ?? 0)}`, "bg-slate-50 text-slate-600 border-slate-200"),
          pill("全文", String(props.item.read_status ?? "pending"), readTone),
          pill("向量", String(props.item.vector_status ?? "pending"), vectorTone)
        ]),
        h("div", { class: "text-xs text-slate-500" }, String(props.item.query_text ?? "")),
        h(
          "p",
          { class: "whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-600 [overflow-wrap:anywhere]" },
          String(props.item.content_excerpt_md ?? props.item.snippet ?? "搜索摘要已入库，等待抓取全文。")
        ),
        showRetry
          ? h("div", { class: "flex justify-end" }, [
              h(
                "button",
                {
                  class:
                    "rounded-xl border border-slate-200 px-3 py-2 text-xs font-medium text-slate-600 transition-colors hover:border-blue-400 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-50",
                  disabled: props.retrying,
                  onClick: () => emit("retry")
                },
                props.retrying ? "处理中..." : retryLabel
              )
            ])
          : null
      ]);
    };
  }
});

export const DataModal = defineComponent({
  name: "DataModal",
  props: {
    open: { type: Boolean, required: true },
    page: { type: Object as () => ProjectPage | null, default: null },
    surface: { type: String as () => "research" | "planning" | "design", required: true },
    titleDraft: { type: String, required: true },
    summaryDraft: { type: String, required: true },
    outlineDraft: { type: String, required: true }
  },
  emits: ["close", "save-outline", "save-summary", "update:title-draft", "update:summary-draft", "update:outline-draft"],
  setup(props, { emit }) {
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        emit("close");
      }
    }

    onMounted(() => window.addEventListener("keydown", handleEscape));
    onBeforeUnmount(() => window.removeEventListener("keydown", handleEscape));

    return () => {
      if (!props.open || !props.page) {
        return null;
      }
      return h("div", { class: "fixed inset-0 z-50 flex items-center justify-center bg-slate-900/35 p-6 backdrop-blur-sm" }, [
        h("div", { class: "flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-2xl" }, [
          h("div", { class: "flex items-center justify-between border-b border-slate-100 px-6 py-5" }, [
            h("div", null, [
              h("div", { class: "text-xs uppercase tracking-wide text-slate-400" }, `${props.surface} / 原始数据`),
              h("div", { class: "text-xl font-semibold text-slate-800" }, props.page.pageCode)
            ]),
            h("button", { class: "rounded-full border border-slate-200 p-2 text-slate-500 hover:bg-slate-50 hover:text-slate-800", onClick: () => emit("close") }, "关闭")
          ]),
          h("div", { class: "min-h-0 flex-1 space-y-6 overflow-y-auto bg-slate-50/50 p-6" }, [
            h("section", { class: "space-y-4 rounded-2xl border border-slate-200 bg-white p-5" }, [
              h("div", { class: "font-semibold text-slate-800" }, "页面结构"),
              h("input", {
                value: props.titleDraft,
                class: "w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500",
                onInput: (event: Event) => emit("update:title-draft", (event.target as HTMLInputElement).value)
              }),
              h("textarea", {
                value: props.outlineDraft,
                rows: 6,
                class: "w-full resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500",
                onInput: (event: Event) => emit("update:outline-draft", (event.target as HTMLTextAreaElement).value)
              }),
              h("div", { class: "flex justify-end" }, [
                h("button", { class: "rounded-xl border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-semibold text-blue-700 hover:bg-blue-100", onClick: () => emit("save-outline") }, "保存页面结构")
              ])
            ]),
            h("section", { class: "space-y-4 rounded-2xl border border-slate-200 bg-white p-5" }, [
              h("div", { class: "font-semibold text-slate-800" }, "当前页 summary"),
              h("textarea", {
                value: props.summaryDraft,
                rows: 10,
                class: "w-full resize-none rounded-2xl border border-slate-200 px-4 py-3 text-sm text-slate-700 outline-none focus:border-blue-500",
                onInput: (event: Event) => emit("update:summary-draft", (event.target as HTMLTextAreaElement).value)
              }),
              h("div", { class: "flex justify-end" }, [
                h("button", { class: "rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50", onClick: () => emit("save-summary") }, "保存 summary")
              ])
            ])
          ])
        ])
      ]);
    };
  }
});
</script>
