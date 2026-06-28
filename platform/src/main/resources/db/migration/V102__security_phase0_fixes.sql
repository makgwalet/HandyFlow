-- =============================================================================
-- V50__security_phase0_fixes.sql
-- Phase 0 stabilisation: fix all production bugs identified in audit
-- Safe to re-run: all use IF NOT EXISTS / IF EXISTS guards
-- =============================================================================

-- ── 1. Guard status workflow (fixes bug #5) ───────────────────────────────────
-- WHY a status column instead of just the existing boolean active?
-- `active` is a tombstone (deleted or not).
-- `status` is a workflow state: a guard can be SUSPENDED (active in the DB,
-- cannot be scheduled) or ON_LEAVE (active, unavailable temporarily).
-- Collapsing these into `active = false` loses the distinction permanently.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS status_changed_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status_note        TEXT,
    ADD COLUMN IF NOT EXISTS psira_expiry_date  DATE;

-- Backfill: existing soft-deleted guards → TERMINATED; others → ACTIVE
UPDATE security_guards SET status = 'TERMINATED' WHERE deleted_at IS NOT NULL AND status = 'ACTIVE';

-- Index: scheduling queries filter on status — partial index for active/on-leave guards only
CREATE INDEX IF NOT EXISTS idx_guards_status
    ON security_guards(tenant_id, status)
    WHERE deleted_at IS NULL;

-- ── 2. Scan type on checkpoint logs (fixes bug #6) ────────────────────────────
-- WHY? CheckpointScanService currently ignores scanType and always does
-- findByQrCode — NFC/BLE scans with qrCode=null throw NPE.
-- Adding scan_type to the log also gives patrol audit trail of HOW each
-- checkpoint was verified, which is the kind of evidence clients want.
ALTER TABLE security_checkpoint_logs
    ADD COLUMN IF NOT EXISTS scan_type  VARCHAR(20) DEFAULT 'QR';

-- ── 3. Per-checkpoint cooldown support (fixes bug #18) ────────────────────────
-- WHY on the log table (not the checkpoint table)?
-- The cooldown is enforced by querying the MOST RECENT log for this checkpoint
-- within the current shift — the query already exists via findByShift.
-- We add an index to make this sub-millisecond at volume.
-- No new column needed: we query scanned_at of the last log row.
CREATE INDEX IF NOT EXISTS idx_logs_checkpoint_shift
    ON security_checkpoint_logs(checkpoint_id, shift_id, scanned_at DESC)
    WHERE shift_id IS NOT NULL;

-- ── 4. NFC tag support on checkpoints (fixes bug #6 — NFC scan method) ────────
-- WHY? ScanRequest already has nfcTagId/bleBeaconId fields but Checkpoint
-- has no matching columns — the scan service can't look them up.
ALTER TABLE security_checkpoints
    ADD COLUMN IF NOT EXISTS nfc_tag_uid   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ble_beacon_id VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_checkpoint_nfc
    ON security_checkpoints(nfc_tag_uid)
    WHERE nfc_tag_uid IS NOT NULL AND active = true;

CREATE UNIQUE INDEX IF NOT EXISTS uq_checkpoint_ble
    ON security_checkpoints(ble_beacon_id)
    WHERE ble_beacon_id IS NOT NULL AND active = true;

-- ── 5. Incident actor tracking (fixes bug #20) ────────────────────────────────
-- WHY? acknowledge/resolve currently record only the timestamp, not WHO did it.
-- For SLA disputes and audit ("who signed off incident #IR-042?") you need
-- the actor. TenantContext provides this — we just need the column.
ALTER TABLE security_incidents
    ADD COLUMN IF NOT EXISTS acknowledged_by UUID,
    ADD COLUMN IF NOT EXISTS resolved_by     UUID;

-- ── 6. Incident type from CreateIncidentRequest (fixes bug #16) ───────────────
-- WHY? V12 created a type CHECK constraint but CreateIncidentRequest/
-- IncidentService never set it — every incident is 'GENERAL'.
-- Dropping the old constraint first (it was too restrictive and blocked
-- the GENERAL default from V48 anyway).
ALTER TABLE security_incidents DROP CONSTRAINT IF EXISTS chk_incident_type;
ALTER TABLE security_incidents
    ADD CONSTRAINT chk_incident_type CHECK (
        type IN ('THEFT','TRESPASS','MEDICAL','FIRE','VANDALISM',
                 'ASSAULT','SUSPICIOUS','GENERAL','OTHER')
    );

-- ── 7. Shift scan-count enforcement column (fixes bug #17) ────────────────────
-- WHY on shifts? completeShift needs to know the minimum checkpoint scan
-- count required by the site to allow completion.
-- We store it on the shift (copied from site config at shift creation time)
-- so it's immutable for that shift even if the site config changes.
-- Default 0 = enforcement disabled (current behaviour preserved for existing rows).
ALTER TABLE security_shifts
    ADD COLUMN IF NOT EXISTS min_scan_count  INTEGER NOT NULL DEFAULT 0;

-- ── 8. Ensure tenant_id index on shifts for findOverlapping fix (bug #15) ─────
CREATE INDEX IF NOT EXISTS idx_shifts_overlap
    ON security_shifts(guard_id, tenant_id, start_at, end_at)
    WHERE deleted_at IS NULL
      AND status NOT IN ('CANCELLED', 'MISSED');

-- ── 9. SiteResponse terminatedAt was already in Site.java, just needs exposing ─
-- (terminated_at was added in V48; confirmed present in Site.java — no DDL needed)
-- ── End of V50 ────────────────────────────────────────────────────────────────