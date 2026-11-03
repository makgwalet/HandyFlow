-- Secure Document Access Engine, phase 4: Contracting and Facilities Management (FM provider) on the engine.
--
-- Contracting: a signed contract gets one frozen executed copy (the PDF rendered once, kept in the document store), so it can be
-- shared through a secure link with an exact hash and kept for its retention period. The retention clock starts when the contract
-- ends (terminated or expired). Disposal deletes the executed copy, clears the contract text and the drawn signatures, and keeps
-- the contract record (number, title, parties, dates, hashes) as the tombstone.
--
-- FM: the files attached to a work order (photos, certificates, reports) can be shared with the client or a third party once the
-- work order is completed, and are kept for their retention period from completion (or cancellation). Files attached to an asset
-- are kept from the day the asset is decommissioned.
--
-- retention_clock_at records that the module has started the record's clock in the retention engine, so the nightly sync does
-- each record once.

-- ── Contracting ─────────────────────────────────────────────────────────────
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS executed_storage_key  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS executed_sha256       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS executed_size_bytes   BIGINT,
    ADD COLUMN IF NOT EXISTS executed_stored_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retention_clock_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposal_batch_id     UUID;

ALTER TABLE contracts DROP CONSTRAINT IF EXISTS chk_contracts_executed_copy;
ALTER TABLE contracts ADD CONSTRAINT chk_contracts_executed_copy CHECK (
    (executed_storage_key IS NULL) = (executed_sha256 IS NULL)
    AND (executed_storage_key IS NULL) = (executed_stored_at IS NULL)
    AND (executed_sha256 IS NULL OR executed_sha256 ~ '^[0-9a-f]{64}$'));

-- The executed copy is written once and never replaced; a disposed contract is final.
CREATE OR REPLACE FUNCTION contracts_executed_copy_guard() RETURNS trigger AS $$
BEGIN
    IF OLD.executed_storage_key IS NOT NULL AND (NEW.executed_storage_key IS DISTINCT FROM OLD.executed_storage_key
            OR NEW.executed_sha256 IS DISTINCT FROM OLD.executed_sha256 OR NEW.executed_stored_at IS DISTINCT FROM OLD.executed_stored_at) THEN
        RAISE EXCEPTION 'The executed copy of a contract is written once and cannot be replaced.';
    END IF;
    IF OLD.disposed_at IS NOT NULL AND (NEW.disposed_at IS DISTINCT FROM OLD.disposed_at OR NEW.body IS DISTINCT FROM OLD.body
            OR NEW.status IS DISTINCT FROM OLD.status) THEN
        RAISE EXCEPTION 'A disposed contract is final and cannot be changed.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_contracts_executed_copy_guard ON contracts;
CREATE TRIGGER trg_contracts_executed_copy_guard BEFORE UPDATE ON contracts
    FOR EACH ROW EXECUTE FUNCTION contracts_executed_copy_guard();

-- ── FM provider ─────────────────────────────────────────────────────────────
ALTER TABLE fm_work_orders
    ADD COLUMN IF NOT EXISTS retention_clock_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS files_disposed_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposal_batch_id    UUID;
ALTER TABLE fm_assets
    ADD COLUMN IF NOT EXISTS retention_clock_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS files_disposed_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposal_batch_id    UUID;

-- The nightly sync looks for finished records whose clock has not been started.
CREATE INDEX IF NOT EXISTS idx_fm_wo_retention_pending ON fm_work_orders (tenant_id)
    WHERE retention_clock_at IS NULL AND deleted_at IS NULL AND status IN ('COMPLETED', 'CANCELLED', 'REJECTED');
CREATE INDEX IF NOT EXISTS idx_fm_assets_retention_pending ON fm_assets (tenant_id)
    WHERE retention_clock_at IS NULL AND status = 'DECOMMISSIONED';
CREATE INDEX IF NOT EXISTS idx_contracts_retention_pending ON contracts (tenant_id)
    WHERE retention_clock_at IS NULL AND deleted_at IS NULL AND status IN ('EXPIRED', 'TERMINATED', 'ARCHIVED');

-- ── Backfill: existing records into the retention engine ────────────────────
-- Contracts that were signed (whatever happened after). Clock: termination date, else end date, else last change.
INSERT INTO document_retention_items (id, tenant_id, module_code, source_type, source_id, category, reference, title, context_label,
                                      clock_start, retain_until, policy_years)
SELECT gen_random_uuid(), c.tenant_id, 'CONTRACTING', 'CONTRACT', c.id, upper(regexp_replace(c.contract_type, '[^A-Za-z0-9_]', '_', 'g')),
       left(c.contract_number, 150), left(c.title, 300), left(p.names, 200),
       e.ended_on, (e.ended_on + INTERVAL '7 years')::date, CASE WHEN e.ended_on IS NULL THEN NULL ELSE 7 END
