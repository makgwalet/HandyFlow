-- src/main/resources/db/migration/V286__tenant_email_signature.sql
--
-- Tenant-branded email signature, phase 1.
--
-- PROBLEM THIS ADDRESSES:
-- EmailTemplates.wrap() (shared) is used by all 46 existing email template
-- methods and is 100% HandyFlow-branded — the header always says
-- "HandyFlow · Your Business Operating System", and none of the 46 methods
-- accept any tenant identity beyond an occasional tenantName used inline in
-- body copy. A tenant's customer receiving a quote/invoice email today has
-- no way to know who at the tenant's business to reply to, or to see the
-- tenant's own contact details, the way the original brief's example
-- ("Kind regards, Thabang Makgwale, Managing Director, FastPrint
-- Solutions...") describes.
--
-- SCOPE OF THIS PHASE: additive only. This does not change
-- EmailTemplates.wrap() or any of the other 45 template methods — doing
-- that in one pass would mean touching every call site across every module
-- that sends email, with no way to verify all of them in one session. This
-- migration adds the data a signature needs; a reference migration on ONE
-- real customer-facing template (quoteSentToClient, in QuoteService) proves
-- the pattern. The remaining templates are tracked as backlog (see
-- PLATFORM-ENGINES-PROGRESS.md) — the same phased approach already used for
-- the Tenant Numbering Engine and PDF skill work.

CREATE TABLE tenant_email_signature (
    tenant_id     UUID         NOT NULL,
    display_name  VARCHAR(120),   -- e.g. "Thabang Makgwale"
    job_title     VARCHAR(120),   -- e.g. "Managing Director"
    phone         VARCHAR(40),
    email         VARCHAR(255),
    website       VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT false, -- opt-in: existing tenants get no visible change until they configure and enable this
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_email_signature PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_email_signature_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

COMMENT ON TABLE tenant_email_signature IS
    'Per-tenant sign-off block appended to outbound customer-facing emails '
    'that have been migrated to use it (see TenantEmailBrandingFacade). '
    'A missing row, or enabled = false, means no signature is appended — '
    'the email renders exactly as it did before this table existed.';
