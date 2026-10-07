-- Guard competencies (first aid, firearm competency, driver...): issue and expiry dates, certificate
-- evidence (stored through the shared evidence module), and a named verifier.
-- `required` is chosen per guard, e.g. firearm competency for an armed guard.

CREATE TABLE security_guard_competencies (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    guard_id          UUID NOT NULL REFERENCES security_guards(id),
    competency_type   VARCHAR(40) NOT NULL
        CHECK (competency_type IN (
            'FIREARM_COMPETENCY', 'FIRST_AID', 'FIREFIGHTING', 'DRIVER', 'CLOSE_PROTECTION',
            'VIP_PROTECTION', 'CONTROL_ROOM', 'CCTV', 'ACCESS_CONTROL', 'CANINE',
            'MINING_SECURITY', 'TACTICAL_RESPONSE', 'OTHER')),
    title             VARCHAR(200),
    issued_by         VARCHAR(200),
    issue_date        DATE,
    expiry_date       DATE,
    certificate_ref   VARCHAR(200),
    required          BOOLEAN NOT NULL DEFAULT FALSE,
    notes             VARCHAR(1000),
    verified_by       UUID,
    verified_by_name  VARCHAR(200),
    verified_at       TIMESTAMPTZ,
    verification_note VARCHAR(500),
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    CHECK (expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date)
);

CREATE INDEX idx_guard_competencies_guard
    ON security_guard_competencies (tenant_id, guard_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_guard_competencies_expiry
    ON security_guard_competencies (expiry_date)
    WHERE deleted_at IS NULL AND expiry_date IS NOT NULL;

COMMENT ON TABLE security_guard_competencies IS
    'Skills and certifications a guard holds, with evidence (evidence module, entity type GuardCompetency), expiry and verifier. Soft delete only.';
