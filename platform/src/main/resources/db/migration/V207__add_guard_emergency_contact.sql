-- V220__add_guard_emergency_contact.sql
-- (Rename to your actual next migration number before applying.)
--
-- Supports the "at least one of guard's own phone OR emergency contact
-- phone is required" onboarding rule -- decided in place of making the
-- guard's own phone mandatory outright, since that would have contradicted
-- the earlier employee-code-login design premise (many guards won't
-- reliably have a personal registered phone). An emergency contact is
-- something every employee should have on file regardless.

ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS emergency_contact_name  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS emergency_contact_phone VARCHAR(30);

COMMENT ON COLUMN security_guards.emergency_contact_phone IS
    'Enforced alongside guard.phone by GuardService.createGuard()/updateGuard(): at least ONE of the two must be present. Neither is individually mandatory.';