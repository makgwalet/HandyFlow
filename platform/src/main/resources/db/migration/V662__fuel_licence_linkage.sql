-- Fuel F01: licence linkage. Additive only. A compliance item may now be tied to a legal entity, a licensed activity and an
-- issuing authority, and may carry evidence references. Nothing here decides which licences a business needs: that is the
-- company's own regulatory judgement, so no rule or list of required licences is stored.

ALTER TABLE fuel_compliance_items ADD COLUMN IF NOT EXISTS legal_entity_id UUID;
ALTER TABLE fuel_compliance_items ADD COLUMN IF NOT EXISTS licensed_activity VARCHAR(16);
ALTER TABLE fuel_compliance_items ADD COLUMN IF NOT EXISTS issuing_authority VARCHAR(120);
ALTER TABLE fuel_compliance_items DROP CONSTRAINT IF EXISTS chk_fuel_compliance_activity;
ALTER TABLE fuel_compliance_items ADD CONSTRAINT chk_fuel_compliance_activity
    CHECK (licensed_activity IS NULL OR licensed_activity IN ('WHOLESALE','RETAIL','DISTRIBUTION','STORAGE','TRANSPORT','OTHER'));
CREATE INDEX IF NOT EXISTS idx_fuel_compliance_entity ON fuel_compliance_items (tenant_id, legal_entity_id) WHERE legal_entity_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS fuel_compliance_evidence (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    item_id           UUID NOT NULL,
    file_ref          VARCHAR(500) NOT NULL,
    note              VARCHAR(300),
    captured_by       UUID,
    captured_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    status            VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    superseded_reason VARCHAR(300),
    superseded_by     UUID,
    superseded_at     TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_comp_evidence_file CHECK (length(trim(file_ref)) > 0),
    CONSTRAINT chk_fuel_comp_evidence_status CHECK (status IN ('ACTIVE','SUPERSEDED')),
    CONSTRAINT chk_fuel_comp_evidence_superseded CHECK (status <> 'SUPERSEDED' OR (superseded_reason IS NOT NULL AND superseded_by IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_fuel_comp_evidence_item ON fuel_compliance_evidence (tenant_id, item_id);
