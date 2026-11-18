# HandyFlow Supply Chain mobile: product and technical plan

Consolidated 2026-11-18 from the "Supply Chain Mobile: Deep Product, Business and Technical Planning" paper. Status tags: `[Proposed]` recommendation from the paper, `[Verified]` seen in the repository, `[Requires approval]` needs an owner decision, `[Deferred]`.

Important: the eight decisions at the end of the paper were shown as recommended defaults ("8 / 8 selected", "planning preferences, not saved configuration"). They have not been recorded as approved. Treat everything here as `[Proposed]` until the owner confirms.

## 1. Conflict with earlier mobile assessment `[Requires approval]`
`MOBILE_MODULE_ASSESSMENT` (12 Nov) put Supply Chain in category C, priority P3: fold goods receipt and approvals into the warehousing scanner and the shared employee app. This paper proposes a standalone app. The two cannot both stand. Options: (a) standalone HandyFlow Supply Chain app as proposed; (b) Supply Chain as a section of the employee app, with receiving shared with a warehousing scanner. The paper's own priority is receiving, approvals and requisitions first, which works in either shape.

## 2. Product `[Proposed]`
Standalone commercial mobile product, not a phone version of the web portal. Tagline: *Procurement. Suppliers. Receiving. Control.* Own app identifier, icon, splash, navigation, notification categories, offline workflows, release cycle, store listing and entitlement. A tenant can subscribe without Fuel, Fleet or Warehousing. It must not duplicate the Supply Chain backend; it consumes existing APIs and shared services.

Principles: financial controls enforced by the backend, never trusted to the app; offline work reliable, traceable and safe against duplicates; supports one-buyer-one-warehouse SMEs through multi-branch, multi-entity enterprises.

### Personas and home screens
Business Owner, Supply Chain Director, Procurement Manager, Buyer, Requisitioner, Department Manager, Budget Controller, Receiving Officer, Warehouse Manager, Quality Inspector, Supplier Manager, AP Officer, Finance Manager, Auditor. Users hold several roles. Home is role-aware: receiving officer sees expected and overdue deliveries and pending GRNs; buyer sees requisitions to source, open RFQs, POs awaiting acknowledgement; procurement manager sees approvals, spend, supplier risk; finance manager sees invoice exceptions, commitments, financial approvals.

### Navigation
Bottom bar: Home, Requests, Orders, Receiving, More. More: Suppliers, RFQs and Tenders, Contracts, Inventory, Stock Transfers, Quality Inspections, Returns and Claims, Invoice Matching, Budgets, Reports, Notifications, Settings, Help. Navigation is permission-based.

## 3. Functional scope
1. **Dashboard and task engine**: role dashboard, branch and entity context, one task inbox (approvals, requisitions, POs, expected deliveries, receiving exceptions, inspections, invoice exceptions, supplier compliance, contract renewals), search, quick actions.
2. **Requisitions**: create (department, cost centre, project, category, items from catalogue or custom where permitted, quantities, estimated cost, justification, required-by date, delivery location, attachments), draft, submit, track approval, budget validation, clarification, amend or withdraw, view resulting RFQs and POs. An approved requisition cannot be changed in purpose, quantity or financial exposure without revalidation.
3. **Approvals and delegation**: personal inbox with requester, prior approvers, remaining steps, why it needs you, financial impact, authority check, history; approve, reject, request changes, delegate, escalate; bulk approval only for eligible low-risk items. Final approval for procurement, PO, budget override and invoice payment is online only. A decision may be drafted offline.
4. **Suppliers and sourcing**: search, profiles, documents, compliance, risk, contacts, performance, onboarding review, qualification, suspension alerts. RFQ: create draft, select suppliers, send invitations, view and compare quotations, delivery commitments, compliance, recommend, approve award. Full tender evaluation stays web-first initially.
5. **Purchase orders**: create from approved requisitions, review quotations, line detail with prices, discounts, tax, totals, send or resend, track acknowledgement, request changes, controlled amendments, cancel or close, partial deliveries, evidence, history. A buyer may change price during PO creation under policy, with audit, budget check and approval check. A receiver cannot change PO price.
6. **Goods receiving (highest mobile priority)**: find expected delivery (PO, supplier, delivery note, barcode); verify supplier, PO, document, destination; capture items by scan or selection with quantity and unit; inspect (accepted, rejected, damaged, quarantined); evidence (photos, delivery note, batch, serial, expiry); review discrepancies (short, over, damaged, wrong product); submit GRN (posted only after server validation); stock updated by the authoritative inventory service. Also multiple GRNs per PO, partial delivery, rejection, over-receipt tolerance, quarantine, supplier claims, immediate returns, receiver identity and timestamp, optional location evidence, signature. Evidence record keeps original file, upload status, checksum, associated record and server verification status.
7. **Inventory and warehouse**: stock views by warehouse and bin, scanning, cycle counts, transfer request, dispatch and receipt, discrepancies, reserved and in-transit stock, low stock, batch and expiry, replenishment requests. Rule: Supply Chain owns procurement decisions, Warehousing owns execution, the stock service owns quantities. No second stock ledger.
8. **Invoice matching and AP**: view invoices, linked PO and GRNs, match status, price, quantity and VAT variance, duplicate warnings, attach documents, assign exceptions, request corrections, submit authorised resolutions, see payment status. Not a payment or accounting system.
9. **Quality, returns, claims**: inspection templates, condition checklist, rating, defect categories, non-conformance, photos, quarantine, release approval, return authorisation, collection, replacement, credit notes, claim resolution, shipment tracking.
10. **Contracts and compliance**: view active contracts, terms, renewals, obligations, expiry alerts, supplier certificates, capture missing documents, request updates, qualification status. Drafting and redlining stay web-first.

