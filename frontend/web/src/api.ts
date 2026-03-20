import type { WorkflowCommandType, WorkflowSessionSnapshot } from "@deckgo/deck-core";

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
  currentVersionId?: string;
  createdAt: string;
}

export interface DeckVersionDto {
  id: string;
  projectId: string;
  versionNumber: number;
  source: string;
  note?: string;
  templateId: string;
  specTitle: string;
  slideCount: number;
  deckSpec: unknown;
  createdAt: string;
}

export async function fetchProjects(): Promise<ProjectDto[]> {
  return request<ProjectDto[]>("/api/projects");
}

export async function fetchTemplates() {
  return request("/api/templates");
}

export async function fetchVersions(projectId: string): Promise<DeckVersionDto[]> {
  return request<DeckVersionDto[]>(`/api/projects/${projectId}/versions`);
}

export async function createProject(payload: {
  title: string;
  topic: string;
  audience: string;
  templateId: string;
}) {
  return request<ProjectDto>("/api/projects", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createVersion(projectId: string, payload: {
  deckSpec: unknown;
  note: string;
  source: string;
}) {
  return request<DeckVersionDto>(`/api/projects/${projectId}/versions`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createDraft(payload: {
  topic: string;
  audience: string;
  goal: string;
  templateId: string;
  slideCountHint?: number;
}) {
  return request("/api/ai/deck-drafts", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createRevision(payload: {
  projectId: string;
  baseVersionId: string;
  instruction: string;
}) {
  return request("/api/ai/deck-revisions", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createRenderJob(projectId: string, payload: { deckVersionId: string; format: string }) {
  return request(`/api/projects/${projectId}/render-jobs`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createWorkflowSession(prompt: string): Promise<WorkflowSessionSnapshot> {
  return request<WorkflowSessionSnapshot>("/api/workflow-sessions", {
    method: "POST",
    body: JSON.stringify({ prompt })
  });
}

export async function fetchWorkflowSession(sessionId: string): Promise<WorkflowSessionSnapshot> {
  return request<WorkflowSessionSnapshot>(`/api/workflow-sessions/${sessionId}`);
}

export async function sendWorkflowCommand(
  sessionId: string,
  payload: {
    command: WorkflowCommandType;
    selectedOptionIds?: string[];
    freeformAnswer?: string;
    feedback?: string;
  }
): Promise<WorkflowSessionSnapshot> {
  return request<WorkflowSessionSnapshot>(`/api/workflow-sessions/${sessionId}/commands`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
