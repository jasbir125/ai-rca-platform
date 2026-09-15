export type IncidentStatus = "PENDING" | "INVESTIGATING" | "COMPLETED" | "FAILED" | "WAITING_FOR_APPROVAL";

export interface Incident {
  id: string;
  service: string;
  environment: string;
  severity: string;
  description: string;
  status: IncidentStatus;
  windowStart: string;
  windowEnd: string;
  summary: string | null;
  probableRootCause: string | null;
  confidence: number | null;
  requiresHumanApproval: boolean | null;
  failureReason: string | null;
  impactServices: string[];
  impactBusinessFlows: string[];
  estimatedImpact: string | null;
  similarIncidents: string[];
  nextActions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Evidence {
  id: string;
  source: string;
  service: string;
  type: string;
  description: string;
  value: string;
  confidence: string;
  correlationId: string;
  reference: string | null;
  capturedAt: string;
}

export interface Hypothesis {
  id: string;
  description: string;
  probability: string;
  evidenceSummary: string;
}

export interface Recommendation {
  id: string;
  recommendation: string;
}

export interface TimelineEvent {
  id: string;
  timestamp: string;
  event: string;
}

export interface CreateIncidentRequest {
  service: string;
  environment: string;
  severity: string;
  description: string;
}
