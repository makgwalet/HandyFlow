# Fuel F01: licence linkage (patches 0775 backend, 0776 web)

Apply with `git am -3`. Migration **V662** (next free is V663). Nothing was pushed.

This is a P0 item from the gap register (not a numbered phase). It was chosen as the next step after Phase 4 because Truck Stop (E01, P2) must not start while P0s are open.

## 1. What was implemented
- **Licence linkage.** A compliance item (licence, certificate, insurance, etc.) can now be linked to a **legal entity** (from Phase 1), a **licensed activity** (Wholesale, Retail, Distribution, Storage, Transport, Other) and an **issuing authority**. All three are optional, so every existing item is unchanged and unlinked.
- **Evidence.** Each item can carry evidence references (link to a scanned licence). Evidence is append-only: a wrong one is replaced with a reason (at least 5 characters) and kept on record. **The file is not uploaded by this feature; it stores the link or reference you give it.**
- **Coverage view.** For each legal entity: how many of its licences are valid, expiring (30 days) and expired today, and which activities they cover; plus the number of licences not linked to any entity. **This is information only.** The system does not store or decide which licences a business must hold; that is the company's own regulatory judgement and nothing is hard-coded.
- The linked entity must belong to the same tenant (unknown or foreign id returns 404 and nothing is saved).

## 2. Existing functionality preserved
- No change to allocation blocking, expiry reminders, renewals or the compliance dashboard counts. The existing company-licence block still applies to any item with subject COMPANY and type LICENCE, linked or not.
- `ComplianceRequest` keeps its old 9-field constructor. JSON clients that omit the new fields behave as before. Editing an item **without** sending the new fields clears its entity link, activity and authority, because the edit form always sends them.
- `ComplianceResponse` gained fields (entity id and name, activity, authority, evidence count).

## 3. Database (V662, additive)
`fuel_compliance_items`: `legal_entity_id`, `licensed_activity` (checked against the six values), `issuing_authority`, plus an index on entity. New `fuel_compliance_evidence` (file reference required, status ACTIVE or SUPERSEDED, a replaced row must carry a reason and who replaced it).

## 4. API (under `/api/v1/fuel/compliance`)
`GET /coverage` (FUEL_READ); `GET /{id}/evidence` (FUEL_READ); `POST /{id}/evidence` (FUEL_MANAGE); `POST /evidence/{id}/supersede` (FUEL_MANAGE). Create and update accept the three new fields. Compliance routes are shared foundation, so no division rule applies.

## 5. Web
Compliance page: Legal entity, Licensed activity and Issuing authority fields on the add/edit dialog; the entity, activity and authority shown under each item; an "Evidence (n)" button with a dialog (list, add, replace with reason; read-only users can only look); a "Licences by legal entity" card with the unlinked count and the not-advice note.

## 6. Verification: what was and was not run
- **Java is parse-only. Maven has not been run**, including the new tests: `FuelLicenceLinkageTest` (5) and `FuelLicenceLinkageServiceTest` (6). `FuelComplianceServiceTest` gained two mocks for the new dependencies.
- **V662 run twice in a scratch Postgres 16**: idempotent; existing rows get null linkage; an unknown activity, a blank file reference, a replaced row without a reason and an unknown status are all rejected.
- **Web: vitest 245 pass** (4 new) across `src/pages/fuel` and `src/navigation`; `tsc` clean.
- Not tested: MockMvc, security or cross-tenant tests (H01); `ArchitectureVerificationTest`.

## 7. Not done, and why
- **Site linkage:** a licence can be tied to a depot or tank through the existing subject field, but there is no separate site entity to link, and Phase 1 did not create one. The existing DEPOT subject is the nearest thing.
- **Blocking by entity:** allocation does not yet refuse a load because the relevant legal entity's licence expired. That needs a link from a tanker, delivery or order to the legal entity that operates it, which does not exist.
- **Vehicle and person links beyond the existing TANKER, DRIVER subjects.**
- **Evidence upload and verification** (as built for delivery evidence in Phase 3): evidence here is references only, with no second-person verification.
- **A required-licence checklist per activity:** deliberately not built (section 1).

## 8. Risks
- Coverage can look reassuring ("all valid") while a licence the business needs was never recorded; it can only report what was entered.
- Evidence references are free text and are not checked.
- Editing an item from an older client that omits the new fields unlinks it.

## 9. Next
H01 (controller, security and cross-tenant tests) and A01 (module boundary) are still open P0s. Your answers are recorded: Truck Stop reservations on Bookings, tankers referencing Fleet vehicles, Fuel may depend on Accounts Payable via a facade. All three add module dependencies, so A01 should be settled (Maven result on `ArchitectureVerificationTest`) before they are built.
