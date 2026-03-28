export type SlideKind = "title" | "agenda" | "content" | "comparison" | "timeline" | "summary";

export interface ThemePalette {
  primary: string;
  secondary: string;
  accent: string;
  background: string;
  text: string;
}

export interface TemplateSummary {
  id: string;
  name: string;
  description: string;
  slideKinds: SlideKind[];
  defaultTheme: ThemePalette;
}

export type WorkflowStage = "DISCOVERY" | "OUTLINE" | "RESEARCH" | "PLANNING" | "DESIGN";
export type WorkflowSessionStatus = "WAITING_USER" | "PROCESSING" | "COMPLETED" | "FAILED";
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

export interface ToolSubStep {
  label: string;
  status: "completed" | "in_progress" | "pending";
}

export interface ToolCallStep {
  id: string;
  toolName: string;
  displayName: string;
  status: "started" | "completed" | "failed";
  durationMs: number;
  summary?: string;
  subSteps?: ToolSubStep[];
}

export interface ToolProgressEvent {
  toolName: string;
  status: "started" | "completed" | "failed";
  description: string;
  timestamp: string;
  subSteps?: ToolSubStep[];
}

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
  toolCalls?: ToolCallStep[];
  messageType?: "COMMAND" | "CHAT";
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
