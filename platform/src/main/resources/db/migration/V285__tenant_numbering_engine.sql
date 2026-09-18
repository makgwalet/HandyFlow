-- src/main/resources/db/migration/V285__tenant_numbering_engine.sql
--
-- Tenant Numbering Engine, phase 1.
--
-- PROBLEM THIS FIXES:
-- Every document number today is generic and module-scoped, not tenant-
-- scoped: InvoiceNumberGenerator, BkNumberGenerator and FmNumberGenerator
-- each independently produce "INV-00001" from their own TenantSequenceService
-- counter (INVOICE / INVOICE_SEQUENCE names differ, so the counters don't
-- collide internally) but the *visible* number is identical in shape. A
-- tenant running invoicing + bookkeeping + facilities-management at once can
-- have three unrelated documents all rendered as "INV-00003" with nothing on
-- the page to tell them apart. This is a real, current defect, not a
-- hypothetical one.
--
-- FIX: give each tenant a short, stable, human-chosen document_code (e.g.
-- "FPS" for FastPrint Solutions), and let every document number be built as
-- {tenantCode}-{typeCode}-{SEQ}. This makes every number tenant-identifiable
-- (useful on a shared inbox, a bank statement reference, a filed document)
-- and, by giving each document TYPE a distinct typeCode (see
-- TenantNumberingEngine.DEFAULT_TYPE_CODES), removes the human-visible
-- collision above even where the underlying counters already differed.
--
-- WHY NOT touch tenant_number_sequences?
-- That table's (tenant_id, sequence_name) counters are untouched and keep
-- incrementing exactly as before — this migration only adds tenant identity
-- and formatting on top. No existing sequence resets, no renumbering, no
-- gap. Already-issued document numbers are never rewritten.

ALTER TABLE tenants
    ADD COLUMN document_code VARCHAR(8);

-- Not UNIQUE NOT NULL yet: 465+ existing tenants have no code. Backfill
-- derives a candidate from the tenant name/slug; uniqueness is enforced by
-- TenantNumberingEngine at issue-time (it appends a numeric suffix on
-- collision) rather than by a blocking migration-time constraint, since a
-- backfill collision must never fail startup.
COMMENT ON COLUMN tenants.document_code IS
    'Short, stable, tenant-chosen code used as the tenant segment of every '
    'generated document number (e.g. "FPS-INV-2026-000001"). Set at '
    'onboarding or in Settings; immutable once any document has been '
    'numbered with it (see TenantNumberingEngine).';

-- Best-effort backfill: derive an uppercase 2-4 letter code from existing
-- tenant name/slug so numbering can switch over without an onboarding step
-- for tenants that already exist. Collisions are resolved lazily by the
-- engine on first use (see TenantNumberingEngine.resolveDocumentCode),
-- exactly like a brand-new tenant with no code yet would be.
UPDATE tenants
SET document_code = UPPER(
    LEFT(
        REGEXP_REPLACE(COALESCE(NULLIF(TRIM(name), ''), slug), '[^a-zA-Z0-9]', '', 'g'),
        4
    )
)
WHERE document_code IS NULL;

CREATE TABLE tenant_numbering_config (
    tenant_id       UUID         NOT NULL,
    -- document_type is the same string already passed as `sequenceName`
    -- into TenantSequenceService.nextValue(...) today (e.g. "INVOICE",
    -- "QUOTE", "CREDIT_NOTE") — reusing it means this table can be adopted
    -- module-by-module with zero changes to existing counters.
    document_type   VARCHAR(50)  NOT NULL,
    type_code       VARCHAR(10),   -- override of the built-in default (see DEFAULT_TYPE_CODES); NULL = use default
    prefix_override VARCHAR(20),   -- override of {tenantCode}-{typeCode}; NULL = use tenant.document_code + type_code
    padding         SMALLINT     NOT NULL DEFAULT 6,
    include_year    BOOLEAN      NOT NULL DEFAULT true,
    reset_policy    VARCHAR(10)  NOT NULL DEFAULT 'NEVER', -- NEVER | YEARLY — YEARLY is a documented future extension, not wired up yet (see engine Javadoc)
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_numbering_config PRIMARY KEY (tenant_id, document_type),
    CONSTRAINT fk_tenant_numbering_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT chk_tenant_numbering_config_reset_policy CHECK (reset_policy IN ('NEVER', 'YEARLY')),
    CONSTRAINT chk_tenant_numbering_config_padding CHECK (padding BETWEEN 1 AND 12)
);

COMMENT ON TABLE tenant_numbering_config IS
    'Per-tenant, per-document-type overrides for TenantNumberingEngine. A '
    'missing row for (tenant_id, document_type) is not an error — the '
    'engine falls back to tenant.document_code + the built-in default type '
    'code for that document_type.';
