# Fuel proof-of-delivery "received by" policy (Batch B)

Status: implemented; backend not compiled or run in the build sandbox. Not production-ready until the tests pass and the rollout steps are done.

## Rule
- An ordinary customer-site delivery needs a receiver name. If the receiver signed on behalf of someone, that person's name is needed too.
- The only ways round it are four authorised exceptions: `UNATTENDED_SITE`, `AUTOMATED_RECEIVING`, `AFTER_HOURS_APPROVED`, `CONTACTLESS_AUTHORISED`. There is no general driver bypass.
- Enforced by the server on both completion paths (driver `POST /driver/loads/{id}/complete` and office `POST /deliveries/{id}/complete`) through `FuelPodPolicyService.complete`.

## What each exception needs (all checked on the server)
| Check | Applies to |
|---|---|
| Company allows that type (`fuel_settings.pod_exception_types`) | all |
| Reason of at least 10 characters | all |
| Evidence already on the load: an active PHOTO, GPS, DOCUMENT or SIGNATURE record | all |
| The delivery's site is marked with that same type (`fuel_customer_sites.pod_exception_type`) | UNATTENDED_SITE, AUTOMATED_RECEIVING |
| A prior approval by someone holding `FUEL_POD_EXCEPTION_APPROVE` (granted to no role), same type, used once | AFTER_HOURS_APPROVED, CONTACTLESS_AUTHORISED |
| No receiver name given alongside | all |

Each use is stored in `fuel_pod_exceptions` and writes audit event `FUEL_POD_EXCEPTION_USED`; an approval writes `FUEL_POD_EXCEPTION_APPROVED`. A retry of a completion that already happened (same client request id) returns the first result and records nothing new.

## API and configuration
- `GET/PUT /api/v1/fuel/settings/pod-policy` (PUT needs FUEL_CONFIGURATION_MANAGE): `enforced` and `allowedExceptions`.
- `PUT /api/v1/fuel/customer-sites/{siteId}/pod-exception` (FUEL_CONFIGURATION_MANAGE): mark a site UNATTENDED_SITE, AUTOMATED_RECEIVING or clear it.
- `POST /api/v1/fuel/deliveries/{id}/pod-exception/approve` (FUEL_POD_EXCEPTION_APPROVE): `{type, reason}`.
- `GET /api/v1/fuel/deliveries/{id}/pod-exceptions` (FUEL_READ).
- Migration V726, additive.

## Rollout switch (decision needed)
`pod_policy_enforced` defaults to **off**. The web office screen currently lets staff complete a delivery with no receiver name, so turning enforcement on changes that screen. Switch it on per company once the office is ready.

## Interpretation to confirm
"Acknowledgement" is implemented as the receiver name (plus the on-behalf name where used). The driver's signature is a separate evidence step that can be queued after the completion, so making it a hard requirement on completion would need the app to send it with the delivery. Say if you want that.

## Not covered
- The web office screen has no UI for exceptions, approvals or site marking yet (API only).
- The app cannot confirm offline that the evidence reached the server; if the photo has not synced when the completion arrives, the server refuses it and the item stays in the sync centre.
- No controller-level test for revoked permissions (enforced by `@PreAuthorize`).

## Tests
Backend `FuelPodPolicyServiceTest` (ordinary, missing receiver, on-behalf, unattended site, evidence/reason/site validation, not-allowed and mixed, after-hours approval and one-time use, wrong-type approval, approve, tenant isolation, off by default, replay, site designation, unknown type); `FuelTransportServiceTest`. Mobile `loads.test.ts` (221 mobile tests pass).
