# Fuel Phase 3: Distribution controls (patches 0771 backend, 0772 web)

Apply with `git am -3`. Migration **V660** (next free is V661). Nothing was pushed.

## 1. What was implemented
Phase 3 in the plan is C01 to C05. I built the two P0 items, C01 and C02. C03, C04 and C05 are not built (section 7).

- **C02 Delivery variance.** For a delivered, non-reversed load with a recorded loading, the system compares litres loaded with litres delivered. If the difference exceeds the company's **variance tolerance** (a percentage of litres loaded) it raises a variance, in either direction. **There is no default tolerance**: until a company sets one, nothing is flagged, and the Load variances page says so. A reviewer (`FUEL_VARIANCE_REVIEW`) can mark it investigating and close it as EXPLAINED or WRITTEN_OFF with a note. **A decision is a record only. It does not change stock, the delivery or any invoice.** Detection runs when the page is opened and looks back 90 days; it is idempotent per delivery.
- **C01 Proof-of-delivery evidence.** A delivery can have evidence records of type PHOTO, SIGNATURE, DOCUMENT or GPS (latitude, longitude, optional accuracy). Records are never edited or deleted. A mistaken unverified record can be replaced (a reason is required, the old one is kept as "Replaced"). A person other than the capturer, with `FUEL_EVIDENCE_VERIFY`, can verify it, which locks it permanently. Office staff (FUEL_MANAGE) can add evidence to any delivery that is on the road or delivered; a driver can add it only to their own load (another driver's load looks missing). **Files are not uploaded by this feature: it stores the link or reference you give it.**

## 2. Existing functionality preserved
- No change to how deliveries are completed, receipted, invoiced or reversed; `complete()` and the signature fields are untouched.
- No change to stock. Variance review never writes anything off by itself.
- Changed response shape: `GET/PUT /settings/controls` gained `varianceTolerancePct` (request field is optional in JSON; **an older client that omits it will clear the tolerance**).
- Division rules: `/variances` and `/evidence` are Wholesale or Distribution routes, like deliveries.

## 3. Database (V660, additive)
`fuel_settings.variance_tolerance_pct` (nullable, 0 to 100); `fuel_delivery_variances` (one per delivery, closed rows must carry decision, note and reviewer); `fuel_delivery_evidence` (GPS needs coordinates, others need a file reference, coordinates range-checked, a verified record cannot be marked replaced). Permissions `FUEL_VARIANCE_REVIEW` (FINANCIAL) and `FUEL_EVIDENCE_VERIFY` (SENSITIVE), granted to ADMIN.

## 4. API
`GET /variances` (FUEL_READ or VARIANCE_REVIEW), `POST /variances/{id}/investigate|close` (VARIANCE_REVIEW); `GET/POST /deliveries/{id}/evidence` (READ / MANAGE); `GET/POST /driver/loads/{id}/evidence` (FUEL_DRIVER or MANAGE); `POST /evidence/{id}/supersede` (MANAGE); `POST /evidence/{id}/verify` (EVIDENCE_VERIFY).

## 5. Web
Load variances page (Logistics group), a "Proof of delivery" button on in-transit and delivered deliveries (add, replace, verify; the capturer is never shown Verify), and a variance tolerance field in Settings, Approvals and controls. The driver's "My loads" page does **not** yet have a button to attach evidence (the driver API exists).

## 6. Verification: what was and was not run
- **Java is parse-only. Maven has not been run**, including the new tests: `FuelDistributionControlsTest` (10) and `FuelDistributionControlsServiceTest` (11). `FuelDivisionRulesTest` was extended for the new paths.
- **V660 run twice in a scratch Postgres 16**: idempotent; out-of-range tolerance, a closed variance without a decision, a duplicate variance for one delivery, GPS without coordinates, a file record without a file, latitude 95 and a verified-but-replaced record are all rejected; both permissions exist.
- **Web: vitest 227 pass** across `src/pages/fuel` and `src/navigation`; `tsc` clean.
- Not tested: any controller, security or cross-tenant test with MockMvc (gap H01); the HR-linked driver path against a real HR module; `ArchitectureVerificationTest`.

## 7. Not done, and why
- **C03 tanker to Fleet vehicle:** waiting for your decision (is `fuel_tankers` meant to stay a Fuel table?).
- **C04 routes, multi-stop, windows, failed delivery, returns:** P2, and a new domain.
- **C05 offline queue with attachments:** depends on a file-upload path for evidence that does not exist yet.
- Driver-app button for evidence; a requirement that a delivery has verified proof before it can be invoiced (not added: it would change existing invoicing behaviour).

## 8. Risks
- Variances are detected on page open, so a tenant that never opens the page sees nothing raised and gets no alert. A scheduled check with a notification is the obvious next step.
- A tolerance chosen too low floods the list; too high hides losses. The figure is yours to set.
- Evidence references are free text: nothing checks that the file exists.
- Only ADMIN holds the two new permissions until granted.
