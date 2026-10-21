-- V458: pairs of customers a person has marked "not a duplicate", so the duplicate finder stops listing them.
-- pair_key is the two customer ids in sorted order joined by '|' (see DuplicateMatcher.pairKey).
CREATE TABLE customer_duplicate_dismissals (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    pair_key      VARCHAR(80)  NOT NULL,
    customer_a    UUID         NOT NULL,
    customer_b    UUID         NOT NULL,
    dismissed_by  UUID,
    dismissed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_duplicate_dismissals UNIQUE (tenant_id, pair_key)
);
