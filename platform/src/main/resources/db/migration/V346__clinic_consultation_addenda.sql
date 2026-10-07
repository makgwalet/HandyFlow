-- Clinic Sprint 1 (S1-1): append-only addenda on signed or locked consultations.
CREATE TABLE clinic_consultation_addenda (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    consultation_id UUID        NOT NULL REFERENCES clinic_consultations(id),
    author_user_id  UUID        REFERENCES users(id),
    text            TEXT        NOT NULL CHECK (length(btrim(text)) > 0),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_clinic_addenda_consultation ON clinic_consultation_addenda (consultation_id, created_at);
