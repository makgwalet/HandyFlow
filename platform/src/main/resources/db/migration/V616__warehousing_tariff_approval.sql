-- V616: a second person for tariff and minimum-charge changes.
--
-- Tariff and minimum-charge versions are immutable and change what clients are billed, so a change can be required to be asked for by one
-- person and approved by another who holds WAREHOUSING_TARIFF_APPROVE. The rule is off until a warehouse administrator switches it on
-- (whse_profile.tariff_approval_required); while it is off a change applies at once, as before. Where nobody else holds the permission the
-- existing audited sole-approver exception applies. A request that is approved after its start date has passed is refused, so a past day is never re-priced.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'WAREHOUSING_TARIFF_APPROVE', 'Approve or reject a storage tariff or minimum-charge change asked for by someone else')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'warehousing', resource = 'tariff', verb = 'APPROVE', sensitivity = 'FINANCIAL',
       requires_second_person = true, grantable_by = 'MODULE_ADMIN'
 WHERE name = 'WAREHOUSING_TARIFF_APPROVE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions ap ON ap.id = rp.permission_id AND ap.name = 'WAREHOUSING_ADMIN'
  CROSS JOIN permissions np
 WHERE np.name = 'WAREHOUSING_TARIFF_APPROVE'
ON CONFLICT DO NOTHING;

ALTER TABLE whse_profile ADD COLUMN IF NOT EXISTS tariff_approval_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE whse_tariff_requests (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    kind                    VARCHAR(8) NOT NULL CHECK (kind IN ('TARIFF','TERMS')),
    client_id               UUID REFERENCES whse_clients (id),
    item_id                 UUID REFERENCES whse_items (id),
    monthly_rate            NUMERIC(12,4) CHECK (monthly_rate IS NULL OR monthly_rate >= 0),
    minimum_storage_charge  NUMERIC(12,2) CHECK (minimum_storage_charge IS NULL OR minimum_storage_charge >= 0),
    effective_from          DATE NOT NULL,
    note                    VARCHAR(300),
    status                  VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED','CANCELLED')),
    requested_by            UUID NOT NULL,
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by              UUID,
    decided_at              TIMESTAMPTZ,
    decision_note           VARCHAR(500),
    sole_exception          BOOLEAN NOT NULL DEFAULT FALSE,
    resulting_id            UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_whse_tariff_req_shape CHECK (
        (kind = 'TARIFF' AND monthly_rate IS NOT NULL AND minimum_storage_charge IS NULL AND (item_id IS NULL OR client_id IS NOT NULL))
     OR (kind = 'TERMS'  AND minimum_storage_charge IS NOT NULL AND monthly_rate IS NULL AND item_id IS NULL)),
    CONSTRAINT chk_whse_tariff_req_applied CHECK ((status = 'APPLIED') = (resulting_id IS NOT NULL)),
    CONSTRAINT chk_whse_tariff_req_decided CHECK (status = 'PENDING' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE INDEX idx_whse_tariff_req_open ON whse_tariff_requests (tenant_id, requested_at) WHERE status = 'PENDING';
