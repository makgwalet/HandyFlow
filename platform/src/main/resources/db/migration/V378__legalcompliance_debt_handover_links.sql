-- RENAME to the next free Flyway version (after the debtcollection legal-handover migration).
-- Same base-column ASSUMPTION as the debtcollection migrations (id, tenant_id, created_at, updated_at, version).

CREATE TABLE legalcompliance_debt_handover_links (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    handover_id UUID        NOT NULL,
    matter_id   UUID        NOT NULL REFERENCES legalcompliance_litigation_matters (id)
);
-- Idempotency: one matter per handover; also settles a race between two concurrent transfers.
CREATE UNIQUE INDEX uq_lc_debt_handover_links ON legalcompliance_debt_handover_links (tenant_id, handover_id);
CREATE INDEX ix_lc_debt_handover_links_matter ON legalcompliance_debt_handover_links (matter_id);
