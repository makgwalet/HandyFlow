-- Webhook retries used to send a placeholder body ({"retry":true,...}) because the original
-- payload was never kept. Keep it while a delivery can still be retried; it is cleared on
-- success and when retries are exhausted (payloads are small and carry no personal data, but
-- there is no reason to keep them longer than needed).
ALTER TABLE security_webhook_deliveries ADD COLUMN IF NOT EXISTS payload_json TEXT;
