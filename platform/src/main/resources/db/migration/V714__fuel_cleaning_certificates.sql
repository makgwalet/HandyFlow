-- Cleaning certificates (decision 8): a scan or photo of the cleaning certificate, kept in the shared file store, confirmed by a second person.
ALTER TABLE fuel_compartment_cleanings
    ADD COLUMN file_ref          VARCHAR(500),
    ADD COLUMN content_type      VARCHAR(100),
    ADD COLUMN file_size_bytes   BIGINT,
    ADD COLUMN file_name         VARCHAR(255),
    ADD COLUMN file_uploaded_by  UUID,
    ADD COLUMN file_uploaded_at  TIMESTAMPTZ,
    ADD COLUMN verified_by       UUID,
    ADD COLUMN verified_at       TIMESTAMPTZ;

-- Verification needs an uploaded file, and never by the person who recorded the cleaning or uploaded the file.
ALTER TABLE fuel_compartment_cleanings ADD CONSTRAINT ck_fuel_cleaning_verify CHECK (
    verified_by IS NULL OR (file_ref IS NOT NULL AND verified_by IS DISTINCT FROM recorded_by AND verified_by IS DISTINCT FROM file_uploaded_by));

-- Off by default: existing companies keep today's rule (a certificate reference or a note is enough). When on, a cleaning clears a changeover
-- only once its certificate is uploaded and verified.
ALTER TABLE fuel_settings ADD COLUMN require_verified_cleaning BOOLEAN NOT NULL DEFAULT FALSE;
