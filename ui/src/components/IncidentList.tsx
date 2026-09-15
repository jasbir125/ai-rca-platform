import { useMemo, useState } from "react";
import type { Incident } from "../types";

const STATUS_CLASS: Record<string, string> = {
  PENDING: "badge badge-pending",
  INVESTIGATING: "badge badge-investigating",
  COMPLETED: "badge badge-completed",
  FAILED: "badge badge-failed",
  WAITING_FOR_APPROVAL: "badge badge-pending",
};

function matches(incident: Incident, query: string): boolean {
  const haystack = [
    incident.service,
    incident.environment,
    incident.severity,
    incident.description,
    incident.status,
    incident.summary ?? "",
    incident.probableRootCause ?? "",
    incident.id,
  ]
    .join(" ")
    .toLowerCase();
  return query
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .every((term) => haystack.includes(term));
}

export function IncidentList({
  incidents,
  selectedId,
  onSelect,
  onNewIncident,
}: {
  incidents: Incident[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onNewIncident: () => void;
}) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(
    () => incidents.filter((i) => matches(i, query)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [incidents, query],
  );

  return (
    <div className="incident-list">
      <div className="list-header">
        <input
          className="search-input"
          type="text"
          placeholder="Search incidents (service, description, root cause, status...)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
        />
        <button className="btn btn-primary" onClick={onNewIncident}>
          + New Incident
        </button>
      </div>

      <div className="list-meta">
        {filtered.length} of {incidents.length} incident{incidents.length === 1 ? "" : "s"}
      </div>

      <ul className="list-items">
        {filtered.map((incident) => (
          <li
            key={incident.id}
            className={`list-item ${incident.id === selectedId ? "selected" : ""}`}
            onClick={() => onSelect(incident.id)}
          >
            <div className="list-item-top">
              <span className="service-name">{incident.service}</span>
              <span className={STATUS_CLASS[incident.status] ?? "badge"}>{incident.status}</span>
            </div>
            <div className="list-item-desc">{incident.description}</div>
            {incident.probableRootCause && (
              <div className="list-item-cause">→ {incident.probableRootCause}</div>
            )}
            <div className="list-item-time">{new Date(incident.createdAt).toLocaleString()}</div>
          </li>
        ))}
        {filtered.length === 0 && (
          <li className="list-empty">
            {incidents.length === 0 ? "No incidents yet. Create one to get started." : "No incidents match your search."}
          </li>
        )}
      </ul>
    </div>
  );
}
