-- Imports can now carry deadlines as well as clients and registrations.
ALTER TABLE compliance_import_batches DROP CONSTRAINT IF EXISTS ck_cs_import_kind;
ALTER TABLE compliance_import_batches ADD CONSTRAINT ck_cs_import_kind CHECK (kind IN ('CLIENTS', 'REGISTRATIONS', 'DEADLINES'));
