-- V828 created lp_document_versions.sha256 as CHAR(64). The entity maps it as a String (VARCHAR), and Hibernate's schema validation refuses CHAR.
-- The column only ever holds 64 lowercase hex characters (ck_lp_docv_hash), so VARCHAR(64) holds exactly the same values.
ALTER TABLE lp_document_versions ALTER COLUMN sha256 TYPE VARCHAR(64);