FROM contracts c
LEFT JOIN LATERAL (SELECT string_agg(coalesce(nullif(cp.company_name, ''), cp.full_name), ', ' ORDER BY cp.signing_order) AS names
                   FROM contract_parties cp WHERE cp.contract_id = c.id AND cp.party_type = 'EXTERNAL') p ON TRUE
CROSS JOIN LATERAL (SELECT CASE WHEN c.status IN ('EXPIRED', 'TERMINATED', 'ARCHIVED')
                                THEN coalesce(c.terminated_at::date, c.end_date, c.updated_at::date) END AS ended_on) e
WHERE c.deleted_at IS NULL AND c.status IN ('SIGNED', 'EXPIRED', 'TERMINATED', 'ARCHIVED')
ON CONFLICT (tenant_id, module_code, source_type, source_id) DO NOTHING;

UPDATE contracts SET retention_clock_at = now()
WHERE retention_clock_at IS NULL AND deleted_at IS NULL AND status IN ('EXPIRED', 'TERMINATED', 'ARCHIVED');

-- FM work orders with at least one active file. Clock: completion date (SAST), or the last change for a cancelled or rejected one.
INSERT INTO document_retention_items (id, tenant_id, module_code, source_type, source_id, category, reference, title, context_label,
                                      clock_start, retain_until, policy_years)
SELECT gen_random_uuid(), w.tenant_id, 'FM', 'WORK_ORDER', w.id, 'WORK_ORDER', left(w.work_order_number, 150),
       left(coalesce(nullif(w.description, ''), 'Work order'), 300), left(cl.trading_name || ' · ' || s.name, 200),
       e.ended_on, (e.ended_on + INTERVAL '7 years')::date, CASE WHEN e.ended_on IS NULL THEN NULL ELSE 7 END
FROM fm_work_orders w
JOIN fm_clients cl ON cl.id = w.client_id
JOIN fm_sites s ON s.id = w.site_id
CROSS JOIN LATERAL (SELECT CASE WHEN w.status = 'COMPLETED' THEN (coalesce(w.completed_at, w.updated_at) AT TIME ZONE 'Africa/Johannesburg')::date
                                WHEN w.status IN ('CANCELLED', 'REJECTED') THEN (w.updated_at AT TIME ZONE 'Africa/Johannesburg')::date END AS ended_on) e
WHERE w.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM evidence ev WHERE ev.tenant_id = w.tenant_id AND ev.source_module = 'facilitiesmanagement'
                AND ev.related_entity_type = 'FmWorkOrder' AND ev.related_entity_id = w.id AND ev.status = 'ACTIVE')
ON CONFLICT (tenant_id, module_code, source_type, source_id) DO NOTHING;

UPDATE fm_work_orders w SET retention_clock_at = now()
WHERE w.retention_clock_at IS NULL AND w.status IN ('COMPLETED', 'CANCELLED', 'REJECTED')
  AND EXISTS (SELECT 1 FROM document_retention_items i WHERE i.tenant_id = w.tenant_id AND i.module_code = 'FM'
                AND i.source_type = 'WORK_ORDER' AND i.source_id = w.id);

-- FM assets with at least one active file. Clock: the day the asset was decommissioned (its last change).
INSERT INTO document_retention_items (id, tenant_id, module_code, source_type, source_id, category, reference, title, context_label,
                                      clock_start, retain_until, policy_years)
SELECT gen_random_uuid(), a.tenant_id, 'FM', 'ASSET', a.id, 'ASSET', left(coalesce(a.asset_tag, ''), 150), left(a.name, 300),
       left(cl.trading_name || ' · ' || s.name, 200),
       e.ended_on, (e.ended_on + INTERVAL '7 years')::date, CASE WHEN e.ended_on IS NULL THEN NULL ELSE 7 END
FROM fm_assets a
JOIN fm_sites s ON s.id = a.site_id
JOIN fm_clients cl ON cl.id = s.client_id
CROSS JOIN LATERAL (SELECT CASE WHEN a.status = 'DECOMMISSIONED' THEN (a.updated_at AT TIME ZONE 'Africa/Johannesburg')::date END AS ended_on) e
WHERE a.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM evidence ev WHERE ev.tenant_id = a.tenant_id AND ev.source_module = 'facilitiesmanagement'
                AND ev.related_entity_type = 'FmAsset' AND ev.related_entity_id = a.id AND ev.status = 'ACTIVE')
ON CONFLICT (tenant_id, module_code, source_type, source_id) DO NOTHING;

UPDATE fm_assets a SET retention_clock_at = now()
WHERE a.retention_clock_at IS NULL AND a.status = 'DECOMMISSIONED'
  AND EXISTS (SELECT 1 FROM document_retention_items i WHERE i.tenant_id = a.tenant_id AND i.module_code = 'FM'
                AND i.source_type = 'ASSET' AND i.source_id = a.id);
