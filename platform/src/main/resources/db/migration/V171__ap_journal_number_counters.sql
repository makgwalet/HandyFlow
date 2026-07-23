-- V__PLACEHOLDER_ap_journal_number_counters.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- WHY a dedicated counter table instead of a Postgres SEQUENCE? A native
-- SEQUENCE is global — numbers would count up across all tenants, not
-- restart at 1 per tenant. This table keeps each tenant's AP-/PAY- numbers
-- independent while still being atomic under concurrent access, via the
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING pattern in
-- ApService.nextJournalNumber() — that single statement is what Postgres
-- guarantees is safe under concurrency, not any application-level locking.
--
-- prefix is 'AP' (bill approval journals) or 'PAY' (payment journals) —
-- kept as separate counters per prefix, not one shared counter, so
-- AP-000001 and PAY-000001 can both exist without colliding.

CREATE TABLE ap_journal_number_counters (
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    prefix      VARCHAR(10) NOT NULL,
    next_value  INT         NOT NULL DEFAULT 1,

    CONSTRAINT pk_ap_journal_number_counters PRIMARY KEY (tenant_id, prefix)
);