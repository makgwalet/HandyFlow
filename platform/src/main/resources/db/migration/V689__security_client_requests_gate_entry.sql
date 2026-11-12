-- Guard app: a gate arrival carries a client id so a retried tap or a timed-out request does not log the visitor twice.
ALTER TABLE security_client_requests DROP CONSTRAINT IF EXISTS security_client_requests_kind_check;
ALTER TABLE security_client_requests
    ADD CONSTRAINT security_client_requests_kind_check
        CHECK (kind IN ('SCAN', 'INCIDENT', 'DURESS', 'EVIDENCE', 'GATE_ENTRY'));
