import { useState } from "react";
import type { CreateIncidentRequest } from "../types";

export function NewIncidentForm({
  onCancel,
  onCreate,
}: {
  onCancel: () => void;
  onCreate: (req: CreateIncidentRequest) => Promise<void>;
}) {
  const [service, setService] = useState("order-service");
  const [environment, setEnvironment] = useState("local");
  const [severity, setSeverity] = useState("HIGH");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onCreate({ service, environment, severity, description });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h2>New Incident</h2>

        <label>
          Service
          <input value={service} onChange={(e) => setService(e.target.value)} required />
        </label>

        <label>
          Environment
          <input value={environment} onChange={(e) => setEnvironment(e.target.value)} required />
        </label>

        <label>
          Severity
          <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
            <option>LOW</option>
            <option>MEDIUM</option>
            <option>HIGH</option>
            <option>CRITICAL</option>
          </select>
        </label>

        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Order creation is failing with 500 errors"
            rows={3}
            required
          />
        </label>

        {error && <div className="form-error">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="btn" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? "Creating & investigating..." : "Create & Investigate"}
          </button>
        </div>
      </form>
    </div>
  );
}
