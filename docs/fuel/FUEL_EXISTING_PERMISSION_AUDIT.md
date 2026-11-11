# Fuel: existing permission audit

Permissions seeded by migrations (grep of `'FUEL_*'`): FUEL_READ, FUEL_MANAGE, FUEL_STOCK_ADJUST, FUEL_MARGIN_READ, FUEL_FORECOURT, FUEL_DRIVER, FUEL_CREDIT_OVERRIDE. Also found in migrations once each: FUEL_DISPATCH, FUEL_RETAILER, FUEL_SPILL (purpose not established in this audit). `FUEL_ADMIN` appears twice in Java; its meaning was not traced.

| Permission | Where enforced | Notes |
|---|---|---|
| FUEL_READ | every read endpoint | single read permission for all data including prices and supplier data |
| FUEL_MANAGE | all writes (72 references in Java) | `SENSITIVE`, no second person (V590). One permission covers pricing, POs, supplier invoice approval, shift reversal, settings |
| FUEL_STOCK_ADJUST | tank adjust | single person |
| FUEL_MARGIN_READ | margin, cost-to-serve | separate from FUEL_READ, good |
| FUEL_FORECOURT | shift open/close | attendant role |
| FUEL_DRIVER | driver loads | driver mobile |
| FUEL_CREDIT_OVERRIDE | inline authority check in `FuelOrderController` | not an annotation |

Findings: (1) writes are coarse; the brief's granular set (approve adjustment, review variance, shift approve, licence manage) does not exist. (2) No site, entity or division scope on any permission. (3) No maker-checker anywhere in Fuel except the dispatch approval via `ApprovalFacade`. (4) Tenant scoping is done in service queries via `TenantId`; one repository (dip readings) filters by tank id only (GAP-B03).
