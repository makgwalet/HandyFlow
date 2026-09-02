-- V267__insurance_internal_module.sql
--
-- UNVERIFIED VERSION NUMBER: this session's GitHub sync excludes
-- /platform/src/main/resources/, so no real migration file — including
-- Legal Practice's own V266 — was ever directly readable this engagement.
-- V267 is an assumed-next-sequential placeholder (V266 = Legal Practice,
-- per that module's own status doc). CONFIRM against the actual latest
-- Flyway version in your checkout before applying, and rename the file if
-- it collides with something already added since.
--
-- Creates the internal Insurance module's own table. No accompanying
-- module_catalogue / permission INSERTs are included below with real
-- confidence — see the note at the bottom of this file for why, and what
-- to check before you apply this.

CREATE TABLE ins_policies (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    policy_number               VARCHAR(100) NOT NULL,
    insurer_name                VARCHAR(255) NOT NULL,
    line_of_business            VARCHAR(20) NOT NULL,   -- MOTOR | PROPERTY | EQUIPMENT | LIABILITY | OTHER
    asset_type                  VARCHAR(20),             -- VEHICLE | PROPERTY | EQUIPMENT | OTHER
    asset_reference             VARCHAR(500),
    sum_insured                 NUMERIC(15,2),
    premium_amount              NUMERIC(12,2) NOT NULL,
    premium_frequency           VARCHAR(15) NOT NULL,    -- MONTHLY | QUARTERLY | ANNUAL
    excess_amount                NUMERIC(12,2),
    broker_or_insurer_contact   VARCHAR(255),
    start_date                  DATE NOT NULL,
    expiry_date                 DATE NOT NULL,
    status                       VARCHAR(20) NOT NULL,    -- ACTIVE | LAPSED | CANCELLED | EXPIRED | RENEWED
    renewal_of_policy_id        UUID,
    cancelled_date               DATE,
    cancel_reason                VARCHAR(500),
    expiry_reminder_sent_at     TIMESTAMPTZ,
    notes                        TEXT,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    version                      BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ins_policies_renewal_of
        FOREIGN KEY (renewal_of_policy_id) REFERENCES ins_policies (id)
);

CREATE INDEX idx_ins_policies_tenant ON ins_policies (tenant_id);
CREATE INDEX idx_ins_policies_tenant_status ON ins_policies (tenant_id, status);
CREATE INDEX idx_ins_policies_tenant_expiry ON ins_policies (tenant_id, expiry_date);
CREATE INDEX idx_ins_policies_renewal_of ON ins_policies (renewal_of_policy_id);

-- ─────────────────────────────────────────────────────────────────────────
-- module_catalogue / permission seeding — NOT INCLUDED, DELIBERATELY.
--
-- Every prior module's own migration (which registers it in whatever
-- table drives the module marketplace / activation UI, and whatever table
-- seeds the INSURANCE_READ / INSURANCE_MANAGE / INSURANCE_ADMIN
-- authorities) lives in the excluded resources/ path, so this session has
-- never had a single real example of that INSERT's exact table/column
-- names to copy from — for every module built this engagement, not only
-- this one. Rather than guess a schema I cannot verify for a table that
-- likely also drives real billing (module price, trial flags, etc.),
-- add this module's own row(s) by copying the exact shape of whatever
-- migration registered Legal Practice (V266) or Agriculture, adjusted for:
--   moduleKey   = 'insurance'
--   name        = 'Insurance' (or 'Insurance (Internal)' if the catalogue
--                 needs to visually distinguish it from the future
--                 'insurancebrokerage' provider module)
--   permissions = INSURANCE_READ, INSURANCE_MANAGE, INSURANCE_ADMIN
-- This is the same class of gap flagged on every prior module's own
-- migration in this engagement — not a new omission specific to this one.
