# Fuel module backlog (as at 2026-11-12, after Phase 8, patches 0798-0810)

Built so far is in `FUEL_GAP_REGISTER.md` and the per-phase notes. This file lists only what is **not** done: checks owed, deliberate gaps, and decisions waiting on you. Items are grouped, not ranked. Size: S under a day, M a few days, L a week or more (rough).

## 1. Checks owed (before more building)
| # | Item | Why |
|---|---|---|
| V1 | Run `mvn -q test "-Dtest=Fuel*Test"` with the app stopped | The Java for 0798-0810 compiles and the migrations V679-V686 applied, but the new tests have not been seen passing. Any failure becomes patch 0811. |
| V2 | Restart `spring-boot:run` with no other Maven or IDE build running | A first login failed with `ClassNotFoundException: TenantContext`. The classes vanished from `target/classes` while the app ran, so it looks like a build-folder clash, not a code fault. |
| V3 | Manual pass: Access limits, depot "Supplied by", create import key and send a test shift, accept it | Only the web side has been exercised. |
| V4 | `EventsSchemaAndSecurityGuardTest` (Events, 40 endpoints vs more than 100 expected) | Failing before this work. Not Fuel's, but it blocks a green build. |

## 2. Decisions waiting on you
| # | Decision | Options and my default |
|---|---|---|
| D1 | Auto-accept of imported pump/POS shifts | Manual only today. Auto-accept within a variance limit needs a trust and tolerance rule from you. Default: stay manual. |
| D2 | Contract entity and volume pricing | Contracts group and report only; no price is held, so no second price master. Volume pricing needs a rule for how it interacts with B07 price approval. |
| D3 | Who is told about variances and long-open items | Administrators only today. Needs a per-company choice. |

## 3. Access limits (G01 scope, 0808)
- Depot filtering of lists and reports other than tanks and depots (orders, group report, stock summaries, shift lists). M.
- Forecourt supply report (0809) is not filtered by a user's depot limits. S.
- Per-request lookup cost for limited users; add a short cache if profiling shows it. S.

## 4. Forecourt and depots (0809)
- Show forecourt sales per legal entity in the main group report table (now only in the new section). M.
- Split one forecourt's volume between several supplying depots. Today one supplier per depot. M.
- Match forecourt receipts to the supplying depot's dispatches. M-L.
- A depot update with no `supplyingDepotId` clears it. Other API clients must send it. S, documentation.

## 5. Pump/POS import (D03, 0810)
- Auto-accept (see D1). S once decided.
- Alias table so a provider's pump id maps to a Fuel pump (today by pump name). S-M.
- Live transactions, refunds and voids from the sender (today only whole finished shifts; adjustments use the existing flow). L.
- Tank-gauge feeds. L.
- Device, vendor adapters and outbound calls: out of scope by decision.
- Accepted shifts carry server time; provider times are kept in the notes only. S.

## 6. Distribution and routes
- Eligibility checks: which tanker and driver may serve which customer or product (C04). M.
- Freight added to a stock receipt after the fact. S-M.
- Alert for variances that stay open too long. S.

## 7. Truck stop (E01)
- Fleet accounts and charge-to-account. L.
- Invoicing and payment for stays. L.
- POS items, driver self-service, capacity view. M each.

## 8. Not Fuel, still open from earlier in this session
Mobile ecosystem decisions: monorepo layout, SDK 58 re-scaffold, backend auth changes B1-B9. See `docs/mobile/` and the project copies.

## 9. How to use this list
Pick by group. Anything in section 2 needs your answer first. Everything in sections 3-7 will follow the same rules as before: no applied migration is edited, no duplicate master data, divisions enforced on the server, existing companies keep their access, patches sent for you to apply with `git am -3`.
