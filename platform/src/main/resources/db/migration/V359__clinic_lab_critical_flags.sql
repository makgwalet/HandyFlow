-- Lab results: remember whether any entered marker is outside the lab's own reference range (has_abnormal) or at or
-- beyond the lab's own critical limits / marked critical by the clinician (has_critical), and who entered the markers.
-- Both flags are worked out from numbers copied from the lab report. No reference ranges or critical limits are
-- built into the system. Existing rows default to false (their markers, if any, were never evaluated).
ALTER TABLE clinic_lab_results ADD COLUMN IF NOT EXISTS has_abnormal       BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE clinic_lab_results ADD COLUMN IF NOT EXISTS has_critical       BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE clinic_lab_results ADD COLUMN IF NOT EXISTS markers_entered_by UUID;
ALTER TABLE clinic_lab_results ADD COLUMN IF NOT EXISTS markers_entered_at TIMESTAMP;

-- The "critical results waiting for a clinician" queue.
CREATE INDEX IF NOT EXISTS idx_clinic_lab_critical_queue
    ON clinic_lab_results (tenant_id, received_at)
    WHERE has_critical AND status = 'UNREVIEWED';
