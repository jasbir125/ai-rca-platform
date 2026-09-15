-- RcaReport (agent-framework) already generates a business-impact block (affected
-- services, business flows, an estimated-impact statement), similar-incident
-- references, and suggested next actions -- but InvestigationRunner never persisted
-- them, so this data was silently discarded after every investigation. Adding it here
-- so the API/UI can surface it, which matters most for business-facing incidents
-- ("what does this cost, who is affected") rather than purely technical ones.
--
-- Lists are stored as newline-joined TEXT (consistent with agent_executions.tools_used
-- using comma-joined TEXT for its own simple string list) rather than child tables,
-- since these are plain strings with no per-item structure (unlike hypotheses, which
-- have description/probability/evidence and already have their own table).
ALTER TABLE incidents
    ADD COLUMN impact_services        TEXT,
    ADD COLUMN impact_business_flows  TEXT,
    ADD COLUMN estimated_impact       TEXT,
    ADD COLUMN similar_incidents      TEXT,
    ADD COLUMN next_actions           TEXT;
