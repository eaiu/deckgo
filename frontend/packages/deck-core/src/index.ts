export type SlideKind = "title" | "agenda" | "content" | "comparison" | "timeline" | "summary";
export type BlockKind = "text" | "table" | "chart" | "image" | "shape" | "diagram_art";

export interface Frame {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface ThemePalette {
  primary: string;
  secondary: string;
  accent: string;
  background: string;
  text: string;
}

export interface ThemeSpec {
  id: string;
  name: string;
  palette: ThemePalette;
}

export interface TextBlock {
  id: string;
  kind: "text";
  frame: Frame;
  content: {
    text: string;
    level: "h1" | "h2" | "body" | "caption";
  };
}

export interface TableBlock {
  id: string;
  kind: "table";
  frame: Frame;
  content: {
    headers: string[];
    rows: string[][];
  };
}

export interface ChartBlock {
  id: string;
  kind: "chart";
  frame: Frame;
  content: {
    chartType: "bar" | "line" | "pie";
    series: Array<{ name: string; values: number[] }>;
  };
}

export interface ImageBlock {
  id: string;
  kind: "image";
  frame: Frame;
  content: {
    src: string;
    fit: "contain" | "cover" | "crop";
    alt?: string;
  };
}

export interface ShapeBlock {
  id: string;
  kind: "shape";
  frame: Frame;
  content: {
    shape: "rect" | "rounded_rect" | "line" | "arrow";
    label: string;
  };
}

export interface DiagramArtBlock {
  id: string;
  kind: "diagram_art";
  frame: Frame;
  content: {
    svg: string;
    description?: string;
  };
}

export type BlockSpec = TextBlock | TableBlock | ChartBlock | ImageBlock | ShapeBlock | DiagramArtBlock;

export interface SlideSpec {
  id: string;
  kind: SlideKind;
  title: string;
  notes?: string;
  blocks: BlockSpec[];
}

export interface DeckSpec {
  version: string;
  deckId: string;
  title: string;
  templateId: string;
  theme: ThemeSpec;
  slides: SlideSpec[];
}

export interface TemplateSummary {
  id: string;
  name: string;
  description: string;
  slideKinds: SlideKind[];
  defaultTheme: ThemePalette;
}

export type WorkflowStage = "DISCOVERY" | "OUTLINE" | "RESEARCH" | "PLANNING" | "DESIGN";
export type WorkflowSessionStatus = "WAITING_USER" | "COMPLETED" | "FAILED";
export type WorkflowMessageRole = "USER" | "ASSISTANT" | "SYSTEM";
export type WorkflowCommandType =
  | "SUBMIT_DISCOVERY"
  | "APPLY_OUTLINE_FEEDBACK"
  | "CONTINUE_TO_RESEARCH"
  | "CONTINUE_TO_PLANNING"
  | "CONTINUE_TO_DESIGN";

export interface DiscoveryOption {
  id: string;
  label: string;
  description: string;
}

export interface DiscoveryQuestion {
  id: string;
  prompt: string;
  options: DiscoveryOption[];
}

export interface DiscoveryCard {
  title: string;
  description: string;
  freeformHint?: string;
  questions: DiscoveryQuestion[];
}

export interface BackgroundSummary {
  summary: string;
  topicUnderstanding?: string;
  answer?: string;
  sources: Array<{
    title: string;
    url: string;
    content: string;
    score?: number;
    favicon?: string;
  }>;
}

export interface DiscoveryAnswers {
  selectedOptionIds: string[];
  freeformAnswer?: string;
}

export interface OutlinePage {
  id: string;
  title: string;
  intent: string;
}

export interface OutlineSection {
  id: string;
  title: string;
  revisionNote?: string;
  pages: OutlinePage[];
}

export interface OutlineDoc {
  title: string;
  narrative: string;
  sections: OutlineSection[];
}

export type PageLayout = "hero" | "two-column" | "three-column" | "comparison" | "timeline" | "bento-grid" | "summary";
export type PageCardKind = "text" | "metric" | "chart" | "table" | "comparison" | "timeline" | "quote" | "image" | "highlight";

export interface PagePlanCard {
  id: string;
  kind: PageCardKind;
  heading: string;
  body: string;
  supportingPoints?: string[];
  chartType?: string;
  tableHeaders?: string[];
  imageIntent?: string;
  emphasis?: "high" | "medium" | "low";
}

export interface PagePlan {
  pageId: string;
  title: string;
  goal: string;
  layout: PageLayout;
  visualTone?: string;
  speakerNotes?: string;
  cards: PagePlanCard[];
}

export interface SvgPage {
  id: string;
  orderIndex: number;
  title: string;
  pagePlan: PagePlan;
  draftSvg?: string | null;
  finalSvg?: string | null;
}

export interface PageResearchItem {
  pageId: string;
  title: string;
  needsSearch: boolean;
  searchIntent: string;
  queries: string[];
  searchDepth: string;
  findings: string;
  sources: Array<{
    title: string;
    url: string;
    content: string;
    score?: number;
    favicon?: string;
  }>;
}

export type PageResearch = PageResearchItem[];

export interface WorkflowMessage {
  id: string;
  role: WorkflowMessageRole;
  stage: WorkflowStage;
  content: {
    text?: string;
    freeformAnswer?: string;
    selectedOptionIds?: string[];
    [key: string]: unknown;
  };
  createdAt: string;
}

export interface WorkflowProjectSummary {
  id: string;
  title: string;
  topic: string;
  audience: string;
  templateId: string;
}

export interface WorkflowSessionSnapshot {
  sessionId: string;
  currentStage: WorkflowStage;
  status: WorkflowSessionStatus;
  currentVersionId?: string | null;
  selectedTemplateId: string;
  lastError?: string | null;
  project: WorkflowProjectSummary;
  messages: WorkflowMessage[];
  backgroundSummary?: BackgroundSummary | null;
  discoveryCard?: DiscoveryCard | null;
  discoveryAnswers?: DiscoveryAnswers | null;
  outline?: OutlineDoc | null;
  pageResearch?: PageResearch | null;
  pages: SvgPage[];
  updatedAt: string;
}

export function isDeckSpec(value: unknown): value is DeckSpec {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<DeckSpec>;
  return typeof candidate.title === "string" && Array.isArray(candidate.slides);
}

export function parseDeckSpec(source: string): DeckSpec {
  const parsed = JSON.parse(source) as unknown;
  if (!isDeckSpec(parsed)) {
    throw new Error("输入内容不是有效的 DeckSpec");
  }
  return parsed;
}

export function prettyDeckSpec(deck: DeckSpec): string {
  return JSON.stringify(deck, null, 2);
}

export function summarizeDeck(deck: DeckSpec): string {
  return `${deck.title} · ${deck.slides.length} 页 · 模板 ${deck.templateId}`;
}

export function workflowStageLabel(stage: WorkflowStage): string {
  switch (stage) {
    case "DISCOVERY":
      return "背景调研";
    case "OUTLINE":
      return "大纲策划";
    case "RESEARCH":
      return "资料搜集";
    case "PLANNING":
      return "策划阶段";
    case "DESIGN":
      return "最终设计稿";
  }
}

export const SAMPLE_DECK: DeckSpec = {
  version: "1.0.0",
  deckId: "sample-deck",
  title: "DeckGo V1 项目框架",
  templateId: "clarity-blue",
  theme: {
    id: "clarity-blue",
    name: "Clarity Blue",
    palette: {
      primary: "#0F5FFF",
      secondary: "#1A2A52",
      accent: "#00C2A8",
      background: "#F7F9FC",
      text: "#172033"
    }
  },
  slides: [
    {
      id: "slide-1",
      kind: "title",
      title: "DeckGo V1 项目框架",
      notes: "面向学习和实践的大模型 PPT 生成系统。",
      blocks: [
        {
          id: "block-1",
          kind: "text",
          frame: { x: 0.8, y: 0.9, w: 11, h: 1.4 },
          content: { text: "DeckSpec 是整个系统的唯一真相。", level: "h1" }
        },
        {
          id: "block-2",
          kind: "text",
          frame: { x: 0.8, y: 2, w: 10.6, h: 1.2 },
          content: { text: "Spring Boot 负责业务流程，Vue 负责工作台，Node 负责可编辑 PPTX 导出。", level: "body" }
        }
      ]
    }
  ]
};
