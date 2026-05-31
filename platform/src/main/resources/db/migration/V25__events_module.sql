-- V25__events_module.sql
-- WHY? Event management for SA businesses — conferences, weddings,
-- church events, festivals. Ticket tiers, QR check-in, vendor coordination,
-- post-event survey link.

-- ── Events ────────────────────────────────────────────────────────────────
CREATE TABLE events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    event_number    VARCHAR(20) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    event_type      VARCHAR(30) NOT NULL DEFAULT 'GENERAL'
        CHECK (event_type IN ('CONFERENCE','WEDDING','CHURCH','FESTIVAL',
                              'CORPORATE','COMMUNITY','FUNDRAISER','GENERAL')),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','SOLD_OUT','LIVE',
                          'COMPLETED','CANCELLED')),
    venue_name      VARCHAR(300),
    venue_address   TEXT,
    venue_capacity  INT,
    start_datetime  TIMESTAMP NOT NULL,
    end_datetime    TIMESTAMP NOT NULL,
    timezone        VARCHAR(50) DEFAULT 'Africa/Johannesburg',
    cover_image_url TEXT,
    is_free         BOOLEAN NOT NULL DEFAULT false,
    is_private      BOOLEAN NOT NULL DEFAULT false,
    registration_deadline TIMESTAMP,
    survey_id       UUID,
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    UNIQUE (tenant_id, event_number)
);

-- ── Ticket tiers ───────────────────────────────────────────────────────────
CREATE TABLE event_ticket_tiers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    event_id             UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name                 VARCHAR(100) NOT NULL,
    description          TEXT,
    price                NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency             VARCHAR(3) DEFAULT 'ZAR',
    quantity             INT NOT NULL,
    quantity_sold        INT NOT NULL DEFAULT 0,
    quantity_checked_in  INT NOT NULL DEFAULT 0,
    sale_start           TIMESTAMP,
    sale_end             TIMESTAMP,
    active               BOOLEAN NOT NULL DEFAULT true,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Guests ─────────────────────────────────────────────────────────────────
CREATE TABLE event_guests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    event_id             UUID NOT NULL REFERENCES events(id),
    tier_id              UUID REFERENCES event_ticket_tiers(id),
    customer_id          UUID,
    full_name            VARCHAR(200) NOT NULL,
    email                VARCHAR(200),
    phone                VARCHAR(30),
    company              VARCHAR(200),
    dietary_requirements VARCHAR(200),
    ticket_number        VARCHAR(30) NOT NULL,
    qr_code              VARCHAR(100) NOT NULL UNIQUE,
    status               VARCHAR(20) NOT NULL DEFAULT 'REGISTERED'
        CHECK (status IN ('REGISTERED','CONFIRMED','CHECKED_IN',
                          'CANCELLED','NO_SHOW')),
    checked_in_at        TIMESTAMP,
    checked_in_by        UUID REFERENCES users(id),
    amount_paid          NUMERIC(10,2) DEFAULT 0,
    payment_status       VARCHAR(20) DEFAULT 'PENDING'
        CHECK (payment_status IN ('FREE','PENDING','PAID','REFUNDED')),
    notes                TEXT,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Vendors ────────────────────────────────────────────────────────────────
CREATE TABLE event_vendors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    event_id            UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    vendor_type         VARCHAR(30) NOT NULL
        CHECK (vendor_type IN ('CATERING','AV_TECH','SECURITY','PHOTOGRAPHY',
                               'TRANSPORT','DECOR','ENTERTAINMENT','OTHER')),
    company_name        VARCHAR(200) NOT NULL,
    contact_name        VARCHAR(200),
    contact_phone       VARCHAR(30),
    contact_email       VARCHAR(200),
    service_description TEXT,
    quoted_amount       NUMERIC(10,2),
    confirmed           BOOLEAN NOT NULL DEFAULT false,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Check-in log ───────────────────────────────────────────────────────────
-- WHY immutable? Every scan is recorded regardless of result.
-- Duplicate scans, invalid QRs, all captured for audit.
CREATE TABLE event_check_ins (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    event_id    UUID NOT NULL REFERENCES events(id),
    guest_id    UUID REFERENCES event_guests(id),
    scanned_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    scanned_by  UUID REFERENCES users(id),
    scan_device VARCHAR(100),
    location    VARCHAR(200),
    result      VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'
        CHECK (result IN ('SUCCESS','ALREADY_CHECKED_IN',
                          'CANCELLED_TICKET','NOT_FOUND'))
);

-- ── Indexes ────────────────────────────────────────────────────────────────
CREATE INDEX idx_events_tenant       ON events(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_events_dates        ON events(tenant_id, start_datetime) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_guests_event  ON event_guests(event_id, status);
CREATE INDEX idx_event_guests_qr     ON event_guests(qr_code);
CREATE INDEX idx_event_tiers_event   ON event_ticket_tiers(event_id);
CREATE INDEX idx_event_vendors_event ON event_vendors(event_id);
CREATE INDEX idx_event_check_ins     ON event_check_ins(event_id, scanned_at);