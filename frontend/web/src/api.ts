import type { ToolCallStep, ToolProgressEvent, WorkflowCommandType, WorkflowSessionSnapshot } from "@deckgo/deck-core";

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

export interface ChatResponseDto {
  sessionId: string;
  assistantMessage: string;
  toolCalls: ToolCallStep[];
  sessionSnapshot: WorkflowSessionSnapshot;
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatResponseDto> {
  return request<ChatResponseDto>(`/api/workflow-sessions/${sessionId}/chat`, {
    method: "POST",
    body: JSON.stringify({ message }),
    signal: AbortSignal.timeout(300_000)
  });
}

export function subscribeProgress(
  sessionId: string,
  onEvent: (event: ToolProgressEvent) => void
): EventSource {
  const source = new EventSource(`${API_BASE_URL}/api/workflow-sessions/${sessionId}/progress`);
  source.addEventListener("tool-progress", (e) => {
    try {
      onEvent(JSON.parse((e as MessageEvent).data) as ToolProgressEvent);
    } catch { /* ignore parse errors */ }
  });
  source.onerror = () => {
    // SSE connection lost — silently ignore, will be closed by caller
  };
  return source;
}
