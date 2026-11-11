-- Events Chunk 6: payments, refunds and the provider-notification inbox.
-- (This file previously held a Java source file by mistake, so the payments tables were never created.)
--
-- event_payments   one payment attempt per reserved ticket; received_amount is the only basis for refunds/revenue
-- event_refunds    money handed back; a REQUESTED refund reserves its amount against the payment
-- event_payment_events  inbox of provider notifications: unique (provider, provider_event_id) makes callbacks idempotent

CREATE TABLE IF NOT EXISTS event_payments (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    event_id        UUID          NOT NULL REFERENCES events (id),
    guest_id        UUID          NOT NULL REFERENCES event_guests (id),
    amount          NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    received_amount NUMERIC(12,2) CHECK (received_amount IS NULL OR received_amount >= 0),
    fee_amount      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'ZAR',
    provider        VARCHAR(30)   NOT NULL,
    provider_ref    VARCHAR(200),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED','CANCELLED','REQUIRES_REFUND')),
    expires_at      TIMESTAMPTZ   NOT NULL,
    paid_at         TIMESTAMPTZ,
    failure_reason  VARCHAR(500),
    review_reason   VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_payments_refund_within_received
        CHECK (refunded_amount <= COALESCE(received_amount, 0))
);

-- At most one open checkout per guest.
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_payments_open_per_guest
    ON event_payments (guest_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_event_payments_event_status ON event_payments (event_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_payments_guest        ON event_payments (guest_id);
-- The expiry sweep's work list.
CREATE INDEX IF NOT EXISTS idx_event_payments_pending_expiry ON event_payments (expires_at) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS event_refunds (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    event_id          UUID          NOT NULL REFERENCES events (id),
    payment_id        UUID          NOT NULL REFERENCES event_payments (id),
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason            VARCHAR(500)  NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','COMPLETED','FAILED')),
    provider_ref      VARCHAR(200),
    requested_by_name VARCHAR(200),
    requested_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    failure_reason    VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_event_refunds_event   ON event_refunds (event_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_refunds_payment ON event_refunds (payment_id, status);

CREATE TABLE IF NOT EXISTS event_payment_events (
    id                UUID         PRIMARY KEY,
    provider          VARCHAR(30)  NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    payment_id        UUID,
    payload_sha256    VARCHAR(64),
    outcome           VARCHAR(30),
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    CONSTRAINT uq_event_payment_events UNIQUE (provider, provider_event_id)
);
