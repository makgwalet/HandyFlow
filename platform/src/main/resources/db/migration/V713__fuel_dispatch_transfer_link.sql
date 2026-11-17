-- Forecourt replenishment (decision 11): a supply run from a depot into a forecourt tank is ONE stock transfer linked to the dispatch.
-- The dispatch records the run (litres, levels, cost, review); the transfer moves the stock. The dispatch posts no stock line of its own, so nothing counts twice.
ALTER TABLE fuel_dispatches ADD COLUMN transfer_id UUID REFERENCES fuel_transfers (id);
CREATE UNIQUE INDEX uq_fuel_dispatches_transfer ON fuel_dispatches (transfer_id) WHERE transfer_id IS NOT NULL;

-- A transfer made for a dispatch is undone by reversing the dispatch. Reversed transfers drop out of the supply reconciliation.
ALTER TABLE fuel_transfers ADD COLUMN reversed_at TIMESTAMPTZ;
