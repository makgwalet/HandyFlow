-- V211__add_cp_evidence_and_armoury_detail_link.sql
-- (Rename to your actual next migration number before applying.)

-- ── Evidence upload (Part 9 gap) ─────────────────────────────────────────────
-- Polymorphic attachment table, same entityType/entityId convention already
-- used by security_audit_log -- lets evidence attach to either a Principal
-- (ID documents, standing threat intel) or a ProtectionDetail (signed
-- engagement letter for this specific booking) without two parallel tables.
CREATE TABLE security_cp_evidence (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    entity_type       VARCHAR(30) NOT NULL,   -- PRINCIPAL | PROTECTION_DETAIL
    entity_id         UUID NOT NULL,
    category          VARCHAR(30) NOT NULL,   -- ID_DOCUMENT | ENGAGEMENT_LETTER | THREAT_INTEL | MEDICAL | OTHER
    file_url          VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255),
    notes             VARCHAR(1000),
    uploaded_by       UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    delete_reason     VARCHAR(500)
);

CREATE INDEX idx_cp_evidence_entity
    ON security_cp_evidence (tenant_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE security_cp_evidence IS
    'Evidentiary documents for Close Protection: ID scans, signed engagement letters, threat intel files. Soft-delete only (deleted_at) -- evidentiary records are not hard-deleted, same rationale as ArmouryLog being append-only.';

-- ── Arms <-> CP detail linkage ────────────────────────────────────────────────
-- Nullable -- most armoury issue/return events have nothing to do with a CP
-- detail (routine site guarding). Set only when a firearm is issued as part
-- of a protection detail's team roster, via CloseProtectionService.
ALTER TABLE security_armoury_logs
    ADD COLUMN protection_detail_id UUID;

CREATE INDEX idx_armoury_logs_protection_detail
    ON security_armoury_logs (protection_detail_id)
    WHERE protection_detail_id IS NOT NULL;

COMMENT ON COLUMN security_armoury_logs.protection_detail_id IS
    'Set when this issue/return happened as part of a CP detail team roster (CloseProtectionService.issueFirearmForDetail). Null for routine site-guarding issuance -- lets GET /cp/details/{id}/armoury show exactly which firearms are out on a given engagement.';