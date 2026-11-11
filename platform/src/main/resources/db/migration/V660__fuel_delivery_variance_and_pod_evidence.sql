-- Fuel Phase 3 (Distribution controls). Additive only.
--  C02 delivery variance: litres loaded vs litres delivered, with a review record. The tolerance is a per-company
--      setting with NO default: until a company sets it, no variance is flagged.
--  C01 proof-of-delivery evidence: append-only records (photo, signature, document, GPS) that can be verified and then locked.

ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS variance_tolerance_pct NUMERIC(5,2);
ALTER TABLE fuel_settings DROP CONSTRAINT IF EXISTS chk_fuel_settings_variance_tol;
ALTER TABLE fuel_settings ADD CONSTRAINT chk_fuel_settings_variance_tol
    CHECK (variance_tolerance_pct IS NULL OR (variance_tolerance_pct >= 0 AND variance_tolerance_pct <= 100));

CREATE TABLE IF NOT EXISTS fuel_delivery_variances (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    delivery_id      UUID NOT NULL,
    loaded_litres    NUMERIC(12,2) NOT NULL,
    delivered_litres NUMERIC(12,2) NOT NULL,
    variance_litres  NUMERIC(12,2) NOT NULL,
    variance_pct     NUMERIC(8,3)  NOT NULL,
    tolerance_pct    NUMERIC(5,2)  NOT NULL,
    status           VARCHAR(14)   NOT NULL DEFAULT 'OPEN',
    decision         VARCHAR(12),
    note             VARCHAR(300),
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    detected_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_variance_status CHECK (status IN ('OPEN','INVESTIGATING','CLOSED')),
    CONSTRAINT chk_fuel_variance_decision CHECK (decision IS NULL OR decision IN ('EXPLAINED','WRITTEN_OFF')),
    CONSTRAINT chk_fuel_variance_closed CHECK (status <> 'CLOSED' OR (decision IS NOT NULL AND note IS NOT NULL AND reviewed_by IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_variance_delivery ON fuel_delivery_variances (tenant_id, delivery_id);
CREATE INDEX IF NOT EXISTS idx_fuel_variance_tenant_status ON fuel_delivery_variances (tenant_id, status, detected_at DESC);

CREATE TABLE IF NOT EXISTS fuel_delivery_evidence (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    delivery_id       UUID NOT NULL,
    kind              VARCHAR(12) NOT NULL,
    file_ref          VARCHAR(500),
    latitude          NUMERIC(9,6),
    longitude         NUMERIC(9,6),
    accuracy_m        NUMERIC(8,2),
    note              VARCHAR(300),
    captured_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    captured_by       UUID,
    status            VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    superseded_reason VARCHAR(300),
    superseded_at     TIMESTAMPTZ,
    superseded_by     UUID,
    verified_by       UUID,
    verified_at       TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_evidence_kind CHECK (kind IN ('PHOTO','SIGNATURE','DOCUMENT','GPS')),
    CONSTRAINT chk_fuel_evidence_status CHECK (status IN ('ACTIVE','SUPERSEDED')),
    CONSTRAINT chk_fuel_evidence_gps CHECK (kind <> 'GPS' OR (latitude IS NOT NULL AND longitude IS NOT NULL)),
    CONSTRAINT chk_fuel_evidence_file CHECK (kind = 'GPS' OR (file_ref IS NOT NULL AND length(trim(file_ref)) > 0)),
    CONSTRAINT chk_fuel_evidence_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_fuel_evidence_lon CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_fuel_evidence_locked CHECK (verified_at IS NULL OR status = 'ACTIVE')
);
CREATE INDEX IF NOT EXISTS idx_fuel_evidence_delivery ON fuel_delivery_evidence (tenant_id, delivery_id, captured_at);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_VARIANCE_REVIEW', 'Review and close loaded-versus-delivered fuel variances'),
    (gen_random_uuid(), 'FUEL_EVIDENCE_VERIFY', 'Verify proof-of-delivery evidence, which then locks it')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'variance', verb = 'REVIEW', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_VARIANCE_REVIEW';
UPDATE permissions SET module_key = 'fuel', resource = 'evidence', verb = 'VERIFY', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_EVIDENCE_VERIFY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('FUEL_VARIANCE_REVIEW', 'FUEL_EVIDENCE_VERIFY')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
