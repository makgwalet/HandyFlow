-- Restricted records and break-glass access (CLINIC-DEC-008, 009).
-- A restricted patient record (mental health, HIV, sexual/reproductive, staff, VIP, other) can be opened only by people
-- who hold standing access, or by an authorised clinician who breaks the glass: a typed reason, a time-limited session,
-- an audit trail of what was opened, and an alert the practice manager / compliance reviews.
-- Rows in these tables are never updated except to acknowledge a session or release a restriction, and never deleted.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_BREAK_GLASS_REVIEW', 'Review and acknowledge break-glass alerts (practice manager / compliance)')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS clinic_restricted_records (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    patient_id     UUID         NOT NULL REFERENCES clinic_patients(id),
    category       VARCHAR(30)  NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    flagged_by     UUID,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    released_at    TIMESTAMP,
    released_by    UUID,
    release_reason VARCHAR(500),
    CONSTRAINT chk_restricted_category CHECK (category IN ('MENTAL_HEALTH','HIV','SEXUAL_REPRODUCTIVE','STAFF','VIP','OTHER'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_restricted_active ON clinic_restricted_records (tenant_id, patient_id) WHERE released_at IS NULL;

CREATE TABLE IF NOT EXISTS clinic_break_glass_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    patient_id        UUID         NOT NULL REFERENCES clinic_patients(id),
    user_id           UUID         NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    started_at        TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP    NOT NULL,
    ip_address        VARCHAR(64),
    user_agent        VARCHAR(300),
    acknowledged_at   TIMESTAMP,
    acknowledged_by   UUID,
    acknowledged_note VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_break_glass_active ON clinic_break_glass_sessions (tenant_id, patient_id, user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_break_glass_review ON clinic_break_glass_sessions (tenant_id, acknowledged_at, started_at);

CREATE TABLE IF NOT EXISTS clinic_break_glass_audit (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    session_id    UUID         NOT NULL REFERENCES clinic_break_glass_sessions(id),
    patient_id    UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    event_type    VARCHAR(40)  NOT NULL,
    resource_type VARCHAR(30),
    resource_id   UUID,
    path          VARCHAR(300),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_break_glass_event CHECK (event_type IN
        ('BREAK_GLASS_OPENED','BREAK_GLASS_VIEWED','BREAK_GLASS_DOCUMENT_PRINTED','BREAK_GLASS_DOCUMENT_EXPORTED'))
);
CREATE INDEX IF NOT EXISTS idx_break_glass_audit_session ON clinic_break_glass_audit (tenant_id, session_id, created_at);
