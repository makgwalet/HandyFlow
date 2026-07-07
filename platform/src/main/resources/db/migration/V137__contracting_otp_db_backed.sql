-- V___contracting_otp_db_backed.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Replaces the two in-memory ConcurrentHashMaps that only worked correctly
-- on a single instance:
--   OtpService.hashStore            -> contract_otp_verifications
--   ContractingService.otpRateStore -> contract_otp_rate_limits
--
-- Both are keyed directly by party_id (one active row per party, matching
-- the original maps' "newest replaces oldest" behavior) rather than having
-- a separate surrogate id — there's never a reason to query either table by
-- anything else.

CREATE TABLE contract_otp_verifications (
    party_id    UUID        NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_contract_otp_verifications PRIMARY KEY (party_id)
);

-- Backs the expiry check in OtpService.verify()/generateAndStore() — lets a
-- periodic cleanup job (optional, see note below) find expired rows cheaply
-- without a full table scan.
CREATE INDEX idx_otp_verifications_expiry ON contract_otp_verifications (expires_at);

CREATE TABLE contract_otp_rate_limits (
    party_id       UUID      NOT NULL,
    request_count  INT       NOT NULL DEFAULT 0,
    fail_count     INT       NOT NULL DEFAULT 0,
    window_start   TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT pk_contract_otp_rate_limits PRIMARY KEY (party_id)
);

-- OPTIONAL CLEANUP NOTE: neither table is ever actually queried by anything
-- other than party_id (its own primary key), and both are self-limiting in
-- size — there's at most one row per party that has ever requested an OTP,
-- and rows are actively deleted on successful/expired verification in
-- OtpService. A periodic DELETE of stale rows (e.g. anything older than a
-- day) is a reasonable defensive addition later if this ever needs it, but
-- isn't required for correctness — unlike Redis's automatic TTL expiry,
-- rows here just sit unused rather than causing any actual problem if
-- cleanup is skipped.
