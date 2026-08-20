-- V236__add_payrun_auto_send.sql
--
-- Idempotency flag for Option C auto-send: prevents the same run's
-- payslips being emailed twice if the scheduler runs more than once
-- while a run remains PROCESSED (e.g. payDate already passed by the
-- time it was processed). Same shape as ApBill.due_soon_reminder_sent
-- and RecInterview's own reminder-sent flag — a real, established
-- precedent for this exact problem, not a new pattern.
ALTER TABLE pay_runs ADD COLUMN payslips_auto_sent_at TIMESTAMPTZ;