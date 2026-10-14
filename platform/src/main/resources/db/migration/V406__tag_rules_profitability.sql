-- Finance F28: more kinds of cost centre (site, vehicle, person) and rules that tag journal lines automatically.
ALTER TABLE acc_cost_centres DROP CONSTRAINT acc_cost_centres_kind_check;
ALTER TABLE acc_cost_centres ADD CONSTRAINT acc_cost_centres_kind_check
    CHECK (kind IN ('COST_CENTRE','DEPARTMENT','BRANCH','PROJECT','SITE','VEHICLE','PERSON'));

CREATE TABLE acc_tag_rules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    name           VARCHAR(120) NOT NULL,
    priority       INT NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 9999),
    account_code   VARCHAR(20),
    text_contains  VARCHAR(80),
    entry_type     VARCHAR(20) CHECK (entry_type IS NULL OR entry_type IN ('MANUAL','INVOICE','PAYMENT','BANK','DEPRECIATION','ADJUSTMENT','VAT')),
    cost_centre_id UUID NOT NULL REFERENCES acc_cost_centres(id),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by     UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tag_rule_has_condition CHECK (account_code IS NOT NULL OR text_contains IS NOT NULL OR entry_type IS NOT NULL)
);
CREATE INDEX idx_acc_tag_rules_tenant ON acc_tag_rules (tenant_id, active, priority);

-- Every line tagged after the fact (the rules run on new journals without a log: the rule itself is the explanation).
CREATE TABLE acc_cost_centre_tag_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    line_id        UUID NOT NULL,
    cost_centre_id UUID NOT NULL REFERENCES acc_cost_centres(id),
    rule_id        UUID,
    applied_by     UUID,
    applied_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_acc_cost_centre_tag_log_line ON acc_cost_centre_tag_log (tenant_id, line_id);
