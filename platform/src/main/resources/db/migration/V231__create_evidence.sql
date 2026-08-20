-- V<NEXT>__create_evidence.sql
--
-- Stage 0 of the Financial Control & Assurance adoption plan — the
-- shared Evidence Layer. Deliberately generic: any module can attach a
-- document to any entity it owns, without inventing its own storage
-- table each time (the exact duplication AccFicaDocument/TaskAttachment/
-- RecAgencyCandidate's cvStorageKey each did independently).
--
-- Uses FileStorageService (confirmed real, provider-agnostic port) for
-- the actual bytes — this table only ever stores the opaque storage_key
-- it returns, never file content directly. Matches TasksService/
-- RecruitmentAgencyService's proven pattern, not AccFicaDocument's
-- file_content_base64 workaround.
CREATE TABLE evidence (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,

    -- File identity — mirrors TaskAttachment's column shape exactly.
    file_name           VARCHAR(300) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,

    -- What this evidence IS and what it's attached to. evidence_type is
    -- free text at the DB layer (RECEIPT, FICA_DOCUMENT, BANK_STATEMENT,
    -- CONTRACT, PURCHASE_ORDER, OTHER, ...) — same "DB-constrained, not
    -- app-enum-validated" choice AccFicaDocument already made for
    -- doc_type, so a new module can introduce a new evidence_type
    -- without an application-layer enum change.
    evidence_type       VARCHAR(50)  NOT NULL,
    source_module       VARCHAR(50)  NOT NULL,
    related_entity_type VARCHAR(100) NOT NULL,
    related_entity_id   UUID NOT NULL,

    -- Nullable — not every piece of evidence ties to an accounting
    -- period (a Recruitment Agency CV doesn't; an Expenses receipt
    -- eventually will, once Stage 1 links evidence to periods for
    -- close/reconciliation purposes).
    period_id           UUID,

    -- Immutability groundwork per the adoption plan — cheap now,
    -- expensive to retrofit once real evidence exists. Not enforced
    -- anywhere yet in Stage 0; just captured.
    file_hash           VARCHAR(64) NOT NULL,
    version             INT NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    uploaded_by         UUID NOT NULL,
    uploaded_by_name    VARCHAR(255),

    -- Nullable, unused by any endpoint in Stage 0 — reserved for the
    -- review workflow Stage 1's Control Layer will need. Adding these
    -- columns now avoids a second migration later just to add two
    -- nullable fields.
    reviewed_by         UUID,
    reviewed_at         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The query every module's "show me the evidence for this record" list
-- view will run.
CREATE INDEX idx_evidence_related ON evidence (tenant_id, source_module, related_entity_type, related_entity_id);
CREATE INDEX idx_evidence_period  ON evidence (tenant_id, period_id) WHERE period_id IS NOT NULL;