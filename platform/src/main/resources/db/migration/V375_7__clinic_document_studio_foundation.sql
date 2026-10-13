-- Document Studio foundation (patch 0193; CLINIC-DEC-023, 025, 026).
-- 1. Letterheads: one practice-wide default and optional branch letterheads (a branch is a name here: the clinic has no branch table yet).
-- 2. A numbered, hashed record of every document the system issues. The record is permanent; only a void can be added to it later.
-- 3. A yearly counter per document type, so numbers are consecutive and never reused.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_LETTERHEAD_MANAGE', 'Create and change the practice and branch letterheads')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'CLINIC_ADMIN'
JOIN permissions np ON np.name = 'CLINIC_LETTERHEAD_MANAGE'
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS clinic_letterheads (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    name          VARCHAR(120) NOT NULL,                 -- the label people choose from
    branch_name   VARCHAR(120),                          -- NULL for the practice-wide letterhead
    practice_name VARCHAR(120) NOT NULL,                 -- printed at the top
    lines         JSONB        NOT NULL DEFAULT '[]'::jsonb,   -- address, phone, practice number: up to six short lines
    logo          BYTEA,
    logo_type     VARCHAR(20),                           -- image/png or image/jpeg
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,   -- the practice-wide letterhead used when none is chosen
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by    UUID,
    CONSTRAINT chk_letterhead_logo CHECK ((logo IS NULL) = (logo_type IS NULL)),
    CONSTRAINT chk_letterhead_default_is_practice_wide CHECK (NOT is_default OR branch_name IS NULL)
);
-- One active default per practice, and one active letterhead per branch name.
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_letterhead_default ON clinic_letterheads (tenant_id) WHERE is_default AND active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_letterhead_branch  ON clinic_letterheads (tenant_id, lower(branch_name)) WHERE branch_name IS NOT NULL AND active;

CREATE TABLE IF NOT EXISTS clinic_document_counters (
    tenant_id  UUID        NOT NULL REFERENCES tenants(id),
    doc_type   VARCHAR(30) NOT NULL,
    year       INT         NOT NULL,
    last_value INT         NOT NULL,
    PRIMARY KEY (tenant_id, doc_type, year)
);

CREATE TABLE IF NOT EXISTS clinic_issued_documents (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL REFERENCES tenants(id),
    doc_number         VARCHAR(40)  NOT NULL,
    doc_type           VARCHAR(30)  NOT NULL,
    patient_id         UUID         NOT NULL REFERENCES clinic_patients(id),
    consultation_id    UUID,
    template_ref       VARCHAR(120),                     -- template name and version, or NULL for a free-typed document
    letterhead_id      UUID,
    issuer_name        VARCHAR(120) NOT NULL,            -- the practice or branch name as printed
    renderer           VARCHAR(60)  NOT NULL,            -- for example itext-7.2.5
    snapshot           JSONB        NOT NULL,            -- the data the document was made from, frozen
    content_sha256     CHAR(64)     NOT NULL,            -- of the PDF bytes
    code_sha256        CHAR(64)     NOT NULL,            -- of the public verification code (the code itself is printed on the document, never stored)
    issued_by          UUID,
    issued_at          TIMESTAMP    NOT NULL DEFAULT now(),
    status             VARCHAR(10)  NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','VOIDED')),
    voided_at          TIMESTAMP,
    voided_by          UUID,
    void_reason        VARCHAR(500),
    CONSTRAINT chk_issued_void CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL AND length(btrim(coalesce(void_reason,''))) >= 5))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_issued_number ON clinic_issued_documents (tenant_id, doc_number);
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_issued_code   ON clinic_issued_documents (code_sha256);
CREATE INDEX IF NOT EXISTS idx_clinic_issued_patient      ON clinic_issued_documents (tenant_id, patient_id, issued_at DESC);

-- Nothing about an issued document can change except that it can be voided, once.
CREATE OR REPLACE FUNCTION clinic_issued_documents_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'clinic_issued_documents rows cannot be deleted';
    END IF;
    IF OLD.status = 'VOIDED' THEN
        RAISE EXCEPTION 'a voided document cannot be changed';
    END IF;
    IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.doc_number <> OLD.doc_number OR NEW.doc_type <> OLD.doc_type
       OR NEW.patient_id <> OLD.patient_id OR NEW.consultation_id IS DISTINCT FROM OLD.consultation_id
       OR NEW.template_ref IS DISTINCT FROM OLD.template_ref OR NEW.letterhead_id IS DISTINCT FROM OLD.letterhead_id
       OR NEW.issuer_name <> OLD.issuer_name OR NEW.renderer <> OLD.renderer OR NEW.snapshot <> OLD.snapshot
       OR NEW.content_sha256 <> OLD.content_sha256 OR NEW.code_sha256 <> OLD.code_sha256
       OR NEW.issued_by IS DISTINCT FROM OLD.issued_by OR NEW.issued_at <> OLD.issued_at THEN
        RAISE EXCEPTION 'an issued document can only be voided, not edited';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_issued_documents_guard ON clinic_issued_documents;
CREATE TRIGGER trg_clinic_issued_documents_guard
    BEFORE UPDATE OR DELETE ON clinic_issued_documents FOR EACH ROW EXECUTE FUNCTION clinic_issued_documents_guard();
