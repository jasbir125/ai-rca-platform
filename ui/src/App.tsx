import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import { IncidentList } from "./components/IncidentList";
import { IncidentDetail } from "./components/IncidentDetail";
import { NewIncidentForm } from "./components/NewIncidentForm";
import type { CreateIncidentRequest, Incident } from "./types";

const LIST_POLL_MS = 8000;

export default function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showNewForm, setShowNewForm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const data = await api.listIncidents();
      setIncidents(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, LIST_POLL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function handleCreate(req: CreateIncidentRequest) {
    const created = await api.createIncident(req);
    await api.investigate(created.id);
    setShowNewForm(false);
    await refresh();
    setSelectedId(created.id);
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>RCA Platform</h1>
        <span className="app-subtitle">AI-powered incident investigation</span>
      </header>

      {error && <div className="app-error">Could not reach rca-api: {error}</div>}

      <div className="app-body">
        <IncidentList
          incidents={incidents}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onNewIncident={() => setShowNewForm(true)}
        />
        <main className="app-main">
          {selectedId ? (
            <IncidentDetail incidentId={selectedId} />
          ) : (
            <div className="empty-state">Select an incident, or create a new one to trigger an investigation.</div>
          )}
        </main>
      </div>

      {showNewForm && <NewIncidentForm onCancel={() => setShowNewForm(false)} onCreate={handleCreate} />}
    </div>
  );
}
