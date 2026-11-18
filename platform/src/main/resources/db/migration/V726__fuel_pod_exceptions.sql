-- Proof of delivery: received-by exceptions. Additive only.
-- pod_policy_enforced: when on, the server refuses a delivery with no receiver name unless an authorised exception applies.
-- Off by default so existing companies and the web office screen are unchanged until the company switches it on.
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS pod_policy_enforced BOOLEAN NOT NULL DEFAULT false;
-- Which exception types the company allows at all (comma separated). Empty: none, so no exception can be used.
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS pod_exception_types VARCHAR(200) NOT NULL DEFAULT '';
-- A site that is unattended or has automated receiving says so here; only that type is then valid for it.
ALTER TABLE fuel_customer_sites ADD COLUMN IF NOT EXISTS pod_exception_type VARCHAR(30);

CREATE TABLE fuel_pod_exceptions (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    delivery_id    UUID NOT NULL,
    exception_type VARCHAR(30) NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(10) NOT NULL,
    requested_by   UUID,
    approved_by    UUID,
    approved_at    TIMESTAMPTZ,
    used_at        TIMESTAMPTZ,
    evidence_count INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fuel_pod_exc_type CHECK (exception_type IN ('UNATTENDED_SITE','AFTER_HOURS_APPROVED','AUTOMATED_RECEIVING','CONTACTLESS_AUTHORISED')),
    CONSTRAINT chk_fuel_pod_exc_status CHECK (status IN ('APPROVED','USED'))
);
CREATE INDEX ix_fuel_pod_exc_delivery ON fuel_pod_exceptions (tenant_id, delivery_id, created_at DESC);

-- Granted to no role. Approves an after-hours or contactless delivery before the driver completes it.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_POD_EXCEPTION_APPROVE', 'Approve a delivery without a receiver (after hours or contactless), with a reason that is recorded')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions
   SET module_key = 'fuel', resource = 'pod_exception', verb = 'APPROVE', sensitivity = 'SENSITIVE',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_POD_EXCEPTION_APPROVE';
