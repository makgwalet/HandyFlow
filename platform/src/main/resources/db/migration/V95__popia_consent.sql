-- =============================================================================
-- V95__popia_consent.sql
-- POPIA consent tracking and retention policy
-- =============================================================================
-- WHY this table?
-- POPIA Section 11 requires lawful grounds for processing personal data.
-- The most common ground is consent.  We record:
--   1. WHEN the customer gave consent (or was imported under a lawful basis)
--   2. WHAT they consented to (marketing, service delivery, etc.)
--   3. IF and WHEN they withdrew consent
--
-- POPIA Section 14 requires data not be kept longer than necessary.
-- retention_expires_at records when the record should be reviewed or deleted.
--
-- WHY separate table and not columns on customers?
-- Consent must be independently auditable.  A separate table lets consent
-- records outlive a soft-deleted customer so you can prove lawful processing
-- for the full period data was held.
--
-- WHY no updated_at trigger?
-- This project uses Hibernate @PreUpdate to manage updated_at — no DB trigger
-- is defined.  Consistent with customers, contacts, and all other tables.
-- =============================================================================

CREATE TABLE customer_consent (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    customer_id          UUID        NOT NULL,

    -- POPIA Section 11 lawful basis:
    -- CONSENT, LEGITIMATE_INTEREST, CONTRACT, LEGAL_OBLIGATION,
    -- VITAL_INTEREST, PUBLIC_INTEREST
    lawful_basis         VARCHAR(50) NOT NULL DEFAULT 'CONSENT',

    -- Processing activities covered (e.g. SERVICE_DELIVERY, MARKETING)
    purposes             TEXT[]      NOT NULL DEFAULT '{}',

    -- When consent was given (may be in the past for imported legacy data)
    consented_at         TIMESTAMP   NOT NULL DEFAULT now(),

    -- Source: WEB_FORM, IMPORT, PHONE, IN_PERSON, EMAIL
    consent_source       VARCHAR(50) NOT NULL DEFAULT 'IMPORT',

    -- Free text: "Signed service agreement 2024-01-15"
    consent_evidence     TEXT,

    -- Withdrawal (null = still active)
    withdrawn_at         TIMESTAMP,
    withdrawal_reason    TEXT,

    -- Retention policy: when data should be reviewed or purged
    -- Set by CustomerRetentionScheduler. Null = not yet calculated.
    retention_expires_at TIMESTAMP,

    -- Last human review of this record
    last_reviewed_at     TIMESTAMP,
    reviewed_by          UUID,

    -- Audit (updated_at managed by Hibernate @PreUpdate — no DB trigger)
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now(),
    version              BIGINT      NOT NULL DEFAULT 0
);

-- One active consent record per customer per tenant
CREATE UNIQUE INDEX idx_consent_active
    ON customer_consent (tenant_id, customer_id)
    WHERE withdrawn_at IS NULL;

-- Retention review query index
CREATE INDEX idx_consent_retention
    ON customer_consent (tenant_id, retention_expires_at)
    WHERE withdrawn_at IS NULL AND retention_expires_at IS NOT NULL;

COMMENT ON TABLE customer_consent IS
    'POPIA Section 11 consent records. One active record per customer per tenant. '
    'Records persist after customer soft-deletion for audit purposes.';