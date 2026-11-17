# Fuel: compliance visibility for limited users (decision 9) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests pass. No migration.

## Decision 9 (closed)
Limited users see that an item exists, its status and its expiry, but not the documents or details.

## Rule (decided on the server)
- **Full view:** anyone holding FUEL_MANAGE, FUEL_COMPLIANCE_MANAGE or FUEL_COMPLIANCE_VERIFY (ADMIN holds all three).
- **Status-only view:** anyone else with FUEL_READ. They see title, type, who or where it is for (tanker, driver, depot, company), legal entity, depot, licensed activity, expiry date, days to expiry and status.
- **Left out of the status-only view:** reference number, notes, issuing authority, issue date, and the document count. `detailsHidden` is true so the screen can explain.
- **Documents:** the evidence list and the evidence file download now need one of the three permissions (they used to need only FUEL_READ). A read-only user gets a 403 even if they know an id.
- Coverage ("licences by entity / site") shows counts only and stays visible to FUEL_READ.

## Behaviour change to know about
People who today hold only FUEL_READ lose the reference, notes and documents. If someone needs them, give them FUEL_COMPLIANCE_VERIFY or FUEL_COMPLIANCE_MANAGE. No new permission was added.

## Not done
- Compliance reminder emails still go to admins and are unchanged.
- Eligibility checks still name the expired item in the allocation error; that message carries title and expiry only, no document.

## Changes
`FuelComplianceAuthority` (new), `FuelComplianceService.list(tenant, detail)`, `ComplianceResponse.statusOnly()` and `detailsHidden`, controller authorities on the two evidence endpoints. Web: ComplianceTab hides the Reference column and Evidence button and shows a one-line explanation. Tests added in `FuelComplianceServiceTest`, `FuelComplianceAuthorityTest`, `ComplianceTab.test.tsx`.
