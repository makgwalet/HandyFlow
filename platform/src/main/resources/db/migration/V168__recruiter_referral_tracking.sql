-- V__PLACEHOLDER5__recruiter_referral_tracking.sql
-- RENAME: replace __PLACEHOLDER5__ with your real next Flyway version.
-- Independent of all prior recruiter migrations this session — alters
-- rec_applications, doesn't touch rec_interviews/rec_jobs.
--
-- WHY referrer_name (free text) SEPARATE from referred_by_user_id (a real
-- FK)? A public applicant filling in the careers-page form has no way to
-- know internal user IDs — they can only type a name. That's unverified
-- and not enough on its own to trigger a bonus payout, so it's kept
-- separate from referred_by_user_id, which only gets set once staff
-- confirms/links the referral to a real employee record.

ALTER TABLE rec_applications
    ADD COLUMN referrer_name            VARCHAR(255),
    ADD COLUMN referred_by_user_id      UUID REFERENCES users(id),
    ADD COLUMN referral_bonus_amount    NUMERIC(12,2),
    ADD COLUMN referral_bonus_status    VARCHAR(20) DEFAULT 'NOT_SET'
        CHECK (referral_bonus_status IN ('NOT_SET','PENDING','APPROVED','PAID')),
    ADD COLUMN referral_bonus_paid_at   TIMESTAMP;