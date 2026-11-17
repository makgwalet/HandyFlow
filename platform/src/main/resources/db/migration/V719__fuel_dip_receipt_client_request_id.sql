-- Mobile replay safety: the phone makes one id per dip reading / receipt and sends it again after a lost reply.
ALTER TABLE fuel_dip_readings ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_fuel_dip_client_request ON fuel_dip_readings (tenant_id, tank_id, client_request_id) WHERE client_request_id IS NOT NULL;

ALTER TABLE fuel_receipts ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_fuel_receipt_client_request ON fuel_receipts (tenant_id, tank_id, client_request_id) WHERE client_request_id IS NOT NULL;
