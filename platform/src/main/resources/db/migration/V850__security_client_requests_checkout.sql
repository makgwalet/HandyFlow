-- Guard app: a resource check-out carries a phone-generated id so a retried request does not sign the item out twice.
ALTER TABLE security_client_requests DROP CONSTRAINT IF EXISTS security_client_requests_kind_check;
ALTER TABLE security_client_requests
    ADD CONSTRAINT security_client_requests_kind_check
        CHECK (kind IN ('SCAN', 'INCIDENT', 'DURESS', 'EVIDENCE', 'GATE_ENTRY', 'CHECKOUT'));
