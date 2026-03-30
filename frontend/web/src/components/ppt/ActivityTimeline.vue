<template>
  <div class="space-y-3">
    <TransitionGroup
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
      tag="div"
      class="space-y-3"
    >
      <template v-for="item in timelineItems" :key="item.key">
        <article
          v-if="item.type === 'event'"
          class="rounded-[1.65rem] border border-slate-200/90 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(248,250,252,0.96))] px-4 py-4 shadow-sm"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex min-w-0 gap-3">
              <span class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" :class="eventToneDot(item.event)" />
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-slate-800">{{ eventTitle(item.event) }}</p>
                <p class="mt-1 text-xs text-slate-500">{{ eventMeta(item.event) }}</p>
              </div>
            </div>
            <span class="rounded-full border px-2.5 py-1 text-[11px] font-medium" :class="eventToneBadge(item.event)">
              #{{ item.event.streamId }}
            </span>
          </div>
          <p v-if="eventSummary(item.event)" class="mt-3 text-xs leading-5 text-slate-500">{{ eventSummary(item.event) }}</p>
        </article>

        <article
          v-else-if="item.type === 'run'"
          class="rounded-[1.65rem] border border-slate-200 bg-white px-4 py-4 shadow-sm"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex min-w-0 gap-3">
              <span class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" :class="runToneDot(item.run.status)" />
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-slate-800">{{ runTitle(item.run.stage) }}</p>
                <p class="mt-1 text-xs text-slate-500">
                  第 {{ item.run.attemptNo }} 次执行
                  <span v-if="item.run.finishedAt"> · {{ formatDate(item.run.finishedAt) }} 完成</span>
                  <span v-else> · {{ formatDate(item.run.startedAt) }} 开始</span>
                </p>
              </div>
            </div>
            <span class="rounded-full border px-2.5 py-1 text-[11px] font-medium" :class="runStatusClass(item.run.status)">
              {{ normalizeStatus(item.run.status) }}
            </span>
          </div>
        </article>

        <article
          v-else
          class="rounded-[1.65rem] border px-4 py-4 shadow-sm"
          :class="item.message.role === 'ASSISTANT' ? 'border-blue-100 bg-blue-50/70' : 'border-slate-200 bg-white'"
        >
          <div class="mb-3 flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="text-sm font-semibold text-slate-800">{{ messageTitle(item.message) }}</div>
              <div class="mt-1 text-xs text-slate-500">{{ messageMeta(item.message) }}</div>
            </div>
            <div class="text-xs text-slate-500">{{ formatDate(item.message.createdAt) }}</div>
          </div>
          <p class="whitespace-pre-wrap text-sm leading-6 text-slate-600">{{ item.message.contentMd }}</p>
          <p v-if="messageSummary(item.message)" class="mt-3 text-xs leading-5 text-slate-500">{{ messageSummary(item.message) }}</p>
        </article>
      </template>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { ProjectEvent, ProjectMessage, ProjectRun } from "../../api";

const props = defineProps<{
  events: ProjectEvent[];
  runs: ProjectRun[];
  messages: ProjectMessage[];
}>();

const timelineItems = computed(() => {
  return [
    ...props.events.map((event, index) => ({
      key: `event-${event.streamId}`,
      type: "event" as const,
      order: Date.parse(event.createdAt) || 0,
      index,
      event
    })),
    ...props.runs.map((run, index) => ({
      key: `run-${run.id}`,
      type: "run" as const,
      order: Date.parse(run.startedAt) || 0,
      index,
      run
    })),
    ...props.messages.map((message, index) => ({
      key: `message-${message.id}`,
      type: "message" as const,
      order: Date.parse(message.createdAt) || 0,
      index,
      message
    }))
  ].sort((left, right) => {
    if (left.order !== right.order) {
      return left.order - right.order;
    }
    return left.index - right.index;
  });
});

function runStatusClass(status: string) {
  switch (status.toUpperCase()) {
    case "COMPLETED":
      return "border-emerald-100 bg-emerald-50 text-emerald-700";
    case "PROCESSING":
    case "RUNNING":
      return "border-blue-100 bg-blue-50 text-blue-700";
    case "FAILED":
      return "border-rose-100 bg-rose-50 text-rose-700";
    default:
      return "border-slate-200 bg-slate-100 text-slate-600";
  }
}

