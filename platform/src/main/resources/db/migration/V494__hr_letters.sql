-- HR Slice 9: the Letter & Document Generation Centre.
--
--   * Templates per company, written with {{merge fields}}. A company gets the standard set the first time it opens the centre.
--   * A letter is made from a template for one employee (and, where it belongs to one, an employee relations case). It starts as a
--     DRAFT that HR can read and edit, becomes ISSUED when the PDF is made and kept, and can later be VOID. An issued letter's wording
--     and file can never change, so what the employee was given is what is on record.
--   * The employee can confirm receipt of a letter that asks for it. This is a recorded acknowledgement with the fingerprint of the file
--     that was acknowledged; it is not an advanced electronic signature.
--
-- Permissions (given to roles that already hold HR_MANAGE or HR_ER_MANAGE):
--   HR_LETTER_READ    see letters and templates
--   HR_LETTER_MANAGE  change templates
--   HR_LETTER_ISSUE   prepare, issue and void letters

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_LETTER_READ',   'See HR letters and letter templates'),
    (gen_random_uuid(), 'HR_LETTER_MANAGE', 'Change HR letter templates'),
    (gen_random_uuid(), 'HR_LETTER_ISSUE',  'Prepare, issue and void HR letters')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('HR_LETTER_READ', 'HR_LETTER_MANAGE', 'HR_LETTER_ISSUE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_ER_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('HR_LETTER_READ', 'HR_LETTER_ISSUE')
ON CONFLICT DO NOTHING;

CREATE TABLE hr_letter_templates (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id),
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    category         VARCHAR(20)  NOT NULL CHECK (category IN ('EMPLOYMENT', 'DISCIPLINARY', 'GRIEVANCE', 'INCAPACITY', 'GENERAL')),
    subject          VARCHAR(200) NOT NULL,
    body             TEXT         NOT NULL,
    requires_ack     BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    customised       BOOLEAN      NOT NULL DEFAULT FALSE,
    revision         INT          NOT NULL DEFAULT 1,
    updated_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version          BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE hr_letter_counters (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    year      INT  NOT NULL,
    last_no   INT  NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE hr_letters (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants(id),
    number             VARCHAR(20)  NOT NULL,
    template_id        UUID         REFERENCES hr_letter_templates(id),
    template_code      VARCHAR(40)  NOT NULL,
    template_name      VARCHAR(120) NOT NULL,
    template_revision  INT          NOT NULL,
    employee_id        UUID         NOT NULL REFERENCES hr_employees(id),
    case_id            UUID         REFERENCES hr_er_cases(id),
    subject            VARCHAR(200) NOT NULL,
    body               TEXT         NOT NULL,
    status             VARCHAR(8)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ISSUED', 'VOID')),
    requires_ack       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    issued_on          DATE,
    issued_at          TIMESTAMPTZ,
    issued_by          UUID,
    file_key           VARCHAR(500),
    file_name          VARCHAR(200),
    file_sha256        VARCHAR(64),
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    UUID,
    voided_at          TIMESTAMPTZ,
    voided_by          UUID,
    void_reason        VARCHAR(500),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, number),
    CHECK (status <> 'ISSUED' OR (file_key IS NOT NULL AND file_sha256 IS NOT NULL AND issued_on IS NOT NULL))
);
CREATE INDEX hr_letters_employee ON hr_letters (tenant_id, employee_id, created_at DESC);
CREATE INDEX hr_letters_case ON hr_letters (case_id) WHERE case_id IS NOT NULL;

-- Once issued, what the employee was given cannot change. Only the acknowledgement and a later voiding may be written.
CREATE FUNCTION hr_letters_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'An issued letter cannot be deleted. Void it instead.';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' THEN
        IF NEW.number IS DISTINCT FROM OLD.number OR NEW.employee_id IS DISTINCT FROM OLD.employee_id
           OR NEW.case_id IS DISTINCT FROM OLD.case_id OR NEW.subject IS DISTINCT FROM OLD.subject
           OR NEW.body IS DISTINCT FROM OLD.body OR NEW.file_key IS DISTINCT FROM OLD.file_key
           OR NEW.file_sha256 IS DISTINCT FROM OLD.file_sha256 OR NEW.issued_on IS DISTINCT FROM OLD.issued_on
           OR NEW.issued_by IS DISTINCT FROM OLD.issued_by OR NEW.requires_ack IS DISTINCT FROM OLD.requires_ack THEN
            RAISE EXCEPTION 'An issued letter cannot be changed. Void it and make a new one.';
        END IF;
        IF OLD.status = 'VOID' AND NEW.status <> 'VOID' THEN
            RAISE EXCEPTION 'A voided letter cannot be brought back.';
        END IF;
        IF OLD.acknowledged_at IS NOT NULL AND NEW.acknowledged_at IS DISTINCT FROM OLD.acknowledged_at THEN
            RAISE EXCEPTION 'The acknowledgement cannot be changed.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_letters_guard_trg BEFORE UPDATE OR DELETE ON hr_letters
    FOR EACH ROW EXECUTE FUNCTION hr_letters_guard();

CREATE TABLE hr_letter_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    letter_id   UUID         NOT NULL REFERENCES hr_letters(id),
    action      VARCHAR(30)  NOT NULL,
    note        VARCHAR(1000),
    done_by     UUID,
    done_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX hr_letter_events_letter ON hr_letter_events (letter_id, done_at);

CREATE FUNCTION hr_letter_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The letter history cannot be changed or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_letter_events_no_update BEFORE UPDATE OR DELETE ON hr_letter_events
    FOR EACH ROW EXECUTE FUNCTION hr_letter_events_immutable();
