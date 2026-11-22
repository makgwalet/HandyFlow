-- Who receives a site's monthly service report. Added by a manager, never taken from guard or visitor data.
CREATE TABLE security_report_recipients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    site_id     UUID NOT NULL,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(254) NOT NULL CHECK (email = lower(email) AND email ~ '^[^[:space:]@<>,;]+@[^[:space:]@<>,;.]+(\.[^[:space:]@<>,;.]+)+$'),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at  TIMESTAMPTZ,
    removed_by  UUID
);
CREATE UNIQUE INDEX uq_report_recipient_site_email ON security_report_recipients (tenant_id, site_id, email) WHERE active;
CREATE INDEX idx_report_recipients_site ON security_report_recipients (tenant_id, site_id) WHERE active;