function runToneDot(status: string) {
  switch (status.toUpperCase()) {
    case "COMPLETED":
      return "bg-emerald-500";
    case "PROCESSING":
    case "RUNNING":
      return "bg-blue-500";
    case "FAILED":
      return "bg-rose-500";
    default:
      return "bg-slate-300";
  }
}

function normalizeStatus(status: string) {
  switch (status.toUpperCase()) {
    case "COMPLETED":
      return "done";
    case "PROCESSING":
    case "RUNNING":
      return "running";
    case "FAILED":
      return "failed";
    default:
      return status.toLowerCase();
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function eventSummary(event: ProjectEvent) {
  if (!event.payload) {
    return "";
  }
  const pairs = Object.entries(event.payload)
    .filter(([, value]) => value !== null && value !== undefined && value !== "")
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${String(value)}`);
  return pairs.join(" · ");
}

function eventTitle(event: ProjectEvent) {
  const type = event.eventType.toLowerCase();
  if (type === "agent.run.started") return "Agent 开始执行";
  if (type === "agent.run.completed") return "Agent 执行结束";
  if (type === "router.decision") return "路由决策已更新";
  if (type === "action.step.started") return "步骤开始";
  if (type === "action.step.progress") return "步骤推进中";
  if (type === "action.step.completed") return "步骤完成";
  if (type === "action.step.failed") return "步骤失败";
  if (type === "recommendations.updated") return "推荐动作已更新";
  return event.eventType.replace(/\./g, " ");
}

function eventMeta(event: ProjectEvent) {
  const pieces = [event.stage, event.scopeType];
  if (event.targetPageId) {
    pieces.push(`page ${event.targetPageId.slice(0, 8)}`);
  }
  if (event.agentRunId) {
    pieces.push(`run ${event.agentRunId.slice(0, 8)}`);
  }
  pieces.push(formatDate(event.createdAt));
  return pieces.filter(Boolean).join(" · ");
}

function eventToneDot(event: ProjectEvent) {
  const type = event.eventType.toLowerCase();
  if (type.includes("failed")) return "bg-rose-500";
  if (type.includes("progress") || type.includes("started")) return "bg-blue-500";
  if (type.includes("completed")) return "bg-emerald-500";
  return "bg-slate-400";
}

function eventToneBadge(event: ProjectEvent) {
  const type = event.eventType.toLowerCase();
  if (type.includes("failed")) return "border-rose-100 bg-rose-50 text-rose-700";
  if (type.includes("progress") || type.includes("started")) return "border-blue-100 bg-blue-50 text-blue-700";
  if (type.includes("completed")) return "border-emerald-100 bg-emerald-50 text-emerald-700";
  return "border-slate-200 bg-slate-100 text-slate-600";
}

function runTitle(stage: string) {
  const normalized = stage.toUpperCase();
  if (normalized === "DISCOVERY") return "初始化阶段";
  if (normalized === "OUTLINE") return "大纲阶段";
  if (normalized === "RESEARCH") return "研究阶段";
  if (normalized === "PLANNING") return "策划阶段";
  if (normalized === "DESIGN") return "设计阶段";
  return `${stage.toLowerCase()} stage`;
}

function messageTitle(message: ProjectMessage) {
  if (message.role === "ASSISTANT") {
    return "Assistant";
  }
  if (message.role === "USER") {
    return "User";
  }
  return message.role;
}

function messageMeta(message: ProjectMessage) {
  const pieces = [message.stage, message.scopeType];
  if (message.targetPageId) {
    pieces.push(`page ${message.targetPageId.slice(0, 8)}`);
  }
  return pieces.filter(Boolean).join(" · ");
}

function messageSummary(message: ProjectMessage) {
  if (!message.structuredPayload) {
    return "";
  }
  const pairs = Object.entries(message.structuredPayload)
    .filter(([key, value]) => key !== "content_md" && value !== null && value !== undefined && value !== "")
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${String(value)}`);
  return pairs.join(" · ");
}
</script>
