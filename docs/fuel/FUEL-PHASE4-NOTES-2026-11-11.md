# Fuel Phase 4: Retail cash controls (patches 0773 backend, 0774 web)

Apply with `git am -3`. Migration **V661** (next free is V662). Nothing was pushed.

## 1. What was implemented
Phase 4 in the plan is D01 to D03. I built **D02 (cash controls) only**. D01 and D03 are not built (section 7).

- **Configurable cash tolerance.** The R5.00 constant in `FuelShift` is now a per-company setting (`cash_variance_tolerance`), defaulting to **5.00**, so nothing changes until a company changes it. The tolerance in force is stored on each shift when it closes, so later changes never re-judge old shifts.
- **Fleet-card tender.** A shift's close now takes a `fleetCard` amount, counted with cash, card, account and other. Omitted means 0, so older clients keep working.
- **Refunds, voids and discounts.** Recorded against an **open** shift (REFUND, VOID, DISCOUNT; amount above 0; reason of at least 5 characters). Each stays pending until a supervisor (`FUEL_SHIFT_SUPERVISE`) approves or rejects it. **The person who recorded it can never approve or reject it** (domain rule, a database check, and the screen hides the buttons). The recorder can withdraw their own pending entry. **Pending entries block closing the shift.** Approved entries are taken off the takings the shift is expected to hold: variance = taken - (meter value - approved adjustments). They are records only: nothing is posted to stock, invoicing or accounts.
- **Supervisor review.** A shift that closes outside the tolerance (it already needed a reason) is marked PENDING_REVIEW. A supervisor who is **not the person who closed it** marks it REVIEWED with a note. A record only; the takings and stock are unchanged.
- **Separate closer (off by default).** A setting that stops the person who opened a shift from closing it.

## 2. Existing functionality preserved
- Defaults reproduce the old behaviour: tolerance R5.00, separate-closer off, no fleet card, no adjustments. `FuelShift.close(...)` with the old signature still exists and behaves as before.
- Stock posting on close, reversal, pump meters and the summary are unchanged. The summary's "needing a look" count now uses each shift's own tolerance (same result at R5.00).
- `CloseShiftRequest` keeps a constructor without `fleetCard`. `ShiftResponse` gained fields (fleet card, adjustments, tolerance, review status, note, time).
- `GET/PUT /settings/controls` gained `cashVarianceTolerance` and `requireSeparateCloser`. **Unlike the Phase 3 tolerance, an older client that omits them leaves them unchanged** (null means "leave it").
- Division rules already cover `/forecourt` (Retail), so no rule change.

## 3. Database (V661, additive)
`fuel_settings`: `cash_variance_tolerance NUMERIC(10,2) NOT NULL DEFAULT 5.00` (>= 0), `require_separate_closer BOOLEAN NOT NULL DEFAULT FALSE`. `fuel_shifts`: `fleet_card_amount`, `adjustments_amount` (both default 0, >= 0), `tolerance_applied`, `review_status` (NONE, PENDING_REVIEW, REVIEWED; default NONE), reviewer, time, note (REVIEWED needs reviewer and note). New `fuel_shift_adjustments` with checks on kind, amount, reason length, status, decided-by, and **approver must differ from recorder**. Permission `FUEL_SHIFT_SUPERVISE` (FINANCIAL), granted to ADMIN.

## 4. API (all under `/api/v1/fuel/forecourt`)
`GET /shifts/{id}/adjustments` (read), `POST /shifts/{id}/adjustments` (FUEL_FORECOURT or MANAGE), `POST /adjustments/{id}/approve|reject` (SHIFT_SUPERVISE), `POST /adjustments/{id}/withdraw` (FORECOURT or MANAGE; recorder only), `POST /shifts/{id}/review` (SHIFT_SUPERVISE).

## 5. Web
Forecourt page: a Fleet card field and the company tolerance in the close dialog, approved refunds shown as taken off the expected amount, a warning when entries are pending; a "Refunds, voids and discounts" panel in each expanded shift (record, approve, reject, withdraw); a "Needs review" / "Reviewed" chip and Review button; an "Awaiting supervisor review" stat. Settings, Approvals and controls: cash tolerance and the separate-closer checkbox.

## 6. Verification: what was and was not run
- **Java is parse-only. Maven has not been run**, including the new tests: `FuelRetailControlsTest` (14), `FuelCashControlServiceTest` (9), and 3 added to `FuelForecourtServiceTest` (which also gained a `FuelCashControlService` mock in its setup).
- **V661 run twice in a scratch Postgres 16**: idempotent; defaults 5.00 / false / 0 / NONE on existing rows; a bad kind, zero amount, short reason, self-approved entry, REVIEWED without a reviewer and a negative tolerance are all rejected; the permission exists.
- **Web: vitest 241 pass** across `src/pages/fuel` and `src/navigation` (14 new); `tsc` clean. Existing tests updated: the close payload now includes `fleetCard: 0`; the controls form and request carry two more fields.
- Not tested: MockMvc, security or cross-tenant tests (H01); concurrent approval; `ArchitectureVerificationTest`.

## 7. Not done, and why
- **D01 nozzles and tank-to-pump reconciliation:** it changes the meter model (a pump has several nozzles, each with its own meter and grade) and every existing reading. It needs its own phase and a decision on how to migrate current pumps.
- **D03 POS/pump integration (P2):** only the boundary was asked for. Not written. The natural seam is the close request: a POS would submit the same readings, tenders and adjustments the screen does.
- Cash banking and till drops, shift handover, and per-attendant variance trends.
- Refund and discount amounts are not limited by sales value or by a per-company cap.

## 8. Risks
- A tolerance set high hides shortages, and the review queue only fills for shifts outside it. The figure is the company's to set.
- A supervisor review is a record, not a write-off: shortages are not posted anywhere.
- Approved entries lower the expected takings, so a fraudulent supervisor and recorder pair could hide cash. The control is two different people, not a limit on amounts.
- Only ADMIN holds `FUEL_SHIFT_SUPERVISE` until granted, so on a site where nobody has it, any pending entry will block that shift's close until it is withdrawn.

## 9. Next
Your four open decisions (AP dependency, tanker versus Fleet vehicle, who activates divisions, Truck Stop bookings) are unchanged. Run Maven and `ArchitectureVerificationTest` and send any failures; then Phase 5 (Truck Stop) needs the bookings decision.
