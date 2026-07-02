-- V90 — Add cancellation_reason to projects
--
-- WHY:
-- Project.cancel(reason) previously appended the reason to the user-facing
-- notes field:
--   this.notes = notes + "\nCancelled: " + reason
-- This destroyed the user's original note content and mixed system-generated
-- text with user input.  The reason was also unqueryable (buried in free text).
--
-- The cancellation_reason column gives cancel() a dedicated home.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);

COMMENT ON COLUMN projects.cancellation_reason IS
    'System-recorded reason for cancellation — populated by Project.cancel(reason).
     Never written to by the user.  Separate from the notes field.';
