# Fuel: data screens, dashboard tiles, visual check, stay decision email (2026-11-18)

## What was built

**Revenue and sales by division** (`GET /api/v1/fuel/division-sales?days=30`, tab "Sales by division").
One card per division the company runs and the reader may see. Current period against the equal period before it, daily revenue line, share of revenue.
What counts as a sale: Distribution = delivered, not-reversed loads at the invoiced amount; Retail forecourt = closed shifts at the pump-reading sales value, dated as the forecourt reports are; Wholesale supply = dispatches billed to a customer (price set, not a transfer); Truck stop = stays checked out in the period at the nightly rate for the nights stayed, before VAT. Money fields are null without `FUEL_MARGIN_READ`. Tank-based figures count only the tanks the person may see.

**Tanker map** (tab "Tanker map"). Tankers joined to their linked Fleet vehicle's last position and to today's loads. Shows why a tanker is not on the map (no Fleet link, linked but no tracking, no position). Without Fleet access it still lists tankers and loads and says why there is no map.

**Tanker compartments** (`GET /api/v1/fuel/tankers/{id}/compartment-loads?date=`, tab "Tanker compartments"). Each compartment filled to what is planned in it for the day, with rated products, last cargo and last cleaned. Flags over capacity, mixed fuels in one compartment, and a fuel the compartment is not rated for.

**Delivery timeline** (`GET /api/v1/fuel/deliveries/{id}/execution`, "Timeline" button on Deliveries and on each variance). Scheduled, loaded, arrival checks (including blocked ones), proof captured, failed attempts, delivered, receipt, reversal, in time order, with notes where loaded, delivered and the meter disagree. The time a driver pressed Start is not stored on the load, so there is no event for it.

**Variance investigation** (Variances tab). Totals (short and over kept apart, written off), where variances cluster by driver or vehicle (repeats marked, with a line saying a pattern is a reason to look, not a finding), status and search filters, and the timeline from each variance.

**Dashboard tiles**: retail takings for 7 days and truck stop yard. Hidden when the company does not run the division or the person has no access (401/403/404); a real failure shows "Could not load".

**Customer notification when staff decide a stay request**: email to the address stored on the request (`FUEL_STAY_DECIDED`), after commit, failures only logged. Nothing is sent when approval is refused, or when the request has no email.

## Visual check (seeded data, not live data)
Real web app in Chromium, API answered with made-up fixtures, 12 sections x light / dark / phone (390 px). Script: `handyflow-web/scripts/fuel-visual-check.cjs`. Map tiles do not load in the sandbox (grey map, marker visible). It found and fixed:
1. Dashboard tiles read `response.data.data`, but the shared client already removes the envelope, so they would have shown "-" against the real backend. My unit tests mocked the old shape and hid it. Now accepts both, with a test using the real shape.
2. KPI tile sparkline sat on top of the tile label. Moved to the bottom right.
3. Empty-state icon off-centre on Deliveries, Dispatches, Receipts, Tanks, Suppliers (svg is block level). The same pattern exists in about 90 files outside Fuel and was not changed.
4. Tanks showed its stats twice (old plain row above the new tiles). Old row removed.
5. Order number, money and litres wrapped onto two lines in Orders, Customers and Planning. No-wrap added.
6. Deliveries action buttons and filter chips overflowed on a phone; they wrap now. Variances cluster table scrolls sideways on a phone.
7. Dark mode is chosen by the server UI preference, not the browser setting, so the first dark pass was invalid and was redone with a dark preference.

## Not done / limits
- Nothing was run against a real backend, a real database, or a real Fleet device feed.
- Java was never compiled (no Maven here). Only a syntax check with unresolved symbols filtered out. Run `mvn test` for the new tests: FuelDivisionSalesServiceTest, FuelDeliveryExecutionServiceTest, additions to FuelTransportServiceTest and FuelStayRequestServiceTest.
- "Reconciliation" means the existing forecourt supply reconciliation (unchanged) plus the variance investigation above. There is no new per-tank dip-versus-book reconciliation screen.
- Sales by division uses closed shifts' pump readings for the forecourt, so a forecourt that has not imported or closed shifts shows less than it sold.
- The compartment view shows litres as planned at allocation, not as loaded.
