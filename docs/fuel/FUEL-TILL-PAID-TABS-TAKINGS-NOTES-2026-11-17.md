# Till-paid tabs and takings (2026-11-17)

Decision (closed): the tab charge is on the day charged; the payment goes into payment-date takings by method (cash, card, EFT); not counted as revenue twice.

## Built
- V716: tabs can be paid by **EFT** as well as cash, card or other (check constraint replaced in a new migration; V707 untouched). The till "Paid by" list now offers EFT.
- `FuelTabTakingsService` and `GET /api/v1/fuel/truck-stop/tabs/takings?from&to` (read-only, FUEL_READ and similar, max 93 days).
  - A tab paid at the till counts on the day it was **paid** (settled time in the company report timezone), by method.
  - A tab charged to an account is shown on the day it was **charged**, in its own column, and is **not** takings: it is billed by an invoice and collected there, so it is never counted twice.
  - Voided tabs are left out.
- Web: a "Takings paid at the till" section under the tabs list with a date range, per-day methods, ex-VAT total, VAT-inclusive figure, and charged-to-accounts column.
- Tests: by day and method (including 23:30 SAST staying on its own day), the account charge excluded from takings, window edges, bad ranges, EFT; web takings table, EFT option, range guard.

## Limits
- Amounts are ex VAT (tab lines). The VAT-inclusive figure uses today's rate, not the rate on the day.
- Tabs carry no till or shift, and there is no truck stop cash-up yet, so this is a report of what was recorded, not a counted-cash reconciliation. It is **not** added to forecourt shift takings, which are checked against pump meters.
- The day boundary is the company report timezone (Settings > report basis), not a per-site one.
- Tabs settled before this change have no EFT; they keep their recorded method.

Written, not compiled or run (Java).
