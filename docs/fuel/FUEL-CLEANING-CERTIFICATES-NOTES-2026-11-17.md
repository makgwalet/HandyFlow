# Fuel: cleaning certificates (decision 8) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests and tsc pass. Migration V714.

## Decision 8 (closed)
Uploaded evidence via the shared Document/Evidence service, verified; block incompatible loading until present.

## The shared service
The same file store (`FileStorageService`) and the same checks as licence evidence and delivery evidence: the type is decided from the bytes (JPEG, PNG, WebP or PDF), the size is capped, the name is cleaned, and a failure after storing removes the orphaned file.

## How it works
- Each cleaning can have **one certificate file**, uploaded after the cleaning is recorded. It is never replaced; a wrong one is corrected by recording another cleaning (cleanings were already append-only).
- A **second person verifies** it (permission FUEL_COMPLIANCE_VERIFY). Not the person who recorded the cleaning and not the person who uploaded the file; the entity and a database check both refuse. Verification needs an uploaded file.
- **Viewing** the file needs FUEL_MANAGE, FUEL_TRANSPORT_MANAGE, FUEL_COMPLIANCE_MANAGE or FUEL_COMPLIANCE_VERIFY (documents are not for read-only users, as in decision 9). Uploading needs the same permission as recording a cleaning.
- **Blocking:** a new setting, "Require cleaning certificates to be uploaded and verified" (Changeover rules tab). When ON, a cleaning clears a product changeover only if its certificate is uploaded and verified; otherwise the load stays blocked with the message "needs a cleaning recorded after that load, with its certificate uploaded and verified by a second person". When OFF (the default, and every existing company) the rule is exactly as before: a certificate reference or a note is enough.
- The block is the existing one (nobody can override a Block). Review points are unchanged.

## Endpoints (under /api/v1/fuel/tankers/{id}/compartments/{compartmentId}/cleanings/{cleaningId})
`POST /certificate` (multipart file), `GET /certificate`, `POST /verify`. Settings: `PUT /api/v1/fuel/eligibility/settings` accepts `requireVerifiedCleaning`; leaving it out keeps the current value (older clients still work).

## Not done
- A verified file is not locked beyond being one-per-cleaning and never replaced; no virus scan (same as other evidence).
- The setting is company-wide, not per product pair.
- Existing cleanings have no file, so switching the setting on means every pending changeover needs a fresh, verified cleaning.

## Changes
V714 (file and verification columns on `fuel_compartment_cleanings` with a check; `fuel_settings.require_verified_cleaning`); `FuelCompartmentCleaning` (attach, verify), `FuelCleaningCertificateService` (new), `FuelEligibilityService` (settings, verified-only rule, view), three endpoints. Web: Cleanings list shows certificate status with Upload, View and Verify; the setting checkbox. Tests: `FuelCleaningCertificateServiceTest` (new), `FuelEligibilityServiceTest`, `EligibilityTab.test.tsx`.
