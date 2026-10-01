-- V306__agriculture_cost_ledger.sql
--
-- ADR-001 (Agriculture financial integration), W1: one ledger for the NEW direct-cost categories (labour, equipment, fuel and other
-- direct costs), each row allocated to one production target. The existing direct costs (feed, health, inputs, seed, animal
-- purchases) stay in their own tables and are NOT duplicated here, so nothing is counted twice.
--
-- Rows are append-only: a correction is a REVERSAL row (negative amount, reverses_entry_id set) and the original is marked REVERSED.
-- The net cost of anything is therefore the plain SUM(amount) over its rows. A cost split across several targets is several rows
-- sharing an allocation_group_id whose percentages total 100 and whose amounts add up exactly to the original cost.
--
-- Rates owned by other modules (HR pay, Fleet equipment, Fuel) are SNAPSHOTTED into quantity/unit/rate/amount when the row is
-- written, so a later pay rise never restates a past production cycle.
CREATE TABLE ag_cost_entries (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    farm_id               UUID NOT NULL,
    entry_date            DATE NOT NULL,
    category              VARCHAR(20)  NOT NULL,
    description           VARCHAR(255) NOT NULL,
    source_type           VARCHAR(30)  NOT NULL,
    source_ref            UUID,
    target_type           VARCHAR(20)  NOT NULL,
    target_id             UUID NOT NULL,
    quantity              NUMERIC(14,3),
    unit                  VARCHAR(20),
    rate                  NUMERIC(14,4),
    amount                NUMERIC(14,2) NOT NULL,
    percentage            NUMERIC(7,4)  NOT NULL,
    allocation_group_id   UUID NOT NULL,
    reverses_entry_id     UUID,
    status                VARCHAR(10)  NOT NULL,
    notes                 TEXT,
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT,
    CONSTRAINT chk_ag_cost_entries_category CHECK (category IN ('LABOUR', 'EQUIPMENT', 'FUEL', 'OTHER_DIRECT')),
    CONSTRAINT chk_ag_cost_entries_target   CHECK (target_type IN ('CROP_CYCLE', 'GROUP', 'ANIMAL', 'ENTERPRISE')),
    CONSTRAINT chk_ag_cost_entries_status   CHECK (status IN ('ACTIVE', 'REVERSED', 'REVERSAL')),
    CONSTRAINT chk_ag_cost_entries_amount   CHECK (amount <> 0)
);
CREATE INDEX idx_ag_cost_entries_tenant_farm   ON ag_cost_entries (tenant_id, farm_id, entry_date);
CREATE INDEX idx_ag_cost_entries_target        ON ag_cost_entries (tenant_id, target_type, target_id);
CREATE INDEX idx_ag_cost_entries_group         ON ag_cost_entries (tenant_id, allocation_group_id);

-- Anything showing labour cost, revenue or margin needs its own permission: rates are derived from salaries (ADR-001, decision 5).
-- Granted to ADMIN only; give it to other roles deliberately.
INSERT INTO permissions (id, name, description)
VALUES (gen_random_uuid(), 'AGRICULTURE_FINANCE', 'View and record Agriculture costs, labour cost, revenue and margin')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'AGRICULTURE_FINANCE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