## 4. Offline `[Proposed]`

| Function | Offline |
|---|---|
| View synced POs and supplier info | Yes |
| Scan barcodes, capture receiving quantities, photos (queued) | Yes |
| Complete draft inspection, capture cycle counts | Yes |
| Draft requisition; draft PO (validated on issue) | Yes |
| Confirm stock transfer | Capture locally; server confirms |
| View stock availability | Last known snapshot with stale marker |
| Approve requisition, approve PO | No final approval |
| Post GRN | No final posting |
| Finalise invoice matching or payment | No |

States shown to the user: saved locally, waiting to sync, uploading, submitted, accepted by server, rejected, needs attention, conflict. Never show "GRN Posted" for a record only on the phone.

Sync requirements: durable transactional outbox, idempotency keys, dependency ordering, exponential backoff and retry limits, resumable uploads, conflict detection, version concurrency, server timestamps, clock-skew handling, tenant-scoped local data, secure storage, logout protection, device registration and revocation. An offline GRN must be revalidated and may not silently overwrite a newer PO or stock state; discrepancies go to an explicit conflict workflow. Every offline transaction carries client transaction ID, idempotency key, tenant, user, device, entity reference, local and server timestamps, last-known entity version, evidence references, sync state and server response. Client-supplied identity is never proof of authorisation.

Local cache: user context, reference data, assigned and recent POs, permitted supplier summaries, receiving, inspection, requisition and count drafts, outbox, sync metadata, evidence metadata. Supplier banking and payment credentials are not cached for ordinary use.

## 5. Financial controls (backend-enforced) `[Proposed]`

| Scenario | App behaviour | Backend rule |
|---|---|---|
| Requisition estimate | Capture | Preserve estimate |
| Buyer converts to PO | May negotiate | Record original and revised price |
| Buyer raises price | Show budget impact | Revalidate and approve |
| Buyer lowers price | Show saving | Record price improvement |
| Receiver captures GRN | PO price read-only | Reject unauthorised changes |
| Invoice differs from PO | Show exception | Apply matching tolerance |
| Approved PO price change | Submit amendment | Keep old and new values |

Budget: ex-VAT by default, include non-recoverable VAT in consumption, inc-VAT cash commitment, configurable tenant tax treatment, revalidation on price change. The app displays three amounts where relevant (goods ex-VAT, recoverable VAT, budget consumption, cash commitment inc-VAT) but never computes approvals with its own VAT rules; it shows the backend's figures. Other controls: reservation and release, concurrent-spend protection, PO thresholds, invoice tolerance, duplicate invoice prevention, credit note matching, period restrictions, segregation of duties, supplier bank-detail change controls with independent approval, audit trail, delegation expiry.

## 6. Notifications
Event-driven and actionable (requisition submitted or awaiting approval or rejected, budget threshold or exceeded, RFQ closing, quotation received, award awaiting approval, PO issued, supplier acknowledgement, delivery due or overdue, GRN submitted, inspection failed, invoice mismatch, certificate or contract expiring, return approved). Need preferences, subscriptions, branch and department scope, escalation, deduplication, deep links, read state, delivery tracking, quiet hours. Push never carries invoice or banking details.

## 7. Supplier access `[Proposed]`
Responsive supplier web portal first, no supplier app. Capabilities: invitation and registration, compliance documents, RFQ invitations and quotation submission, PO acknowledgement, delivery scheduling and status, invoices and credit notes, returns and claims, statements. Supplier accounts are isolated from tenant employees; a supplier sees only records shared with it.

## 8. Security `[Proposed]`
Secure token handling, refresh rotation where supported, device registration and revocation, remote logout, optional biometric unlock (convenience, not authentication); tenant identity never taken from client input; branch, entity and warehouse scope checked server-side; local data partitioned by tenant and user; approval authority limits, segregation of duties, maker-checker; protected cached files and attachment access; audit logging; root and jailbreak risk assessment; POPIA-aware retention. Permission groups: requisitions, approvals, purchase orders, receiving, inventory, suppliers, RFQ and tenders, contracts, quality, invoices, budgets, reports, administration, mapped onto the central permission catalogue.

