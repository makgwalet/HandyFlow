# ADR-003: Business readiness (automatic requirement checking for tenders)

**Status:** IMPLEMENTED (first slice). Pricing, the tender document builder and the submission package are NOT built; see "Not done".
**Date:** 2026-10-06. **Modules:** `businessreadiness` (new), `compliancetender`, `complianceservices`.

## 1. What changed for the user

A tender requirement used to be a line the user ticked by hand (MET, MISSING, NOT_APPLICABLE, PENDING_REVIEW). Now each requirement that is linked to a **tracked requirement with a rule** is
also **checked against the registrations and documents the business actually holds**, and the tender page shows a "Readiness check": met, missing, expired, pending, or not checked, each
with the reason ("SARS TCS registration expires 2026-11-14, before the closing date 2026-11-15"). The same works for a client's tenders, judged against that client's records.

The check is **read-only**. It never changes the status the user set; it only points out where the evidence disagrees ("You marked this met, but the evidence says missing").

## 2. Decisions

1. **The parallel `ClientXxx` entities stay.** `complianceservices` records, in its own package-info, a deliberate decision (Part 8 of the strategic roadmap backlog) never to reference
   `compliancetender`'s Java code. An external analysis recommended reversing it; that is the owner's call and was not made. What is shared instead is the RULES: a new, **pure** module
   `businessreadiness` (no entities, no repositories, no dependencies) holds neutral input records and one stateless evaluator. Each side keeps its own tables and services, maps its own
   entities to the neutral facts, and calls the same evaluator, so a requirement is judged identically for a tenant and a client and the logic is written, tested and fixed once.
2. **The rule is configuration on the requirement, not code.** A tracked requirement says what satisfies it:
   - a **registration**: authority and/or registration type (V312: `satisfied_by_authority` VARCHAR(20), `satisfied_by_registration_type` VARCHAR(60), nullable, on both requirement tables);
   - a **document**: the EXISTING `evidence_type` column, whose schema comment already defined it as "the document type this requirement is satisfied by". No column was added for it.
   Matching ignores case and surrounding spaces; a blank part matches anything. If both are set, both must hold. Regulatory knowledge (what proves "CSD active") therefore lives in the
   tenant's catalogue and changes without a release. Every vocabulary involved (authority, registration type, document type) is free text in this codebase, which is why an explicit rule is needed.
3. **Judged on the closing date.** A certificate that expires the week before closing is not valid for that tender, so a tender with a closing date is judged as of that date; with none, as of today.
   The panel states which it used. A registration is valid when ACTIVE with no expiry, or ACTIVE expiring on or after that date. EXPIRED, LAPSED and ACTIVE-past-expiry are expired; PENDING is pending;
   NOT_APPLICABLE registrations are ignored as if absent. A document must be unexpired on that date AND verified (unverified is pending).
4. **Facts, not a score.** Results are MET, MISSING, EXPIRED, PENDING, NOT_EVALUATED, NOT_APPLICABLE, shown as words (never colour alone), worst first. There is no percentage. A requirement with no rule
   is "Not checked" and is never counted as fine. "Expiring soon" means valid on the date that matters but expiring within 30 days of it (the same 30 days as the expiry scheduler).
5. **The version in force.** `ComplianceRequirement`'s own documentation says a check is judged against the requirement version in force, "not silently reinterpreted against today's rules". So a tender is
   judged on the version its requirement was linked to, and is told when a newer version exists. Saving an edit in the catalogue creates a new version.
6. **Rule fields on a new version: absent keeps, blank clears.** A request that omits the rule fields (null) keeps the existing rule, so an older client cannot wipe rules by accident; only an explicit blank
   clears them. The UI sends blank strings when a box is emptied.
7. **A link must be the owner's own.** A tender requirement may only link to one of the tenant's own tracked requirements (for a client tender, one of THAT client's). At check time an id that does not resolve
   for the tenant, or belongs to another client, is treated as not linked, so nothing is ever judged against someone else's rule.

## 3. Endpoints and screens

- `GET /api/v1/compliance/tenders/{id}/readiness` (COMPLIANCE_READ/MANAGE/ADMIN) and `GET /api/v1/compliance-services/tenders/{id}/readiness` (COMPLIANCE_SERVICES_READ/MANAGE/ADMIN). Rights from the other side do not open them.
- The tracked-requirements catalogue had **no screen at all** (API only), so rules could not be configured. New: Compliance > **Requirements** (the business's own) and a **Requirements** tab on a client
  (deep-linkable with `?tab=requirements`). One shared component serves both; the two wrappers only supply URLs and rights.
- One shared **Readiness check** panel on both tender pages, above the requirement matrix, refreshed whenever a requirement is added or its status changes.

## 4. Fixes made on the way (from the analysis, after checking each against the repo)

| Finding | Verdict | What was done |
|---|---|---|
| Two submits racing for the same snapshot number | Real but contained: unique indexes (V291, V297) refuse the duplicate and the whole submit rolls back | The snapshot is now flushed inside the transaction and a collision is reported as a 409 with a clear message, not a raw 500 |
| A tender requirement's tracked-requirement id is not validated | Real, low severity: the id was only echoed back, never used to load data, so no leak; the foreign key proves existence, not ownership | Validated in both modules (own tenant; for a client tender, same client) |
| PDF code fetches a stored logo URL (`new URL(...).openStream()`) | Code real, not exploitable today: the only writer (`TenantService.uploadLogo`) always stores a `data:` URI | The two tender PDF services now decode only `data:` URIs and refuse every URL. **Eight other PDF services (invoicing, fuel, clinic and others) have the same fallback and are untouched**: a platform-wide cleanup, best done once in a shared loader |

Claims in the analysis that were checked and found **false** in this repo: that the client requirements controller is missing (it exists), that `ClientComplianceRegistrationController` is duplicated (one file), and that there are no tests (the two modules had 9 and 12 test files).
iText in the tender PDFs is used by 20+ files across the platform and no shared document engine exists, so "move away from iText" is a platform decision, not a tender fix.

## 5. Limitations

- The evaluator uses the **current** state of registrations and documents; there is no history of past states. A tender that has already closed would be judged on today's records, so the panel is for tenders still in
  preparation, and the submission snapshot remains the record of what was submitted.
- The snapshot still captures only tender details, requirements and personnel. It does not yet include the readiness assessment or the evidence behind it.
- Vocabulary is free text: "CSD" vs "C.S.D." do not match. The catalogue screen shows what each rule says so a mismatch is visible, but nothing suggests values yet.
- Matching is by authority/type/document type only. It cannot say "CIDB grade 6 is below the required grade 7"; that needs structured grades, which do not exist.

## 6. Not done (the rest of the original plan)

Tender pricing and costing; the tender document builder and submission package; recording the readiness assessment in the submission snapshot; the platform-wide logo-URL cleanup; a shared PDF/document engine.

## 7. Verification status

Pure evaluator: 44 tests, run (39 deliberate breakages all caught). Entity rule tests: 14, run. The two readiness services: run against hand-built fake repositories (29 checks, both sides). Mockito service tests, controller tests and the PDF tests are written and
type-checked or syntax-checked but **not run**: no Maven run has happened. V312 has not been applied to a database, the new module has not been through `ArchitectureVerificationTest`, and JSON deserialisation of the extended request
records (which keep their old constructors for compatibility) is unverified under Jackson. Frontend: 51 new tests, all passing, with mutation checks across the logic, panel, catalogue, wrappers and page wiring.
