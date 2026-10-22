-- Leave: a company policy for how much annual leave may be carried into a new year, and a ledger entry for days forfeited under it.
-- No row means no limit, which is how leave has always worked, so nothing changes until a company sets a policy.

CREATE TABLE IF NOT EXISTS hr_leave_policy (
    tenant_id           UUID PRIMARY KEY REFERENCES tenants(id),
    carry_over_cap_days NUMERIC(5,2) CHECK (carry_over_cap_days IS NULL OR (carry_over_cap_days >= 0 AND carry_over_cap_days <= 100)),
    -- The first year whose 1 January rollover the cap applies to. Earlier rollovers were already posted without it.
    effective_year      INTEGER,
    -- The written agreement that lets the company forfeit leave (BCEA section 20(7)), named so it can be found later.
    agreement_ref       VARCHAR(200),
    updated_by          UUID,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (carry_over_cap_days IS NULL OR (effective_year IS NOT NULL AND agreement_ref IS NOT NULL AND length(trim(agreement_ref)) > 0))
);

ALTER TABLE hr_leave_ledger DROP CONSTRAINT IF EXISTS hr_leave_ledger_kind_check;
ALTER TABLE hr_leave_ledger ADD CONSTRAINT hr_leave_ledger_kind_check
    CHECK (kind IN ('OPENING','ACCRUAL','CARRY_FORWARD','ADJUSTMENT','RESERVED','RELEASED','TAKEN','REVERSAL','EXPIRY'));
