# Learner and employer portals (patch 0178)

Read-only. Both portals show what the provider has recorded. Neither says anyone is competent, accredited or compliant.

## Learner portal
- **Access:** an administrator or manager invites the learner from the learner's page (**Learner portal access**), by email, with a written reason (10+ characters). The learner accepts the emailed link, sets a password and signs in at `/training-provider/portal/learner/login`.
- **One live link per learner.** Access is ended with a written reason; ended access stays in the list. Nothing is deleted. Only an active learner can be invited.
- **What the learner sees:** their own enrolments (programme, status, dates, group), attendance as counts (present, late, absent, excused; no percentage and no threshold), the recorded completion outcome in plain words ("Recorded as completed" or "Recorded as not completed", never "competent"), and upcoming scheduled sessions.
- Table `trainprov_learner_portal_grants` (V944). API: staff `/learners/{id}/portal-access`; learner `/portal/learner/auth/register|login`, `/portal/learner/me`.

## Employer portal
- Uses the existing client invite (Clients → portal users). New tab **Programme learners** (`GET /portal/me/programme-learners`).
- Lists the client's learner enrolments with the same fields as above. **Only learners whose consent flag is set** are listed. The rest are counted and the employer is told to ask the provider. The consent flag is the one recorded on the learner; whether it covers sharing with an employer is the provider's decision.

## Fixes to the existing client portal found while building this
- The server read the tenant from `TenantContext`, which is never set on a portal request, so every client portal read would have failed. It now takes the tenant from the caller's accepted grant.
- The screens used the staff API client (staff token), read `id` where the server sends `portalUserId`, and the home route had no sign-in guard. They now use a portal client (`trainProvPortal.client.ts`), read `portalUserId`, and the route is guarded.

## Known limits
- A portal login (email) that already exists, for example an employer contact who is also a learner, cannot accept a learner invite; the invite must use a different email.
- Employer rows read each learner's enrolments one at a time. Fine for tens of learners, to be batched for hundreds.
- The bookkeeping portal data controller has the same `TenantContext` pattern and was not changed here.
- Mockito service tests, Maven compile and a browser check have not been run.
