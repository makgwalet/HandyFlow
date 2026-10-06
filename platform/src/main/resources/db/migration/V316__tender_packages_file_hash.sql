-- V316__tender_packages_file_hash.sql
-- The SHA-256 of the stored combined PDF itself, so a download can prove the stored bytes are still what was built.
-- (package_hash is a different thing: it covers the ordered list of input files, the manifest.)
ALTER TABLE tender_packages ADD COLUMN file_sha256 VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE tender_packages ALTER COLUMN file_sha256 DROP DEFAULT;
