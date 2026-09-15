import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import type { Evidence, Hypothesis, Incident, Recommendation, TimelineEvent } from "../types";
import { extractErrorSignals, extractExceptions, sourceLabel } from "../utils/incidentAnalysis";

const POLL_MS = 5000;
const ACTIVE_STATUSES = new Set(["PENDING", "INVESTIGATING"]);

/** The backend accepts either vocabulary for Hypothesis.probability (see RcaReport's
 *  javadoc: "High/Medium/Low/Very Low" or "Confirmed/Highly probable/Probable/
 *  Possible/Unknown", model-dependent) — bucket by keyword instead of exact string so
 *  the badge color doesn't silently disappear for whichever one a given model uses. */
function probabilityBucket(probability: string): "high" | "medium" | "low" | "unknown" {
  const p = probability.toLowerCase();
  if (p.includes("confirmed") || p.includes("highly probable") || p === "high") return "high";
  if (p.includes("probable") || p === "medium" || p === "possible") return "medium";
  if (p.includes("low")) return "low";
  return "unknown";
}

export function IncidentDetail({ incidentId }: { incidentId: string }) {
  const [incident, setIncident] = useState<Incident | null>(null);
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [hypotheses, setHypotheses] = useState<Hypothesis[]>([]);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    async function load() {
      try {
        const inc = await api.getIncident(incidentId);
        if (cancelled) return;
        setIncident(inc);
        setError(null);

        if (inc.status === "COMPLETED" || inc.status === "FAILED") {
          const [ev, hyp, tl, rec] = await Promise.all([
            api.getEvidence(incidentId),
            api.getHypotheses(incidentId),
            api.getTimeline(incidentId),
            api.getRecommendations(incidentId),
          ]);
          if (cancelled) return;
          setEvidence(ev);
          setHypotheses(hyp);
          setTimeline(tl);
          setRecommendations(rec);
        }

        if (ACTIVE_STATUSES.has(inc.status)) {
          timer = window.setTimeout(load, POLL_MS);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      }
    }

    setIncident(null);
    setEvidence([]);
    setHypotheses([]);
    setTimeline([]);
    setRecommendations([]);
    load();

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [incidentId]);

  const exceptions = useMemo(() => extractExceptions(evidence), [evidence]);
  const errorSignals = useMemo(() => extractErrorSignals(evidence), [evidence]);

  if (error) return <div className="detail-error">Failed to load incident: {error}</div>;
  if (!incident) return <div className="detail-loading">Loading...</div>;

  const hasGuidance = recommendations.length > 0 || incident.nextActions.length > 0;

  return (
    <div className="incident-detail">
      <div className="detail-header">
        <div>
          <h1>{incident.service}</h1>
          <div className="detail-sub">
            {incident.environment} · {incident.severity} · {incident.id}
          </div>
        </div>
        <span className={`badge badge-${incident.status.toLowerCase()}`}>{incident.status}</span>
      </div>

      <p className="detail-description">
        <span className="detail-description-label">Reported: </span>
        {incident.description}
      </p>

      {ACTIVE_STATUSES.has(incident.status) && (
        <div className="investigating-banner">
          <strong>Investigation in progress.</strong> Real agents are querying Prometheus,
          Loki and Jaeger for evidence, and a local AI model is reasoning over what they
          find. This usually takes a few minutes — this page updates itself, no need to
          refresh.
        </div>
      )}

      {incident.status === "FAILED" && incident.failureReason && (
        <div className="failed-banner">
          <strong>The investigation could not finish.</strong> {incident.failureReason}
        </div>
      )}

      {incident.status === "COMPLETED" && (
        <>
          {/* 1. THE HEADLINE: what happened, in plain English, up front */}
          <section className="rca-summary">
            <h2>What happened</h2>
            {incident.confidence != null && (
              <div className="confidence-bar">
                <div className="confidence-fill" style={{ width: `${incident.confidence * 100}%` }} />
                <span className="confidence-label">{Math.round(incident.confidence * 100)}% confidence</span>
              </div>
            )}
            <p className="probable-cause">{incident.probableRootCause}</p>
            {incident.summary && <p className="summary-text">{incident.summary}</p>}
            {incident.requiresHumanApproval && (
              <div className="approval-note">
                A person needs to review and approve before any fix is applied — this
                platform only ever recommends, it never changes anything on its own.
              </div>
            )}
          </section>

          {/* 2. THE EXACT ERROR: exception type/message/location, pulled from the raw
              logs so a developer doesn't have to go dig through a terminal for it. */}
          {exceptions.length > 0 && (
            <section>
              <h2>Error details</h2>
              {exceptions.map((exc) => (
                <div className="error-card" key={`${exc.exceptionType}-${exc.location}`}>
                  <div className="error-card-top">
                    <span className="error-type">{exc.exceptionType}</span>
                    {exc.location && <span className="error-location">{exc.location}</span>}
                  </div>
                  <p className="error-message">{exc.message}</p>
                  {exc.method && (
                    <p className="error-where">
                      Happened in <code>{exc.method}</code>
                    </p>
                  )}
                  <details className="raw-toggle">
                    <summary>Show full stack trace</summary>
                    <pre className="evidence-value">{exc.rawStackTrace}</pre>
                  </details>
                </div>
              ))}
            </section>
          )}

          {errorSignals.length > 0 && (
            <section>
              <h2>Error signals seen</h2>
              <div className="signal-list">
                {errorSignals.map((sig) => (
                  <div className="signal-card" key={`${sig.errorType}-${sig.httpStatus}`}>
                    <div className="signal-top">
                      <span className="signal-type">{sig.errorType}</span>
                      {sig.httpStatus && <span className="signal-status">HTTP {sig.httpStatus}</span>}
                      <span className="signal-count">×{sig.occurrences}</span>
                    </div>
                    {sig.reason && <p className="signal-reason">{sig.reason}</p>}
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* 3. WHO/WHAT IS AFFECTED */}
          {(incident.estimatedImpact || incident.impactServices.length > 0 || incident.impactBusinessFlows.length > 0) && (
            <section>
              <h2>Business impact</h2>
              <div className="impact-box">
                {incident.estimatedImpact && <p className="impact-estimate">{incident.estimatedImpact}</p>}
                {incident.impactBusinessFlows.length > 0 && (
                  <div className="impact-row">
                    <span className="impact-label">Business flows affected</span>
                    <div className="tag-list">
                      {incident.impactBusinessFlows.map((flow) => (
                        <span className="tag tag-flow" key={flow}>{flow}</span>
                      ))}
                    </div>
                  </div>
                )}
                {incident.impactServices.length > 0 && (
                  <div className="impact-row">
                    <span className="impact-label">Services affected</span>
                    <div className="tag-list">
                      {incident.impactServices.map((svc) => (
                        <span className="tag tag-service" key={svc}>{svc}</span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </section>
          )}

          {/* 4. WHY WE THINK THIS: ranked reasoning, plain English */}
          {hypotheses.length > 0 && (
            <section>
              <h2>Why we think this</h2>
              <p className="section-hint">
                Every possible explanation the AI considered, ranked by how well the
                evidence supports it — not just the top pick.
              </p>
              <ul className="hypothesis-list">
                {hypotheses.map((h) => (
                  <li key={h.id} className="hypothesis-item">
                    <div className="hypothesis-top">
                      <span className={`probability probability-${probabilityBucket(h.probability)}`}>
                        {h.probability}
                      </span>
                      <span>{h.description}</span>
                    </div>
                    <div className="hypothesis-evidence">{h.evidenceSummary}</div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* 5. WHAT TO DO: the whole point of the page, for whoever has to act */}
          {hasGuidance && (
            <section>
              <h2>What to do next</h2>
              {recommendations.length > 0 && (
                <>
                  <p className="section-hint">To fix the underlying problem:</p>
                  <ul className="recommendation-list">
                    {recommendations.map((r) => (
                      <li key={r.id}>{r.recommendation}</li>
                    ))}
                  </ul>
                </>
              )}
              {incident.nextActions.length > 0 && (
                <>
                  <p className="section-hint">To confirm and follow up:</p>
                  <ul className="recommendation-list recommendation-list-secondary">
                    {incident.nextActions.map((action) => (
                      <li key={action}>{action}</li>
                    ))}
                  </ul>
                </>
              )}
            </section>
          )}

          {incident.similarIncidents.length > 0 && (
            <section>
              <h2>Seen before?</h2>
              <ul className="timeline-list">
                {incident.similarIncidents.map((sim) => (
                  <li key={sim}>{sim}</li>
                ))}
              </ul>
            </section>
          )}

          {timeline.length > 0 && (
            <section>
              <h2>How it unfolded</h2>
              <ul className="event-timeline">
                {timeline.map((t) => (
                  <li key={t.id} className="event-timeline-item">
                    <span className="event-timeline-dot" />
                    <div>
                      <div className="timeline-ts">{t.timestamp}</div>
                      <div>{t.event}</div>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* 6. THE RECEIPTS: full technical detail, collapsed by default so the page
              reads clean, but one click away for anyone who wants to verify a claim. */}
          {evidence.length > 0 && (
            <section>
              <details className="raw-toggle raw-toggle-section">
                <summary>
                  <h2 className="inline-heading">Full evidence log ({evidence.length} items)</h2>
                </summary>
                <p className="section-hint">
                  Every raw data point the agents pulled from Prometheus, Loki, Jaeger and
                  the knowledge base — this is what every claim above is built from.
                </p>
                <ul className="evidence-list">
                  {evidence.map((e) => (
                    <li key={e.id} className="evidence-item">
                      <div className="evidence-top">
                        <span className="evidence-source">{sourceLabel(e.source)}</span>
                        <span className="evidence-type">{e.type}</span>
                        <span className={`evidence-confidence conf-${e.confidence.toLowerCase()}`}>{e.confidence}</span>
                      </div>
                      <div className="evidence-desc">{e.description}</div>
                      <pre className="evidence-value">{e.value}</pre>
                    </li>
                  ))}
                </ul>
              </details>
            </section>
          )}
        </>
      )}
    </div>
  );
}
