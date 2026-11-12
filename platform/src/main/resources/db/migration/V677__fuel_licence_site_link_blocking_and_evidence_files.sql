-- F01 leftovers.
-- 1. A licence can be tied to a depot (site), so coverage can be read per site as well as per legal entity.
-- 2. An opt-in, per-company setting: which licensed activities block loading when expired on the loading depot's legal entity.
--    Empty (the default) means no change from today. The system does not decide which licences a business must hold.
-- 3. Compliance evidence can be a stored file (scan or photo) and can be verified by a second person.

ALTER TABLE fuel_compliance_items ADD COLUMN depot_id UUID REFERENCES fuel_depots(id);
CREATE INDEX idx_fuel_compliance_items_depot ON fuel_compliance_items (tenant_id, depot_id) WHERE depot_id IS NOT NULL;

ALTER TABLE fuel_settings ADD COLUMN licence_blocking_activities VARCHAR(120) NOT NULL DEFAULT '';

ALTER TABLE fuel_compliance_evidence
    ADD COLUMN file_stored      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN content_type     VARCHAR(100),
    ADD COLUMN file_size_bytes  INTEGER,
    ADD COLUMN file_name        VARCHAR(120),
    ADD COLUMN verified_by      UUID,
    ADD COLUMN verified_at      TIMESTAMPTZ;

ALTER TABLE fuel_compliance_evidence
    ADD CONSTRAINT ck_fuel_compliance_evidence_stored_file
    CHECK (file_stored = FALSE OR (content_type IS NOT NULL AND file_size_bytes IS NOT NULL AND file_size_bytes > 0)),
    ADD CONSTRAINT ck_fuel_compliance_evidence_verifier
    CHECK (verified_by IS NULL OR captured_by IS NULL OR verified_by <> captured_by),
    ADD CONSTRAINT ck_fuel_compliance_evidence_verified_pair
    CHECK ((verified_by IS NULL) = (verified_at IS NULL));

-- Verification is its own permission, given to ADMIN only.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_COMPLIANCE_VERIFY', 'Verify licence and certificate evidence, not your own uploads')
ON CONFLICT (name) DO NOTHING;
UPDATE permissions SET module_key = 'fuel', resource = 'compliance_evidence', verb = 'VERIFY', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_COMPLIANCE_VERIFY';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'FUEL_COMPLIANCE_VERIFY'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
