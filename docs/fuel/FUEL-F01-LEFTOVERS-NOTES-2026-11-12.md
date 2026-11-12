# Fuel F01 leftovers: site link, blocking by legal entity, licence evidence files and verification (patch 0794)

## Implemented
- **Site link.** A compliance item can be tied to a depot (`depot_id`, same-tenant check, an unknown or foreign depot is a 404). Coverage now also reads per site ("Licences by site"), next to the per-legal-entity view. Information only.
- **Blocking by legal entity (opt-in).** A new per-company setting, "Licences that stop loading when expired", holds the licensed activities (Wholesale, Retail, Distribution, Storage, Transport, Other) that block. It is empty by default, so no existing tenant changes. When set, `allocate` refuses a load while a LICENCE with a chosen activity has expired and either belongs to the legal entity that owns the loading depot or is linked to that depot. The loading depot is the depot of the delivery's tank. The message names the holder, the licence, the date and the depot. Route stops are covered because they allocate through the same method. The existing tanker, driver and company-licence block is untouched.
- **Evidence files.** A scan or photo (JPEG, PNG, WebP or PDF, up to 5 MB) can be uploaded against an item. The type is decided from the file's bytes, not the name or the browser's claim. The file goes to the shared file store; the record keeps the store key, which is never returned: lists show the file name and size, and the file is read through its own download (private, no-store, nosniff). If saving the record fails the stored file is deleted. Typed link references still work.
- **Verification.** A second person verifies evidence (new permission FUEL_COMPLIANCE_VERIFY, ADMIN only). The person who added it cannot (entity, service and a database check all refuse). A replaced item cannot be verified. Lists show Verified or Not verified.

## Preserved
Compliance items, renewals, reminders, the allocation block for tankers, drivers and company licences, evidence supersede (append-only), and the per-entity coverage are unchanged. Old clients that omit the new request fields keep working (the new fields are optional and null leaves the setting alone).

## Files
- DB: V677 (`fuel_compliance_items.depot_id`, `fuel_settings.licence_blocking_activities` default '', evidence file and verification columns with checks, permission FUEL_COMPLIANCE_VERIFY).
- Java: FuelComplianceItem, FuelComplianceEvidence, FuelSettings, FuelComplianceService (site coverage, `requireLoadingSiteLicensed`, `verifyEvidence`), new FuelComplianceEvidenceFileService, FuelComplianceController, FuelTransportService (one call), FuelSettingsService, DTOs (ComplianceRequest/Response, ComplianceLinkageDtos, FuelWholesaleDtos controls).
- Web: ComplianceTab (depot picker, site coverage, upload, View, Verify), complianceOps, SettingsTab (activity checkboxes), wholesaleOps.

## API
Under /api/v1/fuel/compliance: POST /{id}/evidence/file (multipart; FUEL_MANAGE or FUEL_COMPLIANCE_MANAGE), GET /evidence/{id}/file (FUEL_READ), POST /evidence/{id}/verify (FUEL_COMPLIANCE_VERIFY). Existing create/update accept `depotId`; controls accept `licenceBlockingActivities`.

## Tests
- Java: FuelLicenceBlockingTest (8), FuelComplianceEvidenceFileServiceTest (6), one FuelEndpointSecurityTest case. Parse-checked only; not run (you run Maven).
- Web: ComplianceTab (3 new), SettingsTab (1 new), complianceOps (2 new), wholesaleOps updated. Fuel and navigation suite 390 pass; tsc clean.
- V677 applied to a scratch PostgreSQL 16 with stubbed parent tables: runs clean, default is empty, and a self-verified evidence row is refused.

## Gaps and risks
- No rule says which licences a business must hold; the system only enforces what the company ticks in Settings. A company that ticks nothing has no new blocking.
- Verified evidence can still be replaced (with a reason, the old one is kept). I did not lock it, since a locked wrong document could never be corrected.
- No virus scan; no one-step "replace with a new file"; no expiry read from the document.
- Items linked to a depot but not to an entity block only that depot's loads; a licence on a different depot of the same entity blocks only if the entity link is set.
- Run Maven including FuelEndpointSecurityTest, FuelRepositoryTenantScopingTest, PermissionConsistencyTest and ArchitectureVerificationTest.

## Next
Phase 6a: group reporting (0795).
