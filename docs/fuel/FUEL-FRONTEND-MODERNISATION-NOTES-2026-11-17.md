# Fuel frontend modernisation (2026-11-17)

Goal: bring the Fuel module's look in line with the HandyFlow Fuel mockups — gradient KPI tiles, navy-header panels, status pills, ring gauges — without rewriting 40 tabs.

## What changed
- **`pages/fuel/fuelUi.tsx` (new kit):** `KpiTile`/`KpiGrid` (gradient, optional sparkline), `Panel` (navy header, action slot), `PanelLink`, `StatusPill` (maps statuses to tones), `RingGauge`, `StepTracker`, `ProgressBar`, `EmptyState`, `ActionTile`. All colours are theme tokens, so light and dark both work.
- **`styles/fuel.css` (new):** scoped to `.hf-fuel`. Tables get a quiet uppercase header band, hover rows and tabular numbers; fields and buttons get a consistent focus ring; tiles lift on hover; sections fade in once; small-screen padding; `prefers-reduced-motion` respected. This is what modernises every other Fuel tab at once.
- **`SectionedModulePage`:** the section panel carries class `hf-fuel` (Fuel only) and a soft shadow. Other modules are unchanged.
- **`FuelDashboard` rebuilt** as a command centre: four KPI tiles, ring-gauge tank levels, alerts and exceptions (low tanks, negative variance, overdue deliveries), ongoing deliveries with status pills and a step tracker, this-month summary, quick actions.
- **`dashboardOps.ts` (new, tested):** the dashboard figures as pure functions. Months are matched on the local calendar, not on the UTC date string, so a late-night dispatch lands in the right month.
- **`OverviewTab`:** same content and text, now with KPI tiles, panels, attention list and a fill bar per fuel.

## Checked
- `tsc -b` clean; Fuel and component tests pass (679 tests across 95 files, run before a final cosmetic sparkline tweak).
- Visual check in light, dark and phone width on sample data.

## Not done / next
- Individual tabs still use their own inline cards and buttons; they pick up the table, focus and motion polish but not the new panels. Convert the busiest first: Deliveries, Dispatches, Tanks, Orders, Planning.
- Dashboard figures use the existing lists (200 rows each); a server-side summary endpoint would be more exact for large tenants.
- Mockup items not built because the data does not exist yet: revenue and sales-by-division tiles, live fleet map, tanker compartment loading view, delivery execution screen with live tracking.
- Re-check in a real browser against live data; the visual check used seeded sample data.
