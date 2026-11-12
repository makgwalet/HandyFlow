-- Photo and signature capture: proof-of-delivery evidence can now hold a real file kept in the shared file store.
-- file_ref keeps the store's opaque key; the extra columns describe the file so it can be served back without guessing.
-- Existing rows (a typed reference, or GPS) stay as they are: file_stored is false for them.
ALTER TABLE fuel_delivery_evidence
    ADD COLUMN file_stored      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN content_type     VARCHAR(100),
    ADD COLUMN file_size_bytes  INTEGER,
    ADD COLUMN file_name        VARCHAR(120);

ALTER TABLE fuel_delivery_evidence
    ADD CONSTRAINT ck_fuel_evidence_stored_file
    CHECK (file_stored = FALSE OR (file_ref IS NOT NULL AND content_type IS NOT NULL AND file_size_bytes IS NOT NULL AND file_size_bytes > 0));
