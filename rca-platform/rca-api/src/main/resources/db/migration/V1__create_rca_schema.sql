CREATE TABLE incidents (
    id                       UUID PRIMARY KEY,
    service                  VARCHAR(255)   NOT NULL,
    environment              VARCHAR(50)    NOT NULL,
    severity                 VARCHAR(20)    NOT NULL,
    description              TEXT           NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    window_start             TIMESTAMPTZ    NOT NULL,
    window_end               TIMESTAMPTZ    NOT NULL,
    summary                  TEXT,
    probable_root_cause      TEXT,
    confidence               DOUBLE PRECISION,
    requires_human_approval  BOOLEAN,
    failure_reason           TEXT,
    correlation_id           VARCHAR(64)    NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_incidents_status ON incidents (status);
CREATE INDEX idx_incidents_service ON incidents (service);
CREATE INDEX idx_incidents_created_at ON incidents (created_at);

CREATE TABLE incident_evidence (
    id             UUID PRIMARY KEY,
    incident_id    UUID           NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    source         VARCHAR(50)    NOT NULL,
    service        VARCHAR(255),
    type           VARCHAR(30)    NOT NULL,
    description    TEXT           NOT NULL,
    value          TEXT,
    confidence     VARCHAR(20)    NOT NULL,
    correlation_id VARCHAR(64),
    reference      VARCHAR(255),
    captured_at    TIMESTAMPTZ    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_incident_evidence_incident_id ON incident_evidence (incident_id);

CREATE TABLE incident_hypotheses (
    id                UUID PRIMARY KEY,
    incident_id       UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    description       TEXT        NOT NULL,
    probability       VARCHAR(20) NOT NULL,
    evidence_summary  TEXT,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_incident_hypotheses_incident_id ON incident_hypotheses (incident_id);

CREATE TABLE incident_timeline (
    id               UUID PRIMARY KEY,
    incident_id      UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    event_timestamp  VARCHAR(64) NOT NULL,
    event            TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_incident_timeline_incident_id ON incident_timeline (incident_id);

CREATE TABLE incident_recommendations (
    id             UUID PRIMARY KEY,
    incident_id    UUID NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    recommendation TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_incident_recommendations_incident_id ON incident_recommendations (incident_id);

CREATE TABLE agent_executions (
    id             UUID PRIMARY KEY,
    incident_id    UUID           NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    agent_name     VARCHAR(50)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    duration_ms    BIGINT         NOT NULL,
    tools_used     TEXT,
    summary        TEXT,
    error_message  TEXT,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_agent_executions_incident_id ON agent_executions (incident_id);
