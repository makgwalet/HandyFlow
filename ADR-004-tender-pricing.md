# ADR-004: Tender pricing

**Status:** IMPLEMENTED (first slice) for the tenant's own tenders. Client-side tenders (`complianceservices`), supplier-rate import, the document builder and a pricing section in the tender PDF are NOT built; see "Not done".
**Date:** 2026-10-06. **Module:** `compliancetender`. Inspiration: the "Pricing" tab of the FlowPro Tender Management mock-up.

## 1. What changed for the user

A tender now has a **Pricing** screen (`/compliancetender/tenders/:id/pricing`) and a summary card on the tender page. The user enters a schedule of items, each with a quantity and **what it costs
them**, grouped by section; sets overhead, contingency and profit percentages and whether VAT is added; and sees the price build up. Items can be added, changed and removed while the tender is being
prepared. Once the tender is submitted the screen is read-only, and the schedule is frozen into the submission snapshot as the record of what was priced. The schedule exports to CSV.

## 2. The calculation (pure, `TenderPriceCalculator`)

Every amount is rounded to cents (half-up) at the step it is produced, so what is shown always adds up.

```
line total   = quantity x unit cost
direct cost  = sum of line totals
overhead     = direct cost x overhead %
contingency  = direct cost x contingency %
profit       = (direct cost + overhead + contingency) x profit %
price ex VAT = direct cost + overhead + contingency + profit
VAT          = price ex VAT x VAT rate        (zero when VAT is switched off)
price incl.  = price ex VAT + VAT
margin       = profit / price ex VAT          (stated as a fact, to two decimals)
```

Settings are **markups on cost** (how estimators price); the margin is shown separately because a 25% markup is a 20% margin and people confuse them. The screen states the basis of each markup in a sentence.
The server does all arithmetic; the browser never recalculates a price (it shows what the last save returned).

## 3. Decisions

1. **Cost-based schedule, not quoted rates.** A line holds what the item costs the tenant. The price quoted is derived from the markups. Tenders priced by tendered unit rates directly would need a second mode; not built.
2. **Amounts are never stored.** Only lines and settings are. The price is computed on read, so there is nothing to drift. The submission snapshot stores the computed result, frozen.
3. **VAT rate is captured per tender**, from the shared `VatRateProvider`, when pricing is first created, so a priced tender does not move if the platform rate changes. VAT can be switched off (VAT-exempt or non-VAT-vendor bidders).
4. **Lock at SUBMITTED.** Editable in DRAFT, IN_PREPARATION, INTERNAL_REVIEW and READY_TO_SUBMIT (the lifecycle has no way back from READY_TO_SUBMIT, so locking there would strand a typo); locked from SUBMITTED onwards and when
   WITHDRAWN. Every write goes through one guard, so no route edits a locked schedule. A locked write is a 409 with the status named.
5. **Access: `COMPLIANCE_MANAGE` or `COMPLIANCE_ADMIN`, even to read.** `COMPLIANCE_READ` cannot see pricing; the card is hidden for them and the server refuses. This is a choice the owner can reverse (it is one annotation per endpoint).
6. **Estimated value is not compared with the price.** The tender's `estimatedValue` does not record whether it includes VAT, so the screen shows both totals and says so rather than inventing a "x% under budget" figure.
7. **Snapshot includes pricing.** `TenderSnapshotData` gained a nullable `pricing`. Older snapshots lack it and still read (tested).

## 4. Data

V313: `tender_pricing` (one per tender: overhead/contingency/profit %, VAT switch and captured rate, notes; unique on tender; percentages checked 0 to 100 in the database as well) and `tender_pricing_lines`
(section, ref, description, unit, quantity, unit cost, sort order; non-negative checks). Both carry tenant id and optimistic-lock version.

## 5. Endpoints (`/api/v1/compliance/tenders`)

`GET /{id}/pricing`, `PUT /{id}/pricing/settings`, `POST /{id}/pricing/lines`, `PUT|DELETE /pricing/lines/{lineId}`. Each write returns the re-priced schedule.

## 6. Limitations to know about

- Reading an unpriced tender returns defaults and writes nothing; the first write creates the pricing row. Two people doing the first write at the same moment: one gets a 409 asking them to reload.
- A line's section is free text; renaming a section means editing each of its lines. Order within a section is entry order; there is no drag to reorder.
- Quantity allows three decimals and unit cost two, and more is REFUSED (screen and server), not rounded, so a price never changes between save and reload. A unit cost needing more (for example R 0.125 per item) must be entered as a total on a quantity of 1.
- Decimal comma: `12,5` reads as 12.5, and a single comma with no dot is always a decimal comma, so `1,250` reads as 1.25 (a space or a dot-and-comma form, `1 250` or `1,250.00`, reads as one thousand two hundred and fifty).
- No currency other than rand; no per-line VAT treatment (zero-rated or exempt items inside a VAT-charging tender); no versions or "what-if" scenarios.
- The tender PDF does not yet include the price. The CSV does.
- The client-side mirror (`complianceservices`) does not have pricing; under Part 8 it would be a parallel set of entities calling the same pure calculator.

## 7. Not done

Supplier rate import from Supply Chain; a rates library (reuse previous tender rates); pricing section in the PDF; the document builder's "Pricing Schedule" section; per-line VAT; scenarios; client-side pricing.
The mock-up's other screens (AI build response, document builder, tabbed tender layout, a single readiness percentage) are separate decisions: a single readiness percentage was deliberately not adopted (ADR-003: facts, not a score).
