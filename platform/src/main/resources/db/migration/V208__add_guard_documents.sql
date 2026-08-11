-- V219__add_guard_documents.sql
-- (Rename to your actual next migration number before applying.)
--
-- Guard File document storage -- PSIRA/POPIA compliance documents attached
-- to a guard's record (ID copy, PSIRA certificate, proof of address, bank
-- confirmation, training certificates, etc). Deliberately mirrors
-- CpEvidence's exact shape (V211, Close Protection module) rather than
-- inventing a new pattern -- same polymorphic-adjacent design question
-- already solved there: soft-delete only (compliance documents are not
-- hard-deleted), category enum, dev-mode base64 upload handling.

CREATE TABLE security_guard_documents (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    guard_id          UUID NOT NULL,
    category          VARCHAR(40) NOT NULL,
    file_url          VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255),
    notes             VARCHAR(1000),
    uploaded_by       UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    delete_reason     VARCHAR(500)
);

CREATE INDEX idx_guard_documents_guard
    ON security_guard_documents (tenant_id, guard_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE security_guard_documents IS
    'Guard File compliance documents (ID copy, PSIRA certificate, proof of address, bank confirmation, training/firearm/medical certificates, fingerprint form, employment contract, POPIA consent, etc). Soft-delete only -- same rationale as CpEvidence: these are compliance records, not disposable attachments.';