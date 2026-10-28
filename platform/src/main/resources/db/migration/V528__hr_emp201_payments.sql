-- Payroll: filing and paying the EMP201. Until now nothing could move a declaration past DRAFT. SUBMITTED means it was filed on
-- SARS eFiling and has a payment reference (payment_ref); PAID means the full amount was paid on paid_on.

ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS submitted_by UUID;
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS paid_on      DATE;
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS paid_amount  NUMERIC(15,2);
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS paid_by      UUID;
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS paid_at      TIMESTAMP;

-- The due date was the period end plus seven days, which is only right for a month ending on the last day. It is the 7th of the
-- following month, and the last working day before it when the 7th is a Saturday or Sunday. Public holidays are not allowed for.
UPDATE hr_emp201
   SET due_date = CASE EXTRACT(DOW FROM (date_trunc('month', period_end) + INTERVAL '1 month 6 days'))
                    WHEN 0 THEN (date_trunc('month', period_end) + INTERVAL '1 month 6 days')::date - 2
                    WHEN 6 THEN (date_trunc('month', period_end) + INTERVAL '1 month 6 days')::date - 1
                    ELSE (date_trunc('month', period_end) + INTERVAL '1 month 6 days')::date
                  END
 WHERE status <> 'PAID';
