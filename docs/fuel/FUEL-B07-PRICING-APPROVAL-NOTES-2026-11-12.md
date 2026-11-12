# Fuel B07 - Price approval and margin guard (2026-11-12)
**Implemented:** a new price can wait for approval when (a) it moves more than a set percentage from the price it replaces, or (b) its margin over the latest purchase cost of that fuel is under a set minimum. Both limits are off until a tenant sets them. Each price keeps what it replaced and the cost it was set against.
**Preserved:** prices are still never edited (end and add). Existing prices are APPROVED, so live pricing is unchanged. Pricing resolution only uses APPROVED prices.
**Files:** V663 migration; FuelPrice, FuelSettings, FuelPriceRepository, FuelReceiptRepository (latestForFuelType), FuelPricingService, FuelApprovalEventHandler, FuelSettingsService, FuelWholesaleDtos, PriceResponse; web PricingTab, priceOps, SettingsTab, wholesaleOps.
**DB (V663, additive):** fuel_prices.approval_status/approval_request_id/approval_reason/previous_price_per_litre/cost_per_litre_at_entry/decided_at/version; fuel_settings.price_change_approval_pct, min_margin_pct (check constraints).
**API:** PriceResponse and controls request/response gained fields; old constructors/clients still work (null = leave unchanged; 0 = switch off).
**Security:** no new endpoints; same FUEL_MANAGE / FUEL_CONFIGURATION_MANAGE guards. The decision is made by the Approvals engine (entity type PRICE, module fuel); a tenant with no approval rule gets straight approval, as for purchase orders.
**Tests:** FuelPriceApprovalTest (11) plus web tests; vitest 247 passed (run here). Java tests are parse-checked only. V663 applied to a scratch PostgreSQL 16 with stubbed parent tables.
**Gaps:** margin is measured against the latest receipt price per litre for that fuel type, not average cost or landed cost; contract-price margin uses the same cost. Pending prices that overlap a new price block it until decided or rejected. No approval rule is created by the system: the tenant configures a PRICE rule in Approvals.
**Next:** G02 outbox/retry.
