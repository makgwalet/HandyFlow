-- Generic, reusable rate-limit tracking table. Same shape as
-- contract_otp_rate_limits, generalized: a free-form String key instead of
-- a UUID partyId, so one table + one mechanism covers login, registration,
-- and any future public endpoint (Clinic/Projects/Desk/Recruiter/Creative
-- public portals — see HandyFlow BOS Discovery doc, Section 19.3/20.1)
-- rather than a new table per endpoint.
--
-- Key convention: "<scope>:<identifier>", e.g. "auth:login:192.168.1.1",
-- "auth:register:192.168.1.1". Scope prefix keeps different endpoints'
-- counters from colliding even if the same IP hits multiple limited
-- endpoints in the same window.

CREATE TABLE rate_limits (
    rate_key      VARCHAR(255) PRIMARY KEY,
    request_count INTEGER      NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Periodic cleanup of stale rows is worth adding as a scheduled job once
-- this is live (rows for IPs that stop making requests never get deleted
-- otherwise) — not included in this migration since it's an operational
-- concern, not a correctness one: a stale row just means one extra table
-- row, never a wrong rate-limit decision (isWindowExpired() below always
-- checks the actual timestamp regardless of row age).