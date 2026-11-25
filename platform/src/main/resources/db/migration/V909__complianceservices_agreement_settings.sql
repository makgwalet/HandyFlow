-- Practice-set check on activating agreements: when switched on, the person who drafted an agreement cannot be the one who activates it
-- (optionally only from a quoted total the practice names). Off until an administrator switches it on; no amount is seeded.
CREATE TABLE IF NOT EXISTS compliance_agreement_settings (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    require_second_person BOOLEAN NOT NULL DEFAULT FALSE,
    min_value             NUMERIC(14,2),
    updated_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_agreement_settings_min CHECK (min_value IS NULL OR min_value > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_agreement_settings ON compliance_agreement_settings(tenant_id);
