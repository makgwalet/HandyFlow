# Fuel: forecourt supply reconciliation (2026-11-16)

Written, not compiled or run (Java). Web tests pass; tsc checked.

## What it is
Read-only report, `GET /api/v1/fuel/forecourt/supply-report/reconciliation?days=` (under /forecourt, so the Retail division rule applies). Litres only, so no margin permission.
Per forecourt (a depot whose tanks have active pumps) for the period it shows litres in from: the depot recorded as its supplier, any other depot, and supplier receipts; the total; the share from the supplying depot; and litres sold on closed shifts. Depot limits filter the forecourts.
Shown in the Group report tab under "Forecourt sales by supplying depot".

## What it is not
It is not a sent-versus-received check. A transfer is one record, so both sides always match, and dispatches carry no forecourt link. It answers: does the fuel really arrive from the supplier on record?
Total in is not expected to equal litres sold (stock level changes, dip adjustments); both are shown side by side only.

## Rules
- Transfers inside one depot are ignored. Receipts that are deleted or reversed are excluded.
- A forecourt with no supplying depot shows "Not set" and a warning once fuel has arrived.
- Nothing is stored; no migration.

## Files
Service `FuelForecourtSupplyReconciliationService`; DTOs `Inbound`, `Reconciliation`; repo queries `FuelTransferRepository.findBetween`, `FuelReceiptRepository.findReceivedBetween`; web `ForecourtReconciliationSection.tsx`, `forecourtSupplyOps.ts`.
