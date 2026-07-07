-- Prevents the expiry-reminder job from re-sending the same reminder every
-- night for the whole 3-day warning window. Without this flag, a quote
-- sitting at "3 days to expiry" would email the client and staff once per
-- night until it actually expires or is acted on.
ALTER TABLE quotes
    ADD COLUMN expiry_reminder_sent_at TIMESTAMP;