-- V280__post_orders.sql
-- Post Orders / My Post, per the product owner's own explicit design —
-- "I think this is the one I'd prioritize for the actual guard
-- experience... A security guard shouldn't open an app and see only:
-- Start shift -> Scan checkpoint -> Finish shift." Site + Post
-- hierarchy, versioned orders, guard acknowledgment, reusable security
-- contacts — exactly the model the product owner specified.

-- Reusable across every post order at a site, per the product owner's
-- own explicit instruction: "don't put emergency contacts exclusively
-- inside Post Orders... if the client's control-room number changes,
-- you don't have to edit 15 different post orders."
CREATE TABLE security_contacts (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    site_id      UUID REFERENCES security_sites (id), -- nullable: a tenant-wide contact (e.g. "Police") isn't scoped to one site; a "Site Manager" contact is
    name         VARCHAR(150) NOT NULL,
    role         VARCHAR(50)  NOT NULL, -- SITE_MANAGER | CLIENT_CONTACT | SECURITY_MANAGER | CONTROL_ROOM | POLICE | AMBULANCE | FIRE | OTHER
    phone        VARCHAR(30),
    email        VARCHAR(150),
    active       BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_security_contacts_tenant ON security_contacts (tenant_id) WHERE active = true;
CREATE INDEX idx_security_contacts_site ON security_contacts (tenant_id, site_id) WHERE active = true;

-- "A site can have multiple posts... the guard assigned to Main Gate
-- shouldn't necessarily see the same instructions as the Control Room
-- officer" — a specific guard-post location within a site.
CREATE TABLE security_posts (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    site_id      UUID NOT NULL REFERENCES security_sites (id),
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    active       BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_security_posts_site ON security_posts (tenant_id, site_id) WHERE active = true;

-- FIX: one table for both site-level and post-level orders, per the
-- product owner's own unified PostOrder schema — post_id nullable: null
-- means this is the SITE-level order (general rules, emergency
-- procedures, evacuation, client rules — everyone at the site needs
-- these); set means a POST-level order (duties, opening/closing,
-- visitor procedures — specific to that guard's own location).
-- Versioning is mandatory, per the product owner's own explicit
-- instruction ("Don't simply overwrite instructions") — a new row per
-- version, never an UPDATE to instructions in place. status ACTIVE
-- means "the current one a guard should be reading and acknowledging";
-- publishing a new version supersedes the previous ACTIVE one
-- (effective_to set, status -> SUPERSEDED) rather than deleting it —
-- the history itself is part of the audit trail.
CREATE TABLE security_post_orders (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    site_id                 UUID NOT NULL REFERENCES security_sites (id),
    post_id                 UUID REFERENCES security_posts (id), -- nullable — see comment above
    version                 INTEGER NOT NULL,
    status                  VARCHAR(15) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | SUPERSEDED | ARCHIVED
    effective_from          TIMESTAMPTZ,
    effective_to            TIMESTAMPTZ,
    instructions            TEXT,
    duties                  TEXT,
    emergency_procedures    TEXT,
    restricted_areas        TEXT,
    access_rules            TEXT,
    created_by              UUID,
    published_by            UUID,
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Only one ACTIVE version per (site, post) at a time — post_id can be
-- null (site-level), and two nulls are NOT considered equal for a
-- uniqueness check in Postgres by default, so this partial index
-- correctly still enforces "one active site-level order per site" too.
CREATE UNIQUE INDEX idx_security_post_orders_one_active
    ON security_post_orders (tenant_id, site_id, COALESCE(post_id, '00000000-0000-0000-0000-000000000000'))
    WHERE status = 'ACTIVE';
CREATE INDEX idx_security_post_orders_lookup ON security_post_orders (tenant_id, site_id, post_id, version);

-- Attachments: a simple URL+name list per post order, matching the
-- attachment_url/attachment_name pattern already used elsewhere in this
-- codebase (e.g. ApBill) rather than a new multi-file subsystem —
-- deliberately kept simple since a post order's attachments (a site
-- map, an evacuation diagram) are typically one or two files, not a
-- document library.
CREATE TABLE security_post_order_attachments (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    post_order_id  UUID NOT NULL REFERENCES security_post_orders (id),
    file_url       TEXT NOT NULL,
    file_name      VARCHAR(300) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_order_attachments_order ON security_post_order_attachments (tenant_id, post_order_id);

-- Referencing reusable SecurityContacts from a post order — a join
-- table, not a duplicated copy, so updating one SecurityContact's phone
-- number is reflected everywhere it's referenced, per the product
-- owner's own explicit reasoning for wanting contacts reusable at all.
CREATE TABLE security_post_order_contacts (
    post_order_id UUID NOT NULL REFERENCES security_post_orders (id),
    contact_id    UUID NOT NULL REFERENCES security_contacts (id),
    PRIMARY KEY (post_order_id, contact_id)
);

-- "That creates evidence." — per-guard, per-version acknowledgment,
-- with the offline/device/sync fields the product owner's own data
-- model specified exactly (deviceId, syncStatus).
CREATE TABLE security_post_order_acknowledgements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    post_order_id   UUID NOT NULL REFERENCES security_post_orders (id),
    version         INTEGER NOT NULL, -- denormalized on purpose: the post order row it points to could later be superseded/archived, but this acknowledgement must always show which exact version was actually read, unaffected by that
    guard_id        UUID NOT NULL REFERENCES security_guards (id),
    acknowledged_at TIMESTAMPTZ NOT NULL,
    device_hardware_id VARCHAR(200),
    acknowledged_offline BOOLEAN NOT NULL DEFAULT false,
    synced_at       TIMESTAMPTZ, -- null while acknowledged_offline = true and not yet synced; set once the sync queue delivers it, matching this session's own Agriculture/offline-sync precedent
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_order_ack_guard ON security_post_order_acknowledgements (tenant_id, guard_id);
-- Backs "has this guard acknowledged the CURRENT version of their post
-- order" — the single most common query this table exists to answer.
CREATE INDEX idx_post_order_ack_lookup ON security_post_order_acknowledgements (tenant_id, post_order_id, guard_id);
