-- Training Provider 0169: authority and accreditation framework.
-- Builds on V911: authorities, accreditations, scopes (programme at site) and the report obligations already exist.
-- Adds SAQA and DHET as authority types, the dates and source an accreditation record should carry, whether a record is current,
-- transitional or historical, and a list of the evidence behind it. No regulatory content is seeded: the provider enters what its
-- authorities gave it. Nothing is deleted.

ALTER TABLE trainprov_authorities DROP CONSTRAINT trainprov_authorities_authority_type_check;
ALTER TABLE trainprov_authorities ADD CONSTRAINT trainprov_authorities_authority_type_check
    CHECK (authority_type IN ('QCTO', 'SETA', 'SAQA', 'DHET', 'UMALUSI', 'CHE', 'PROFESSIONAL_BODY', 'OTHER'));

ALTER TABLE trainprov_accreditations
    ADD COLUMN effective_from       DATE,
    ADD COLUMN review_due_on        DATE,
    ADD COLUMN currency             VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    ADD COLUMN currency_note        TEXT,
    ADD COLUMN ended_on             DATE,
    ADD COLUMN superseded_by_id     UUID REFERENCES trainprov_accreditations(id),
    ADD COLUMN source_type          VARCHAR(30),
    ADD COLUMN source_reference     VARCHAR(300),
    ADD COLUMN source_version_date  DATE,
    ADD COLUMN source_location      VARCHAR(200);

-- Every existing row is CURRENT with nothing else set, so these hold for existing data.
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_currency CHECK (
    currency IN ('CURRENT', 'TRANSITIONAL', 'HISTORICAL')
    AND ((currency = 'CURRENT' AND ended_on IS NULL AND superseded_by_id IS NULL)
         OR (currency = 'TRANSITIONAL' AND COALESCE(char_length(btrim(currency_note)), 0) >= 10 AND ended_on IS NULL AND superseded_by_id IS NULL)
         OR (currency = 'HISTORICAL' AND COALESCE(char_length(btrim(currency_note)), 0) >= 10 AND ended_on IS NOT NULL)));
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_superseded CHECK (superseded_by_id IS NULL OR superseded_by_id <> id);
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_effective CHECK (
    effective_from IS NULL OR expiry_date IS NULL OR expiry_date >= effective_from);
ALTER TABLE trainprov_accreditations ADD CONSTRAINT ck_trainprov_accr_source CHECK (
    (source_type IS NULL AND source_reference IS NULL AND source_version_date IS NULL AND source_location IS NULL)
    OR (COALESCE(source_type IN ('LETTER', 'CERTIFICATE', 'REGISTER_ENTRY', 'NOTICE', 'EMAIL', 'WEBSITE', 'OTHER'), FALSE)
        AND COALESCE(char_length(btrim(source_reference)), 0) >= 5));
CREATE INDEX idx_trainprov_accr_currency ON trainprov_accreditations (tenant_id, authority_id, currency);

-- What backs an accreditation: the letter, certificate or register entry. Pending evidence is not verified evidence.
CREATE TABLE trainprov_accreditation_evidence (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    accreditation_id  UUID NOT NULL REFERENCES trainprov_accreditations(id),
    kind              VARCHAR(30) NOT NULL CHECK (kind IN ('LETTER', 'CERTIFICATE', 'REGISTER_ENTRY', 'NOTICE', 'EMAIL', 'OTHER')),
    description       VARCHAR(300) NOT NULL,
    reference         VARCHAR(300),
    document_date     DATE,
    received_on       DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'WITHDRAWN')),
    added_by          UUID NOT NULL,
    added_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by        UUID,
    decided_at        TIMESTAMPTZ,
    decision_note     TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_accr_ev_description CHECK (COALESCE(char_length(btrim(description)), 0) >= 5),
    CONSTRAINT ck_trainprov_accr_ev_state CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL)
        OR (status = 'VERIFIED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND (decided_by <> added_by OR COALESCE(char_length(btrim(decision_note)), 0) >= 10))
        OR (status IN ('REJECTED', 'WITHDRAWN') AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND COALESCE(char_length(btrim(decision_note)), 0) >= 10))
);
CREATE INDEX idx_trainprov_accr_ev ON trainprov_accreditation_evidence (tenant_id, accreditation_id, status);
