import type {
  CreateIncidentRequest,
  Evidence,
  Hypothesis,
  Incident,
  Recommendation,
  TimelineEvent,
} from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8090";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`${init?.method ?? "GET"} ${path} -> ${res.status}: ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  listIncidents: () => request<Incident[]>("/api/incidents"),
  getIncident: (id: string) => request<Incident>(`/api/incidents/${id}`),
  createIncident: (body: CreateIncidentRequest) =>
    request<Incident>("/api/incidents", { method: "POST", body: JSON.stringify(body) }),
  investigate: (id: string) =>
    request<{ incidentId: string; status: string }>(`/api/incidents/${id}/investigate`, { method: "POST" }),
  getEvidence: (id: string) => request<Evidence[]>(`/api/incidents/${id}/evidence`),
  getHypotheses: (id: string) => request<Hypothesis[]>(`/api/incidents/${id}/hypotheses`),
  getTimeline: (id: string) => request<TimelineEvent[]>(`/api/incidents/${id}/timeline`),
  getRecommendations: (id: string) => request<Recommendation[]>(`/api/incidents/${id}/recommendations`),
};
