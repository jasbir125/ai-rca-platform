# Web UI Guide

`ui/` is a React 18 + Vite + TypeScript single-page app that covers the "browse
incidents, search by typing, read the RCA" flow described in `plan.md`'s backlog. It is
a real client of `rca-api`'s existing REST API (`docs/api-documentation.md`) — no new
backend logic was added beyond CORS support (see below).

## What it covers vs. the original spec

The spec (`master_prompt.txt`) calls for a 6-screen UI (dashboard, incident list,
incident detail, agent activity view, knowledge base browser, settings/config). This
build is **one screen** — a list/detail split view — covering exactly what was asked
for: find an incident by typing, and read its RCA. Deferred, per the MVP-first
prioritization the rest of this project follows (see `plan.md`):

- Dedicated agent-activity view (`GET /api/agents/status` is already called nowhere in
  the UI — it exists on the backend, unused here)
- Knowledge base browser/upload screen
- Settings/config screen
- A dashboard/overview screen distinct from the incident list
- Auth/RBAC around the UI itself (matches the backend, which also has none yet)

## Screens

**Incident list** (left panel) — every incident from `GET /api/incidents`, polled every
8s, sorted newest-first. A search box filters client-side across service, environment,
severity, description, status, summary, probable root cause, and ID — so typing
`payment` or `timeout` or a status name narrows the list live. No server-side search
endpoint exists or was needed at this data volume.

**Incident detail** (right panel) — `GET /api/incidents/{id}` plus, once `COMPLETED`,
`.../evidence`, `.../hypotheses`, `.../timeline`, `.../recommendations`. Polls every 5s
while `PENDING`/`INVESTIGATING`. Renders the confidence bar, probable root cause,
ranked hypotheses with their evidence summaries, recommendations, timeline, and every
raw evidence item (source/type/confidence/value) it was built from — so a claim in the
RCA can be traced back to the actual Prometheus/Loki/Jaeger data, matching the
platform's evidence-first design principle.

**New Incident** (modal) — a form (service/environment/severity/description) that calls
`POST /api/incidents` then immediately `POST /api/incidents/{id}/investigate`, selecting
the new incident so its detail view starts polling right away.

## Files

```
ui/
  src/
    api.ts                        typed fetch client (VITE_API_BASE_URL, default localhost:8090)
    types.ts                      TS types mirroring rca-api's response records
    App.tsx                       top-level layout + incident-list polling
    components/IncidentList.tsx   search + list
    components/IncidentDetail.tsx detail view + polling
    components/NewIncidentForm.tsx create-incident modal
    styles.css                    dark theme, no UI framework/component library
```

No router, no state-management library, no component library — the app is one view
with a list/detail split, which doesn't need any of those; would reconsider if the
deferred screens above get built.

## Backend change required: CORS

`rca-api` had no CORS configuration (nothing needed it before — only `curl`/tests called
it). A browser-based UI on a different origin (`localhost:5173` in dev) needs it.
Added `com.airca.rcaapi.config.WebConfig` (`WebMvcConfigurer.addCorsMappings`), allowing
`GET/POST/PUT/DELETE/OPTIONS` on `/api/**` from an externalized origin list —
`cors.allowed-origins` in `application.yml`, sourced from `CORS_ALLOWED_ORIGINS` (default
`http://localhost:5173`), same externalized-config pattern as every other integration
point in this app. Set in `docker-compose.yml` for the containerized `rca-api` too.

Verified live (not just "should work"): a real `OPTIONS` preflight against the
containerized `rca-api` with `Origin: http://localhost:5173` returns
`Access-Control-Allow-Origin: http://localhost:5173`, and a real `GET /api/incidents`
with that origin header succeeds.

## Running it

See `docs/local-setup.md` step 5. Short version: `cd ui && npm install && npm run dev`,
open http://localhost:5173, `rca-api` must already be running (Docker or local).

`npm run build` (`tsc -b && vite build`) is the production build — confirmed to compile
clean with no TypeScript errors. Not yet wired into `docker-compose.yml` or the Helm
chart as a served static asset / container (backlog — trivial to add, e.g. an nginx
image serving `ui/dist`, once the UI's scope is more final).

## Known limitation found while building this (backend bug, not a UI bug)

While exercising the real incident list through the UI, one pre-existing incident
(`43515377-24af-43e2-9dd6-306612188f15`, created 2026-09-09) was found stuck showing
`status: INVESTIGATING` despite having a populated `failureReason`
(`"LLM reasoning step failed: ... request timed out"`) — i.e. the investigation actually
failed (during one of the host-contention incidents documented in `plan.md`'s Phase 7
entries), but `InvestigationRunner` never transitioned `status` to `FAILED` in that code
path, leaving it permanently showing "investigation in progress" in any client,
including this UI. **Not fixed yet** — flagged to the user, decision pending on whether
to fix the status-transition bug now or track it as backlog. If reproducing: an
`investigate()` call whose LLM call throws/times out needs to be traced through
`InvestigationRunner`/`InvestigationOrchestrator` to find where a thrown exception sets
`failureReason` without also setting `status = FAILED`.
