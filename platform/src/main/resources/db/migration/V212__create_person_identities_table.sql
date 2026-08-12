-- Backs PersonIdentityService — a shared-identifier resolution capability,
-- NOT a shared person entity. See HandyFlow BOS Discovery doc, Section
-- 22.3: modules keep their own tailored person records (HrEmployee,
-- SecurityGuard, RecApplicant, etc.) — this table only lets them ask
-- "is this the same human as that record over there" via a common
-- reference id, without merging their actual business data.
--
-- Scoped per tenant (id_number is only unique within one tenant's own
-- workforce, not globally) — this is NOT a cross-tenant person registry.

CREATE TABLE person_identities (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    id_number     VARCHAR(20),   -- SA ID number — strongest natural key when present
    email         VARCHAR(255),
    phone         VARCHAR(50),
    full_name     VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Partial unique index: id_number is the strong match key WHEN present,
-- but is nullable (some records — e.g. a just-applied recruitment
-- candidate — may not have captured it yet), so a plain UNIQUE constraint
-- would incorrectly reject multiple genuinely-different people who both
-- happen to have a null id_number.
CREATE UNIQUE INDEX uq_person_identity_tenant_idnum
    ON person_identities (tenant_id, id_number)
    WHERE id_number IS NOT NULL;

CREATE INDEX idx_person_identity_tenant_email ON person_identities (tenant_id, email);