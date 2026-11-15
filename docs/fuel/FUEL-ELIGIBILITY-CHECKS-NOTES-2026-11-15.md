# Fuel eligibility checks (site tanker, site driver, tanker compartment product)

Status: written, not compiled or run (Java). Web tests and `tsc` were run. Migrations V696, V697, V698.

## What it does
Before a load is allocated to a tanker and driver (including as a route stop), the server checks three things beside the existing
licence, permit, roadworthiness, loading-site-licence and capacity checks, which are unchanged and still mandatory.

1. **Customer site restricts tankers.** Per site: approved tanker list, maximum total capacity, maximum gross vehicle mass, maximum length/width/height,
   approved tanker types, special access notes. Tankers gain type, mass and dimensions.
2. **Customer site requires approved drivers.** Per site: require-approved-drivers switch, required training name. Per driver at a site: status
   (approved / restricted / suspended), induction date and expiry, training reference and expiry, conditions.
3. **Tanker compartment product rating.** Per compartment: approved products. Company-set changeover rules (from product, to product). Append-only cleaning
   records with certificate reference and/or evidence note. Previous cargo is derived from the compartment's latest load that left the depot.

## Outcomes
| Situation | Result |
|---|---|
| Tanker not on a site's list, over capacity/mass/size, wrong type | Block |
| Driver suspended; not on the list when required; induction, or required training, expired or missing | Block |
| Compartment not rated for the product | Block |
| Changeover rule applies and no cleaning recorded after the previous load | Block |
| Tanker/driver detail the site limits but is not recorded; missing expiry date; driver restricted; unknown previous load where a rule leads into the product; unrated compartment when the company requires ratings | Review |
| Site access notes, driver conditions | Information |

- **Block**: stops the allocation for everyone. No override exists.
- **Review**: allowed only for a user with `FUEL_ELIGIBILITY_REVIEW` (sensitive; granted to ADMIN by the migration) who gives a reason of at least 5 characters.
  The acknowledgement is written to `fuel_eligibility_overrides` (append-only: load, tanker, driver, findings, reason, user, time), and only when the allocation goes ahead.

## Design decisions
- A site, tanker or compartment with nothing configured is not restricted, so existing tenants see no change.
- Every limit is entered by the company; no regulatory interpretation, dimension or product pairing is built in. Which product changes need cleaning is the company's list.
- Product compatibility is checked per compartment from the split in the allocation request, not per vehicle.
- A suspension or restriction recorded at a site counts even when the site has no rule.
- A cleaning counts only if its time is after the compartment's previous load. Cleanings cannot be edited; record a corrected one.
- Cleaning evidence is a reference and a note. There is no file upload yet.
- A load that failed and went back to SCHEDULED is not treated as previous cargo, even if the product stayed on board.

## Endpoints (all under /api/v1/fuel)
`GET/PUT customer-sites/{id}/access-rule`, `GET/PUT/DELETE customer-sites/{id}/driver-approvals[/{employeeId}]`, `GET/PUT tankers/{id}/specs`,
`GET tankers/{id}/compartment-ratings`, `PUT tankers/{id}/compartments/{cid}/rating`, `GET/POST tankers/{id}/compartments/{cid}/cleanings`,
`GET/POST/DELETE changeover-rules`, `GET/PUT eligibility/settings`, `POST deliveries/{id}/eligibility` (preview, changes nothing).
`allocate` and route-stop add take an optional `reviewReason`.

## Web
Planning dialog previews Blocks, Reviews and information once tanker, driver and split are complete; a Block disables sending, a Review shows a reason field
to reviewers and a note to everyone else. New "Site & tanker rules" screen under Distribution.

## Still to run / decide
- Maven build and tests for all new Java (never compiled here).
- Granting `FUEL_ELIGIBILITY_REVIEW` to roles other than ADMIN.
- Whether cleaning certificates should be uploaded files.
