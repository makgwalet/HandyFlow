# Authority and accreditation framework (patch 0169)

## What was already there
Authorities, delivery sites, accreditations with a status lifecycle and history, scope per programme per site, a coverage check, and reporting obligations per authority (V911, V925). A submitted application is never an approval; a recorded approval is checked against its scope, site and dates by `AccreditationRules`. There is no single "accredited" flag anywhere. This patch adds the missing pieces and does not change those rules.

## What this adds (V935)
- **Authority types:** SAQA and DHET join QCTO, SETA, Umalusi, CHE, professional body and other. The list order the screens use is QCTO, SETA, SAQA, DHET, Umalusi, CHE, then the rest. That is the provider's build and display order, not a legal ranking. Only authorities the provider records appear; nothing is seeded.
- **Dates:** `effective_from` (when the authority says it takes effect) and `review_due_on` (the provider's own review date) beside the existing approval and expiry dates. Cover is refused on days before `effective_from`.
- **Current, transitional, historical:** `currency` on each accreditation.
  - Transitional needs a note (10+ characters) and is only ever covered *with a caveat* (`COVERED_WITH_CONDITIONS`), never as plain cover.
  - Historical needs a note and the day it stopped applying (not in the future, not before approval), and may name the record that replaced it (same authority). It still counts for delivery on days up to that end date, and not after.
  - Changing currency writes a line in the accreditation's history. Nothing is overwritten or deleted.
- **Source:** type (letter, certificate, register entry, notice, email, website, other), document or reference, version or publication date and section or page. Optional, but if any is given the type and a reference (5+ characters) are required. This sits beside the existing "where it was verified" text.
- **Evidence:** letters, certificates and register entries behind an accreditation. New evidence is *pending*. A different person verifies it, or the same person says why alone (10+ characters). Rejecting or withdrawing needs a reason. Pending evidence is shown as such and is never counted as verified.
- **Overview:** `GET /accreditation/framework` lists authorities in the order above, each with its records (current first, then transitional, then historical), dates, source, scope and evidence counts, and live reporting obligations.
- **Prompts:** expired, transitional, no verified evidence on a current approval, evidence waiting to be checked, review overdue. These are prompts to look. They are not findings and no threshold is built in.

## Not done on purpose
- Approving an accreditation does not yet require verified evidence. Existing approvals have none, and making it a rule would block them. The overview flags the gap instead. Say if you want it enforced for new approvals.
- Evidence holds a description and reference, not an uploaded file.
- Reporting obligations are unchanged. The overview only counts them.
