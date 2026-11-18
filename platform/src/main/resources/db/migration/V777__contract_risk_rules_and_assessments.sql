-- Risk rules. The tenant's legal reviewer writes them; the system ships none. A rule is a deterministic check on a contract (a missing
-- clause category, a phrase present or absent, a value, a term, a renewal notice). Rules follow the same lifecycle as library clauses:
-- DRAFT -> APPROVED (a recorded legal review) -> PUBLISHED, replaced by a new version. A review is evidence that it took place on the
-- date given, not a guarantee that the rule is complete or correct for every contract.
CREATE TABLE contract_risk_rules (
    id                            UUID PRIMARY KEY,
    tenant_id                     UUID NOT NULL,
    family_id                     UUID NOT NULL,
    version_no                    INTEGER NOT NULL DEFAULT 1,
    name                          VARCHAR(200) NOT NULL,
    description                   TEXT,
    rule_type                     VARCHAR(40)  NOT NULL,
    param_text                    VARCHAR(500),
    param_number                  NUMERIC(19, 2),
    applies_to_types              VARCHAR(500),          -- comma separated contract types; empty means every type
    severity                      VARCHAR(10)  NOT NULL,
    blocks_sending                BOOLEAN NOT NULL DEFAULT FALSE,
    message                       VARCHAR(500) NOT NULL, -- shown when the rule fails
    remediation                   VARCHAR(1000),
    legal_area                    VARCHAR(100),
    jurisdiction                  VARCHAR(100) NOT NULL DEFAULT 'South Africa',
    status                        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reviewer_name                 VARCHAR(200),
    reviewer_professional_details VARCHAR(300),
    review_date                   DATE,
    next_review_date              DATE,
    review_notes                  TEXT,
    published_at                  TIMESTAMP,
    withdrawn_at                  TIMESTAMP,
    withdrawn_reason              VARCHAR(500),
    created_by                    UUID,
    created_at                    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_risk_rule_status CHECK (status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT chk_risk_rule_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT uq_risk_rule_family_version UNIQUE (family_id, version_no)
);
CREATE UNIQUE INDEX uq_risk_rule_one_published_per_family ON contract_risk_rules (family_id) WHERE status = 'PUBLISHED';
CREATE INDEX idx_contract_risk_rules_tenant ON contract_risk_rules (tenant_id, status);

-- A stored risk check: what was checked, against which version of the text, and what was found.
CREATE TABLE contract_risk_assessments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    contract_id          UUID NOT NULL REFERENCES contracts(id),
    contract_version_no  INTEGER,
    risk_level           VARCHAR(20) NOT NULL,            -- NOT_ASSESSED, NONE, LOW, MEDIUM, HIGH, CRITICAL
    rules_evaluated      INTEGER NOT NULL,
    rules_skipped        INTEGER NOT NULL,                -- published but not applied: review expired or not for this contract type
    findings_json        TEXT NOT NULL,
    assessed_by          UUID,
    assessed_at          TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_risk_assessments_contract ON contract_risk_assessments (tenant_id, contract_id, assessed_at DESC);
