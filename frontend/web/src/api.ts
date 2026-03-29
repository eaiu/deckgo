import type { BackgroundSummary, DiscoveryCard } from "@deckgo/deck-core";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  const payload = await response.text();
  const parsed = payload ? JSON.parse(payload) : null;

  if (!response.ok) {
    const message = parsed?.message ?? `请求失败: ${response.status}`;
    throw new Error(message);
  }

  return parsed as T;
}

export interface ProjectDto {
  id: string;
  title: string;
  topic: string;
  audience: string;
  templateId: string;
  createdAt: string;
  updatedAt: string;
}

export async function fetchProjects(): Promise<ProjectDto[]> {
  return request<ProjectDto[]>("/api/projects");
}

export async function fetchTemplates() {
  return request("/api/templates");
}

export async function createProject(payload: {
  prompt: string;
  pageCountTarget?: number;
  stylePreset?: string;
  backgroundAssetPath?: string;
  workflowConstraints?: Record<string, unknown>;
}) {
  return request<ProjectStudioSnapshotDto>("/api/projects", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export interface RequirementFormDto {
  id: string;
  status: string;
  basedOnOutlineVersionId?: string | null;
  summaryMd?: string | null;
  outlineContextMd?: string | null;
  fixedItems?: Record<string, unknown> | null;
  initSearchQueries?: unknown;
  initSearchResults?: BackgroundSummary | null;
  initCorpusDigest?: Record<string, unknown> | null;
  aiQuestions?: DiscoveryCard | null;
  answers?: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMessageDto {
  id: string;
  stage: string;
  scopeType: string;
  targetPageId?: string | null;
  role: string;
  contentMd: string;
  structuredPayload?: Record<string, unknown> | null;
  createdAt: string;
}

export interface StageRunDto {
  id: string;
  stage: string;
  attemptNo: number;
  status: string;
  inputRefs?: Record<string, unknown> | null;
  outputRef?: Record<string, unknown> | null;
  errorMessage?: string | null;
  startedAt: string;
  finishedAt?: string | null;
}

export interface ProjectStudioSnapshotDto {
  projectId: string;
  title: string;
  topic: string;
  audience: string;
  templateId: string;
  requestText?: string | null;
  currentStage: string;
  currentOutlineVersionId?: string | null;
  pageCountTarget?: number | null;
  stylePreset?: string | null;
  backgroundAssetPath?: string | null;
  workflowConstraints?: Record<string, unknown> | null;
  requirementForm?: RequirementFormDto | null;
  currentOutline?: Record<string, unknown> | null;
  pages: Array<Record<string, unknown>>;
  messages: ProjectMessageDto[];
  projectRuns: StageRunDto[];
  createdAt: string;
  updatedAt: string;
}

export async function fetchProject(projectId: string): Promise<ProjectStudioSnapshotDto> {
  return request<ProjectStudioSnapshotDto>(`/api/projects/${projectId}`);
}
