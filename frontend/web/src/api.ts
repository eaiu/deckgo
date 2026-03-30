export type ProjectStage = "DISCOVERY" | "OUTLINE" | "RESEARCH" | "PLANNING" | "DESIGN";

export interface ProjectSummary {
  projectId: string;
  title: string;
  requestText: string;
  currentStage: ProjectStage;
  previewSurface?: "research" | "planning" | "design" | null;
  previewSvgMarkup?: string | null;
  pageCountTarget: number | null;
  stylePreset: string | null;
  backgroundAssetPath: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateOption {
  id: string;
  name: string;
  description: string;
}

export interface RequirementQuestionOption {
  optionCode: string;
  label: string;
  description?: string;
}

export interface RequirementQuestion {
  questionCode: string;
  label: string;
  description?: string;
  options: RequirementQuestionOption[];
}

export interface RequirementSourceItem {
  id: string;
  title: string;
  url: string;
  snippet: string;
}

export interface RequirementFormData {
  projectId: string;
  status: string;
  summaryMd: string;
  questions: RequirementQuestion[];
  sources: RequirementSourceItem[];
  answers: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMessage {
  id: string;
  stage: string;
  scopeType: string;
  targetPageId?: string | null;
  role: string;
  contentMd: string;
  structuredPayload?: Record<string, unknown> | null;
  createdAt: string;
}

export interface ProjectRun {
  id: string;
  stage: string;
  attemptNo: number;
  status: string;
  startedAt: string;
  finishedAt?: string | null;
}

export interface ProjectPage {
  id: string;
  pageCode: string;
  pageRole?: string | null;
  partTitle?: string | null;
  sortOrder: number;
  outlineStatus: string;
  searchStatus: string;
  summaryStatus: string;
  draftStatus: string;
  designStatus: string;
  currentBrief?: Record<string, unknown> | null;
  currentResearch?: Record<string, unknown> | null;
  currentDraftSvg?: string | null;
  currentDesignSvg?: string | null;
  citations: Array<Record<string, unknown>>;
}

export interface StudioProject {
  projectId: string;
  title: string;
  requestText: string;
  currentStage: ProjectStage;
  templateId: string;
  pageCountTarget: number | null;
  stylePreset: string | null;
  backgroundAssetPath: string | null;
  requirementForm?: RequirementFormData | null;
  messages: ProjectMessage[];
  pages: ProjectPage[];
  projectRuns: ProjectRun[];
  createdAt: string;
  updatedAt: string;
}

export interface ProjectEvent {
  streamId: number;
  eventId: string;
  projectId: string;
  eventType: string;
  stage: string;
  scopeType: string;
  targetPageId?: string | null;
  agentRunId?: string | null;
  payload?: Record<string, unknown> | null;
  createdAt: string;
}

interface PptActionJobResponse {
  status: string;
  agentRunId: string;
}

class ApiError extends Error {
  status: number;
  errors: string[];

