-- Reusable letter templates (patch 0164): sick note, referral, prescription letter and general letter presets.
-- The text may hold merge fields such as {{patient.name}}; they are filled in when a template is applied to a visit.
-- A template is archived, never deleted, so letters already issued keep their meaning.

CREATE TABLE IF NOT EXISTS clinic_letter_templates (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    kind         VARCHAR(20)  NOT NULL CHECK (kind IN ('SICK_NOTE','REFERRAL','PRESCRIPTION_LETTER','GENERAL_LETTER')),
    name         VARCHAR(100) NOT NULL,
    title        VARCHAR(200),
    body         VARCHAR(4000),
    specialty    VARCHAR(100),
    urgency      VARCHAR(12)  CHECK (urgency IS NULL OR urgency IN ('ROUTINE','SEMI_URGENT','URGENT')),
    unfit_days   SMALLINT     CHECK (unfit_days IS NULL OR unfit_days BETWEEN 1 AND 365),
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    archived_at  TIMESTAMP,
    CHECK (body IS NOT NULL OR title IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_letter_templates_name
    ON clinic_letter_templates (tenant_id, kind, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_clinic_letter_templates_kind ON clinic_letter_templates (tenant_id, kind);
