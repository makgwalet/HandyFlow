-- Incidents (breakdowns, accidents, theft, fire, etc.) reported against
-- equipment. Previously this existed only as in-memory React state on the
-- frontend (IncidentsTab.tsx) — every incident was lost on page refresh.
CREATE TABLE earthmoving_incidents (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    asset_id            UUID        NOT NULL,
    type                VARCHAR(30) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    operator_name       VARCHAR(200),
    site_name           VARCHAR(255),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reported_at         TIMESTAMP   NOT NULL DEFAULT now(),
    reported_by_user_id UUID,
    resolved_at         TIMESTAMP,
    resolved_by_user_id UUID,
    resolution_notes    TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_earthmoving_incidents PRIMARY KEY (id),
    CONSTRAINT fk_incident_asset FOREIGN KEY (asset_id) REFERENCES earthmoving_assets(id),
    CONSTRAINT chk_incident_type CHECK (
        type IN ('BREAKDOWN','ACCIDENT','THEFT','FIRE','ROLLOVER','NEAR_MISS','FUEL_SPILL','OTHER')
    ),
    CONSTRAINT chk_incident_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN','RESOLVED'))
);

CREATE INDEX idx_em_incidents_tenant_status   ON earthmoving_incidents (tenant_id, status, reported_at DESC);
CREATE INDEX idx_em_incidents_tenant_severity ON earthmoving_incidents (tenant_id, severity, reported_at DESC);
CREATE INDEX idx_em_incidents_asset           ON earthmoving_incidents (asset_id, reported_at DESC);