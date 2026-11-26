# Internal quality audits (patch 0177)

The provider's own checklists, the audits run against them, and the corrective actions that follow. **Quality audits** tab in the Training Provider module.

## What it does
- **Checklists** (administrators): a named list of questions with a scope (general, site, cohort or programme). Nothing is seeded; a checklist holds only what someone typed. Changing one makes a new version. Audits already planned keep the questions they were created with. Retire with a written reason; nothing is deleted.
- **Audits**: plan an audit from a checklist (optionally against a site, cohort or programme), start it, answer each question (no issue seen / issue / note / not applicable), add corrective actions, and complete it.
- **Corrective actions**: owner, due day, marked done with a note, then verified.

## Rules
- Only "no issue seen" may be answered without a note; every other outcome needs at least 10 characters saying what was seen.
- Every issue needs a live (not cancelled) corrective action before the audit can be completed. Every item must be answered. A summary of at least 10 characters is required.
- Completing an audit needs a second person. The lead can complete alone only by confirming and giving a written reason of at least 10 characters; the reason is kept.
- Verifying an action: someone other than the person who marked it done, or the same person with a written reason.
- Cancelling an audit or an action needs a written reason.
- An audit records what the auditor saw. It never says anyone or anything is compliant, accredited or competent.

## API
`/api/v1/training-provider/quality-audits` (READ / MANAGE; checklist create, version and retire are ADMIN). Migration V943.

## Not yet done
Reminders for overdue actions, DB-level uniqueness beyond the CHECKs, and linking an audit action to a document or accreditation record.
