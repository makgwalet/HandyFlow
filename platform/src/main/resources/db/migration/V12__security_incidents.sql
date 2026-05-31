CREATE TABLE security_incidents (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    shift_id        UUID,
    guard_id        UUID        NOT NULL,
    type            VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'LOW',
    description     TEXT        NOT NULL,
    occurred_at     TIMESTAMP   NOT NULL,
    resolved_at     TIMESTAMP,
    photo_urls      JSONB,      -- array of photo URLs
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_security_incidents PRIMARY KEY (id),
    CONSTRAINT fk_incident_site
        FOREIGN KEY (site_id)  REFERENCES security_sites(id),
    CONSTRAINT fk_incident_guard
        FOREIGN KEY (guard_id) REFERENCES security_guards(id),
    CONSTRAINT fk_incident_shift
        FOREIGN KEY (shift_id) REFERENCES security_shifts(id) ON DELETE SET NULL,
    CONSTRAINT chk_incident_type CHECK (
        type IN ('THEFT','TRESPASS','MEDICAL','FIRE','VANDALISM','ASSAULT','SUSPICIOUS','OTHER')
    ),
    CONSTRAINT chk_incident_severity CHECK (
        severity IN ('LOW','MEDIUM','HIGH','CRITICAL')
    )
);

CREATE INDEX idx_incidents_tenant ON security_incidents(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_site   ON security_incidents(site_id)   WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_open   ON security_incidents(tenant_id, severity)
    WHERE resolved_at IS NULL AND deleted_at IS NULL;