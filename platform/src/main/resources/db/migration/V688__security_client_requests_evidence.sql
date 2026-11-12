-- Guard app: incident photos are sent with their own client id so a retried upload does not attach twice.
-- Extends the kind check from V687 (constraint name is the Postgres default for an inline column check).
ALTER TABLE security_client_requests DROP CONSTRAINT IF EXISTS security_client_requests_kind_check;
ALTER TABLE security_client_requests
    ADD CONSTRAINT security_client_requests_kind_check CHECK (kind IN ('SCAN', 'INCIDENT', 'DURESS', 'EVIDENCE'));
