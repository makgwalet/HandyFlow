# Supply Chain engine audit — 2026-11-18

Method: read `platform/.../supplychain` on branch `scm-hardening` against the external assessment. Code read only; nothing run. The assessment says it used an older snapshot, and it is behind the code in several places.

## Assessment vs code

| Assessment says | Code shows |
|---|---|
| Budget checks may not exist | **Exist.** `BudgetService`: requisition submit/approve checks (reserve nothing); PO approval creates a commitment (`ScBudgetCommitment`), with warning/HARD_STOP, authorised overrides (`ScBudgetOverride`), events, and a **row lock when committing** (`evaluate(..., lock=true)`). The stale "not implemented" comment is the thing to remove. |
| RFQ/tender "not established" | **Exist**: `RfqService`, quotations, `RfqComparisonService`, `RfqEvaluationRules`, invitations, events. Award-to-PO and single-source justification not checked. |
| Supplier performance "not established" | **Exist**: `SupplierScorecardService`, `SupplierScoreRules`. |
| Supplier documents/compliance partial | **Exist**: documents, compliance rules and scheduler, bank-change control, lifecycle (suspend/blacklist), audit. |
| PO amendments | **Exist**: amendments, versions. |
| Three-way match enforces, not just reports | `ThreeWayMatchEngine` + persisted `ScMatchException`; `ScSupplierInvoice.approve` is state-guarded and override-approve only from DISPUTED. Whether every status can reach approval/payment through other endpoints is **not proven**; needs a test. |
| SoD default off | **Confirmed**: `app.scm.sod.enforce:false` in `ScmService`, `PoAmendmentService`, `SupplierDocumentService`. Needs an explicit rollout decision and a test with it on. |
| Reversal uses `SCM_ADMIN` | **Confirmed** (`ScmReceivingController`, comment says temporary). |
| Contracts, returns/claims, quality inspection | **Not found** (no model, service or controller). Genuine gaps. |
| Multi-warehouse, replenishment | `ScStockLocation`, `ScStockMovement`, `ScInventory` exist; no reorder/replenishment service found. |

## Gaps, in order
1. Permission split: `SCM_RECEIVE_POST` and a reversal permission replacing `SCM_ADMIN` (reconcile with the permission catalogue and role seeds first).
2. SoD rollout decision, plus tests proving it blocks when on.
3. Test that no endpoint can approve/pay an invoice with an open critical exception.
4. Concurrent-budget test (two POs against one budget).
5. Supplier contracts (reuse shared Contracting/Documents), returns & claims, quality inspection.
6. Replenishment recommendations; analytics.

## Not verified
Nothing was run. Fuel and Supply Chain were not compared for supplier sharing beyond the stated design.

## Status after the hardening work (2026-11-18, evening)

| Item | State | Where |
|---|---|---|
| Reversal permission split off `SCM_ADMIN` | Done. `SCM_RECEIVE_REVERSE` (or `SCM_ADMIN`) reverses a receipt. V760 seeds the permission and grants it to roles that hold `SCM_ADMIN`; grant it deliberately to the people who should reverse, then remove it from the rest. | `ScmReceivingController`, V760 |
| SoD rollout | Decided: **on by default** (`app.scm.sod.enforce` now defaults true), matching the shared platform checker. A sole holder of the checking permission may still act; that is written to `audit_events`. Covers PO amendments, supplier documents, RFQ awards, requisitions, supplier lifecycle, invoice approval. | `ScmSeparationOfDuties`, `ScmSeparationOfDutiesTest` |
| No endpoint pays an invoice with a critical match exception | Proven by tests per transition plus a source scan that only `ScmService` moves an invoice toward payment. The one sanctioned bypass is the override path (reason required, exceptions marked resolved, second person under SoD, `SCM_ADMIN` only). Cancelling a billed invoice and disputing an approved one are refused. | `ScmInvoicePaymentGuardTest` |
| Contracts | Done: lifecycle, renewal window, price check against contract on PO lines (warning), daily expiry scheduler. | V762, `ContractService`, `ScmContractController` |
| Returns and claims | Done: return, approval, credit note recorded (not yet posted to AP). | `SupplierReturnService`, `ScmReturnController` |
| Quality inspection | Done. A failed inspection raises a draft return; **inspected stock is not quarantined**. | `QualityInspectionService`, `ScmQualityController` |
| Supplier scorecard inputs | Inspection pass rate and return rate now feed the score. | `SupplierScoreRules` |
| Quotation and RFQ reminders | Hourly job; marker columns stop repeats. | `ScmReminderService`, `RfqReminderRules` |
| RFQ REST controller | Was missing; added (list, create, publish, extend, close, cancel, award, create-PO, comparison, quotations). | `ScmRfqController` |
| Per-tenant match tolerances | Done (was a stub), with audit event. | `ScmMatchTolerances`, `ScmMatchSettingsController`, V761 |
| No-PO invoices count as budget actuals | Done when the invoice is coded to a department, cost centre or project. **An uncoded no-PO invoice still counts in no budget.** | `BudgetService`, V761 |
| DB constraint against overlapping budgets | Done as a trigger (no `btree_gist` needed). Existing overlaps are untouched; list them with `v_sc_budget_overlaps` and fix them before relying on it. | V761 |
| Goods-receipt lines locked in a fixed order | Done (deadlock risk). | `GoodsReceivingService` |
| Web: requisitions, RFQ comparison, budgets | Done; permission-gated sidebar entries. | `handyflow-web/src/pages/supply-chain` |
| Integration tests | Written: budget race, amendment withdraw with the approval engine down, GR reversal after a partial invoice, requisition double submit, overlap trigger incl. a race. | `src/test/.../supplychain/integration` |

### Still open
- **Nothing here has been compiled or run by Maven.** Run `mvn test` before merging; the integration tests also need Docker.
- Credit notes are recorded, not posted to AP. Failed-inspection stock is not quarantined.
- Replenishment recommendations and analytics (item 6 above) are untouched.
- Migrations V760–V762 should be tried on a copy of production data first.