  constructor(message: string, status: number, errors: string[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

type BackendProjectSummary = {
  id: string;
  title: string;
  topic: string;
  requestText: string;
  currentStage: string;
  pageCountTarget?: number | null;
  stylePreset?: string | null;
  backgroundAssetPath?: string | null;
  createdAt: string;
  updatedAt: string;
};

type BackendProjectDetail = BackendProjectSummary & {
  audience: string;
  templateId: string;
  currentOutlineVersionId?: string | null;
  workflowConstraints?: Record<string, unknown> | null;
};

type BackendTemplateSummary = {
  id: string;
  name: string;
  description: string;
};

type BackendRequirementFormSnapshot = {
  id: string;
  status: string;
  basedOnOutlineVersionId?: string | null;
  summaryMd?: string | null;
  outlineContextMd?: string | null;
  fixedItems?: Record<string, unknown> | null;
  initSearchQueries?: unknown;
  initSearchResults?: unknown;
  initCorpusDigest?: Record<string, unknown> | null;
  aiQuestions?: unknown;
  answers?: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
};

type BackendProjectMessageSnapshot = {
  id: string;
  stage: string;
  scopeType: string;
  targetPageId?: string | null;
  role: string;
  contentMd: string;
  structuredPayload?: Record<string, unknown> | null;
  createdAt: string;
};

type BackendStageRunSnapshot = {
  id: string;
  stage: string;
  attemptNo: number;
  status: string;
  startedAt: string;
  finishedAt?: string | null;
};

type BackendProjectPageSnapshot = {
  id: string;
  pageCode: string;
  pageRole?: string | null;
  partTitle?: string | null;
  sortOrder: number;
  currentBriefVersionId?: string | null;
  currentResearchSessionId?: string | null;
  currentDraftVersionId?: string | null;
  currentDesignVersionId?: string | null;
  outlineStatus: string;
  searchStatus: string;
  summaryStatus: string;
  draftStatus: string;
  designStatus: string;
  artifactStaleness?: Record<string, unknown> | null;
  currentBrief?: Record<string, unknown> | null;
  currentResearch?: Record<string, unknown> | null;
  currentDraftSvg?: string | null;
  currentDesignSvg?: string | null;
  citations?: Array<Record<string, unknown>> | null;
  createdAt: string;
  updatedAt: string;
};

type BackendProjectStudioSnapshot = {
  projectId: string;
  title: string;
  topic: string;
  audience: string;
  templateId: string;
  requestText: string;
  currentStage: string;
  currentOutlineVersionId?: string | null;
  pageCountTarget?: number | null;
  stylePreset?: string | null;
  backgroundAssetPath?: string | null;
  workflowConstraints?: Record<string, unknown> | null;
  requirementForm?: BackendRequirementFormSnapshot | null;
  currentOutline?: Record<string, unknown> | null;
  pages: BackendProjectPageSnapshot[];
  messages: BackendProjectMessageSnapshot[];
  projectRuns: BackendStageRunSnapshot[];
  createdAt: string;
  updatedAt: string;
};

type BackendProjectStudioChatResponse = {
  projectId: string;
  assistantMessage: string;
  snapshot: BackendProjectStudioSnapshot;
};

type BackendProjectEvent = {
  streamId: number;
  eventId: string;
  projectId: string;
  eventType: string;
  stage: string;
  scopeType: string;
  targetPageId?: string | null;
  agentRunId?: string | null;
  payload?: Record<string, unknown> | null;
  createdAt: string;
};

type ApiErrorPayload = {
  code?: string;
  message?: string;
  errors?: string[];
};

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const PROJECT_EVENT_NAMES = [
  "PROJECT_CREATED",
  "BACKGROUND_RESEARCH_STARTED",
  "BACKGROUND_RESEARCH_COMPLETED",
  "DISCOVERY_CARD_GENERATED",
  "OUTLINE_GENERATED",
  "OUTLINE_REVISED",
  "PAGE_RESEARCH_COMPLETED",
  "RESEARCH_COMPLETED",
  "PAGE_PLANNED",
  "PLANNING_COMPLETED",
  "PAGE_DESIGNED",
  "DESIGN_COMPLETED",
  "PAGE_REDESIGNED",
  "COMMAND_FAILED",
  "agent.run.started",
  "router.decision",
  "action.step.started",
  "action.step.progress",
  "action.step.completed",
  "action.step.failed",
  "recommendations.updated",
  "agent.message",
  "agent.run.completed"
] as const;

function buildUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function toProjectStage(stage: string | null | undefined): ProjectStage {
  switch (String(stage || "").toUpperCase()) {
    case "OUTLINE":
      return "OUTLINE";
    case "RESEARCH":
      return "RESEARCH";
    case "PLANNING":
      return "PLANNING";
    case "DESIGN":
      return "DESIGN";
    case "DISCOVERY":
    default:
      return "DISCOVERY";
  }
}

function toPreviewSurface(stage: ProjectStage): ProjectSummary["previewSurface"] {
  if (stage === "DESIGN") return "design";
  if (stage === "PLANNING") return "planning";
  return "research";
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!(init?.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(buildUrl(path), {
    ...init,
    headers
  });

  const text = await response.text();
  const contentType = response.headers.get("content-type") || "";
  const payload = text && contentType.includes("application/json") ? (JSON.parse(text) as unknown) : text;

  if (!response.ok) {
    const apiError = isRecord(payload) ? (payload as ApiErrorPayload) : null;
    throw new ApiError(
      apiError?.message || response.statusText || "请求失败",
      response.status,
      Array.isArray(apiError?.errors) ? apiError.errors : []
    );
  }

  return payload as T;
}

function mapProjectSummary(payload: BackendProjectSummary | BackendProjectDetail): ProjectSummary {
  const currentStage = toProjectStage(payload.currentStage);
  return {
    projectId: payload.id,
    title: payload.title,
    requestText: payload.requestText || payload.topic || payload.title,
    currentStage,
    previewSurface: toPreviewSurface(currentStage),
    previewSvgMarkup: null,
    pageCountTarget: payload.pageCountTarget ?? null,
    stylePreset: payload.stylePreset ?? null,
    backgroundAssetPath: payload.backgroundAssetPath ?? null,
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt
  };
}

function mapRequirementQuestion(raw: unknown, index: number): RequirementQuestion | null {
  if (!isRecord(raw)) {
    return null;
  }

  const optionsRaw = Array.isArray(raw.options) ? raw.options : [];
  const options: RequirementQuestionOption[] = [];
  optionsRaw.forEach((option, optionIndex) => {
    if (!isRecord(option)) {
      return;
    }
    options.push({
      optionCode: String(option.id ?? option.optionCode ?? `option-${optionIndex + 1}`),
      label: String(option.label ?? "未命名选项"),
      description: typeof option.description === "string" ? option.description : undefined
    });
  });

  return {
    questionCode: String(raw.id ?? raw.questionCode ?? `question-${index + 1}`),
    label: String(raw.prompt ?? raw.label ?? "未命名问题"),
    description: typeof raw.description === "string" ? raw.description : undefined,
    options
  };
}

function mapRequirementSource(raw: unknown, index: number): RequirementSourceItem | null {
  if (!isRecord(raw)) {
    return null;
  }

  const title = String(raw.title ?? raw.name ?? `来源 ${index + 1}`);
  const url = String(raw.url ?? raw.link ?? "");
  const snippet = String(raw.snippet ?? raw.content ?? raw.description ?? "");
  return {
    id: String(raw.id ?? `${url || title}-${index + 1}`),
    title,
    url,
    snippet
  };
}

function mapRequirementForm(projectId: string, snapshot: BackendRequirementFormSnapshot): RequirementFormData {
  const aiQuestionsRoot = isRecord(snapshot.aiQuestions) ? snapshot.aiQuestions : null;
  const questionsRaw = Array.isArray(aiQuestionsRoot?.questions) ? aiQuestionsRoot.questions : [];

  const initSearchResultsRoot = isRecord(snapshot.initSearchResults) ? snapshot.initSearchResults : null;
  const initSearchResultsSources = Array.isArray(initSearchResultsRoot?.sources) ? initSearchResultsRoot.sources : [];
  const initSearchQueriesSources = Array.isArray(snapshot.initSearchQueries) ? snapshot.initSearchQueries : [];
  const sourcesRaw = initSearchResultsSources.length ? initSearchResultsSources : initSearchQueriesSources;

  return {
    projectId,
    status: snapshot.status,
    summaryMd: snapshot.summaryMd || snapshot.outlineContextMd || "",
    questions: questionsRaw
      .map((question, index) => mapRequirementQuestion(question, index))
      .filter((question): question is RequirementQuestion => Boolean(question)),
    sources: sourcesRaw
      .map((source, index) => mapRequirementSource(source, index))
      .filter((source): source is RequirementSourceItem => Boolean(source)),
    answers: isRecord(snapshot.answers) ? snapshot.answers : {},
    createdAt: snapshot.createdAt,
    updatedAt: snapshot.updatedAt
  };
}

function mapProjectMessage(message: BackendProjectMessageSnapshot): ProjectMessage {
  return {
    id: message.id,
    stage: message.stage,
    scopeType: message.scopeType,
    targetPageId: message.targetPageId ?? null,
    role: message.role,
    contentMd: message.contentMd,
    structuredPayload: isRecord(message.structuredPayload) ? message.structuredPayload : null,
    createdAt: message.createdAt
  };
}

function mapProjectRun(run: BackendStageRunSnapshot): ProjectRun {
  return {
    id: run.id,
    stage: run.stage,
    attemptNo: run.attemptNo,
    status: run.status,
    startedAt: run.startedAt,
    finishedAt: run.finishedAt ?? null
  };
}

function mapProjectPage(page: BackendProjectPageSnapshot): ProjectPage {
  const brief = isRecord(page.currentBrief) ? page.currentBrief : null;
  const briefTitle = brief && typeof brief.title === "string" && brief.title.trim() ? brief.title.trim() : null;
  return {
    id: page.id,
    pageCode: briefTitle || page.pageCode,
    pageRole: page.pageRole ?? null,
    partTitle: page.partTitle ?? null,
    sortOrder: page.sortOrder,
    outlineStatus: page.outlineStatus,
    searchStatus: page.searchStatus,
    summaryStatus: page.summaryStatus,
    draftStatus: page.draftStatus,
    designStatus: page.designStatus,
    currentBrief: brief,
    currentResearch: isRecord(page.currentResearch) ? page.currentResearch : null,
    currentDraftSvg: page.currentDraftSvg ?? null,
    currentDesignSvg: page.currentDesignSvg ?? null,
    citations: Array.isArray(page.citations) ? page.citations : []
  };
}

function mapStudioProject(snapshot: BackendProjectStudioSnapshot): StudioProject {
  return {
    projectId: snapshot.projectId,
    title: snapshot.title,
    requestText: snapshot.requestText || snapshot.topic || snapshot.title,
    currentStage: toProjectStage(snapshot.currentStage),
    templateId: snapshot.templateId,
    pageCountTarget: snapshot.pageCountTarget ?? null,
    stylePreset: snapshot.stylePreset ?? null,
    backgroundAssetPath: snapshot.backgroundAssetPath ?? null,
    requirementForm: snapshot.requirementForm ? mapRequirementForm(snapshot.projectId, snapshot.requirementForm) : null,
    messages: Array.isArray(snapshot.messages) ? snapshot.messages.map(mapProjectMessage) : [],
    pages: Array.isArray(snapshot.pages) ? snapshot.pages.map(mapProjectPage) : [],
    projectRuns: Array.isArray(snapshot.projectRuns) ? snapshot.projectRuns.map(mapProjectRun) : [],
    createdAt: snapshot.createdAt,
    updatedAt: snapshot.updatedAt
  };
}

function mapProjectEvent(event: BackendProjectEvent): ProjectEvent {
  return {
    streamId: event.streamId,
    eventId: event.eventId,
    projectId: event.projectId,
    eventType: event.eventType,
    stage: event.stage,
    scopeType: event.scopeType,
    targetPageId: event.targetPageId ?? null,
    agentRunId: event.agentRunId ?? null,
    payload: isRecord(event.payload) ? event.payload : null,
    createdAt: event.createdAt
  };
}

function deriveProjectTitle(requestText: string, title?: string) {
  const candidate = (title || requestText || "未命名项目").trim().replace(/\s+/g, " ");
  return candidate.length > 28 ? candidate.slice(0, 28) : candidate;
}

export async function listProjects(): Promise<ProjectSummary[]> {
  return request<ProjectSummary[]>("/api/v1/projects");
}

export async function createProject(requestText: string, title?: string): Promise<ProjectSummary> {
  return request<ProjectSummary>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify({
      title: deriveProjectTitle(requestText, title),
      requestText
    })
  });
}

export async function updateProject(
  projectId: string,
  payload: {
    title: string;
    topic: string;
    audience: string;
    templateId?: string | null;
    requestText?: string | null;
    pageCountTarget?: number | null;
    stylePreset?: string | null;
    backgroundAssetPath?: string | null;
    workflowConstraints?: Record<string, unknown> | null;
  }
): Promise<ProjectSummary> {
  const response = await request<BackendProjectDetail>(`/api/projects/${projectId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
  return mapProjectSummary(response);
}

export async function getProject(projectId: string): Promise<ProjectSummary> {
  return request<ProjectSummary>(`/api/v1/projects/${projectId}`);
}

export async function fetchTemplates(): Promise<TemplateOption[]> {
  const payload = await request<BackendTemplateSummary[]>("/api/templates");
  return payload.map((item) => ({
    id: item.id,
    name: item.name,
    description: item.description
  }));
}

export async function getStudioProject(projectId: string): Promise<StudioProject> {
  const payload = await request<BackendProjectStudioSnapshot>(`/api/studio/projects/${projectId}`);
  return mapStudioProject(payload);
}

export async function getRequirementForm(projectId: string): Promise<RequirementFormData> {
  return request<RequirementFormData>(`/api/v1/projects/${projectId}/requirements/form`);
}

export async function submitRequirementAnswers(
  projectId: string,
  answers: Array<{ questionCode: string; value: unknown }>
): Promise<RequirementFormData> {
  return request<RequirementFormData>(`/api/v1/projects/${projectId}/requirements/answers:batch`, {
    method: "POST",
    body: JSON.stringify({
      answers: answers.map((item) => ({
        questionCode: item.questionCode,
        value: item.value
      }))
    })
  });
}

export async function patchRequirementAnswer(projectId: string, questionCode: string, value: unknown): Promise<RequirementFormData> {
  return request<RequirementFormData>(`/api/v1/projects/${projectId}/requirements/answers/${questionCode}`, {
    method: "PATCH",
    body: JSON.stringify({ value })
  });
}

export async function confirmRequirements(projectId: string, noteMd?: string): Promise<StudioProject> {
  const payload = await request<BackendProjectStudioSnapshot>(`/api/v1/projects/${projectId}/requirements/confirm`, {
    method: "POST",
    body: JSON.stringify({ noteMd: noteMd ?? null })
  });
  return mapStudioProject(payload);
}

export async function runProjectCommand(projectId: string, command: string): Promise<StudioProject> {
  const actionType =
    command === "CONTINUE_TO_RESEARCH"
      ? "project_batch_search"
      : command === "CONTINUE_TO_PLANNING"
      ? "project_batch_draft"
      : command === "CONTINUE_TO_DESIGN"
      ? "project_batch_design"
      : null;

  if (!actionType) {
    throw new ApiError(`不支持的命令: ${command}`, 400);
  }

  await request<PptActionJobResponse>(`/api/v1/projects/${projectId}/actions/batch`, {
    method: "POST",
    body: JSON.stringify({
      actionType
    })
  });
  return getStudioProject(projectId);
}

export async function sendProjectMessage(
  projectId: string,
  message: string,
  options?: {
    scopeType?: "PROJECT" | "PAGE";
    targetPageId?: string | null;
    uiSurface?: string | null;
  }
): Promise<StudioProject> {
  await request<ProjectMessage>(`/api/v1/projects/${projectId}/messages`, {
    method: "POST",
    body: JSON.stringify({
      scopeType: options?.scopeType ?? "PROJECT",
      targetPageId: options?.targetPageId ?? null,
      uiSurface: options?.uiSurface ?? null,
      contentMd: message,
      attachments: []
    })
  });
  return getStudioProject(projectId);
}

export async function redesignProjectPage(projectId: string, pageId: string, instruction?: string): Promise<ProjectPage> {
  const payload = await request<BackendProjectPageSnapshot>(`/api/studio/projects/${projectId}/pages/${pageId}/redesign`, {
    method: "POST",
    body: JSON.stringify({ instruction: instruction ?? null })
  });
  return mapProjectPage(payload);
}

export function connectProjectEventStream(
  projectId: string,
  handlers: {
    onEvent: (event: ProjectEvent) => void;
    onError?: () => void;
  }
) {
  const source = new EventSource(buildUrl(`/api/v1/projects/${projectId}/events/stream`));
  const handleEvent = (rawEvent: MessageEvent<string>) => {
    try {
      handlers.onEvent(mapProjectEvent(JSON.parse(rawEvent.data) as BackendProjectEvent));
    } catch {
      handlers.onError?.();
    }
  };

  source.onmessage = handleEvent;
  PROJECT_EVENT_NAMES.forEach((eventName) => {
    source.addEventListener(eventName, handleEvent as EventListener);
  });
  source.onerror = () => {
    handlers.onError?.();
  };

  return () => {
    PROJECT_EVENT_NAMES.forEach((eventName) => {
      source.removeEventListener(eventName, handleEvent as EventListener);
    });
    source.close();
  };
}