## 9. Integrations and boundaries
Approvals, Accounting, AP, Warehousing, POS and Stock, Contracting, Documents, Notifications, CRM, Projects, Fuel, Fleet. Fuel boundary: office supplies, general vehicle parts and warehouse equipment go through Supply Chain; fuel supplier contracts, depot receipts and fuel delivery stay in Fuel. A supplier can be shared; each module owns its operational records. HandyFarm boundary: when both are subscribed, Supply Chain provides advanced procurement and HandyFarm must not duplicate suppliers, POs, GRNs or stock movements (see `docs/handyfarm/HANDYFARM_ARCHITECTURE.md`).

## 10. Web versus mobile
Web owns configuration (policies, approval matrix, budget setup, templates, warehouse config, users and roles, reporting config). Mobile owns execution (receiving, counting, approvals, requisitions, supplier review). API groupings proposed in the paper (`/api/v1/mobile/scm/{me, dashboard, requisitions, approvals, purchase-orders, receiving, suppliers, rfqs, inventory, quality, invoices, documents, sync}`) are proposals; reuse existing contracts first.

## 11. Where the backend and web are today `[Verified]`
No Supply Chain mobile app exists under `HandyFlow-Apps/`. The backend (in the separate makgwalet tree, branch `scm-hardening`) and the web portal (fmwork branch `scm-web`) were extended in patch sets delivered separately; see project documents `SCM-BUILD-STATUS-2026-11-17`, `SCM-PENDING-2026-11-17` and `SCM-ENGINE-AUDIT-2026-11-18`). Existing controllers are under `/api/v1/supply-chain/**` and enforce, among others, a supply-chain module check, approval by a second person, optional sealed quotations, price-override rules and a procurement policy. A mobile app would consume those; the receiving and GRN flow, offline sync and device and push support are the main unknowns. Gaps recorded elsewhere that the app would inherit: no monetary approval limits, no supplier-facing quotation submission, no budget parent and child allocation. A mobile audit of the Supply Chain APIs for idempotency, evidence upload and receiving flows has not been done.

## 12. Roadmap `[Proposed]`

| Phase | Scope |
|---|---|
| 0 Audit | Audit Fuel app foundation, Supply Chain APIs, permissions, auth and tenant handling, missing mobile APIs, build and release setup. Output: approved architecture and backlog |
| 1 Foundation | Expo app, identifiers, environments, authentication, tenant and branch selection, permission-aware navigation, dashboard shell, notifications |
| 2 Procurement | Requisitions, task inbox, approval inbox, PO list and detail, supplier profiles, PO notifications |
| 3 Receiving and warehouse | Expected deliveries, scanning, receiving, quantity and condition, photos, delivery notes, inspection, GRN submission, stock sync |
| 4 Offline | SQLite, outbox, idempotency, attachment sync, conflicts, retry and recovery, offline receiving and inspections |
| 5 Supplier and commercial | RFQ review, quotation comparison, qualification, contract tracking, returns and claims, invoice exceptions |
| 6 Enterprise | Management dashboards, budgets and commitments, advanced notifications, multi-entity workflows, analytics, advanced approvals |
| 7 Pilot and release | Android internal test, pilot tenants, security, performance and offline tests, UAT, Play Store, then iOS |

Milestones: Internal Alpha, Procurement Alpha, Operations Beta, Offline Beta, Commercial Beta, Production 1.0, 1.x. Strongest recommendation in the paper: make goods receiving, approvals and requisitions the first priorities.

## 13. Acceptance gates and scenarios
Gates: authentication; entitlement denial; tenant isolation; permissions identical in app and backend; requisition lifecycle; approval controls (no self-approval); PO price and amendment rules; concurrent budget protection; receiving and stock reconciliation; invoice mismatch blocking; no duplicate GRNs; secure resumable attachments; notification recipient, deduplication and deep link; audit; performance; accessibility.

Scenarios to automate: (1) two users spend the same remaining budget; (2) buyer raises PO price after approval; (3) receiver tries to change PO price; (4) user loses permission while holding a cached record; (5) offline GRN submitted twice; (6) GRN arrives after PO amendment; (7) submit under another tenant; (8) invoice exceeds price tolerance; (9) unauthorised supplier bank-detail change; (10) attachment upload fails halfway; (11) user removed from branch while offline; (12) app update changes the local schema with pending transactions.

## 14. Documents the paper expects (not yet written)
PRD, architecture, screen spec, API contract, permission matrix, offline spec, sync spec, security spec, test plan, release plan, and `MOBILE_SHARED_FOUNDATION_REUSE_MATRIX.md` (first cut in `MOBILE_SHARED_FOUNDATION_REUSE_MATRIX.md` alongside this file).
