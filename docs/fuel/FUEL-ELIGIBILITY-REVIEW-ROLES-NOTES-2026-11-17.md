# Fuel: eligibility review roles (decision 7) — notes, 2026-11-17

Written, not compiled or run (Java). Web tests pass.

## Decision
Eligibility review goes to the Fuel Manager, Dispatch Supervisor, Compliance Officer and ADMIN. Review is separate from override. Permission-based, not hard-coded roles; tenants can configure equivalent custom roles.

## What the code already did
- A **Block** (expired licence, unapproved tanker, and so on) stops an allocation and cannot be overridden by anyone. So a Dispatch Supervisor can never override an expired statutory licence.
- A **Review** point (the check could not be made because information is missing) lets a person holding `FUEL_ELIGIBILITY_REVIEW` allocate with a recorded reason. That is the only thing the permission does.
So review versus override is already separate in the rules; nothing was merged and nothing needed splitting.

## Changes
- V711: clearer permission wording (says it cannot override a block), and an index for the log. No role grants are seeded: this project grants new permissions to ADMIN only and lets each tenant assign them to its own roles, and no role names (Fuel Manager and so on) exist as system roles to attach to.
- `GET /api/v1/fuel/eligibility/overrides?limit=` (read only, newest first, max 200) for `FUEL_ELIGIBILITY_REVIEW`, `FUEL_COMPLIANCE_MANAGE`, `FUEL_TRANSPORT_MANAGE` or `FUEL_MANAGE`: tanker, findings that were open, reason, who decided (name if it can be found), when. This is what a Compliance Officer reviews.
- Web: a "Review decisions" tab in Eligibility, shown only to those permissions.

## For you to do
- In your company's role settings, give `FUEL_ELIGIBILITY_REVIEW` to the roles that should have it (fuel manager, dispatch supervisor, compliance officer). The compliance officer also needs `FUEL_COMPLIANCE_MANAGE` or the review permission to see the log.
