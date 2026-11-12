-- C05: a phone that loses signal replays its queued evidence. The request id it made once is kept on the record, so a replay
-- of a request the server already saved is recognised (and the unique index is the backstop if two replays ever race).
ALTER TABLE fuel_delivery_evidence ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_fuel_evidence_client_request ON fuel_delivery_evidence (tenant_id, delivery_id, client_request_id) WHERE client_request_id IS NOT NULL;
