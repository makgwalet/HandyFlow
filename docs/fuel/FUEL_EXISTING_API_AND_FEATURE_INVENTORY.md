# Fuel: existing API and feature inventory

About 107 operation endpoints (122 mapping annotations less 15 class-level) across 15 controllers under `fuel/api`. Permission shown is the `@PreAuthorize` authority.

| Controller | Base path | Operations (permission) |
|---|---|---|
| FuelTankController | `/api/v1/fuel` | tanks list/get/create/update/deactivate/forecast/reorder; suppliers CRUD + statement; receipts list, receive, reverse; dispatches list, dispatch, approval status, reverse, usage report; dip readings list/record, reconciliation report; margin report (FUEL_MARGIN_READ); deliveries list/create/complete/dispatch/cancel/reverse/receipt. Reads FUEL_READ, writes FUEL_MANAGE |
| FuelStockController | `/api/v1/fuel` | depots CRUD/deactivate; tank placement; tank adjust (**FUEL_STOCK_ADJUST**); transfers; ledger; reservations create/release/list; availability |
| FuelOrderController | `/api/v1/fuel` | orders list/get/credit-check/create/update/confirm/cancel; create delivery for order; loading record; invoicing queue and invoice. Credit override checked inline against FUEL_CREDIT_OVERRIDE |
| FuelCustomerController | `/api/v1/fuel` | customer accounts CRUD, credit hold, sites add/update/remove |
| FuelPricingController | `/api/v1/fuel/prices` | list, create, end, resolve |
| FuelPurchasingController | `/api/v1/fuel/purchase-orders` | list, match, create, update, place, cancel, close-short, delete |
| FuelSupplierInvoiceController | `/api/v1/fuel/supplier-invoices` | list, get, candidates, create, approve, reject |
| FuelTransportController | `/api/v1/fuel` | tankers CRUD; drivers; allocate; planning; driver loads, dispatch, complete (FUEL_DRIVER or FUEL_MANAGE) |
| FuelForecourtController | `/api/v1/fuel/forecourt` | pumps CRUD; shifts list/get/open/close/reverse; summary (FUEL_READ/FUEL_FORECOURT/FUEL_MANAGE) |
| FuelComplianceController | `/api/v1/fuel/compliance` | list, create, update, renew, delete |
| FuelDashboardController | `/api/v1/fuel/dashboard` | overview; margin and cost-to-serve (FUEL_MARGIN_READ) |
| FuelSettingsController | `/api/v1/fuel/settings` | get (FUEL_READ), put (FUEL_MANAGE) |
| FuelPortalAdminController | `/api/v1/fuel/customer-accounts/{id}/portal-access` | list, invite, revoke |
| FuelPortalAuthController | `/api/v1/fuel/portal/auth` | register, login (public) |
| FuelPortalDataController | `/api/v1/fuel/portal` | accounts, sites, orders, deliveries, order-requests (PORTAL_USER) |

Public facade: `FuelFacade` (`findOwnDispatches`, `findOwnDispatch`) for other modules. Event: `FuelDispatchedToVehicleEvent`. Schedulers: delivery reminders (07:00 and 08:00 SAST) and compliance reminders (07:30 SAST).

Frontend sections (`/fuel/:section?`): dashboard, tanks, stock, receipts, dispatches, customers, orders, pricing, invoicing, planning, driver, deliveries, suppliers, purchase-orders, supplier-invoices, compliance, forecourt, overview, margin, cost-to-serve, settings; portal at `/fuel/portal/*`.
