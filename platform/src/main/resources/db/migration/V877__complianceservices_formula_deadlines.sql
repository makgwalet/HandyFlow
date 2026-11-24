-- Compliance Services: deadlines worked out from an obligation's due rule. The client needs two key dates for some rules
-- (financial year end month, date registered). A generated deadline remembers which obligation and version produced it,
-- and cannot be generated twice for the same date. One whose rule or applicability changes is cancelled, never deleted.

ALTER TABLE compliance_service_clients ADD COLUMN IF NOT EXISTS financial_year_end_month INTEGER;
ALTER TABLE compliance_service_clients ADD COLUMN IF NOT EXISTS registered_on DATE;

ALTER TABLE client_compliance_deadlines ADD COLUMN IF NOT EXISTS obligation_id UUID;
ALTER TABLE client_compliance_deadlines ADD COLUMN IF NOT EXISTS obligation_version_number INTEGER;
ALTER TABLE client_compliance_deadlines ADD COLUMN IF NOT EXISTS generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE client_compliance_deadlines ADD COLUMN IF NOT EXISTS responsible_role VARCHAR(20);
ALTER TABLE client_compliance_deadlines ADD COLUMN IF NOT EXISTS cancelled_reason TEXT;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_client_fye_month') THEN
        ALTER TABLE compliance_service_clients ADD CONSTRAINT ck_client_fye_month
            CHECK (financial_year_end_month IS NULL OR financial_year_end_month BETWEEN 1 AND 12);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_client_deadline_generated') THEN
        ALTER TABLE client_compliance_deadlines ADD CONSTRAINT ck_client_deadline_generated
            CHECK ((generated = FALSE OR (obligation_id IS NOT NULL AND obligation_version_number IS NOT NULL))
               AND (status <> 'CANCELLED' OR cancelled_reason IS NOT NULL));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_client_deadline_obligation_date
    ON client_compliance_deadlines(client_id, obligation_id, due_date)
    WHERE obligation_id IS NOT NULL AND status <> 'CANCELLED';
