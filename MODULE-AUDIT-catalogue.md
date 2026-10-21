# MODULE AUDIT: Catalogue (2026-10-21)

Source of truth for what exists: `platform/.../catalogue` (13 Java files, 725 lines), migrations V6/V41/V55, `handyflow-web/src/pages/catalogue/CataloguePage.tsx` (749 lines), `api/catalogue.api.ts`.
Roadmap compared against: "HandyFlow Catalogue Module: Build, Update & Upgrade Roadmap".

## 1. What exists today

| Area | State |
|---|---|
| Entity | `CatalogueItem`: name, description, unit (free text), default price, VAT rate, category, active flag, soft delete, `@Version`. |
| Category | Flat list, name unique per tenant, sort order. No tree. |
| API | `GET/POST/PUT/DELETE /items`, `GET/POST/PUT/DELETE /categories`. Item search is by **name only**, returns **all** rows (no paging). |
| Facade | `CatalogueFacade`: `searchItems`, `findItemById`, `findItemByBarcode`. Used by Invoicing (quotes) and POS. |
| Consumers (FK to `catalogue_items`) | POS stock, Supply Chain (supplier items, inventory, PO lines and 3 more tables), Invoicing quote lines, recurring schedules. |
| Screen | One page, "Products & services": category cards, item table, add/edit/delete modals. |
| Tests | **None** for the catalogue module (backend or frontend). |

## 2. Defects found (real, in current code)

| # | Finding | Impact |
|---|---|---|
| D1 | `barcode`, `sku`, `vat_exempt` columns exist in the table (V41, V55) but are **not on the entity**. There is no screen or endpoint that can set a barcode or SKU. | POS barcode scanning (`findItemByBarcode`) can never match anything created through the app. |
| D2 | DB constraint `UNIQUE (tenant_id, name)` also counts soft-deleted rows, but the service only checks non-deleted rows. | Delete "Concrete 20MPa", add it again: the service says OK, the database refuses with a 500. |
| D3 | `updateItem` does not check for a duplicate name. | Renaming an item to an existing name hits the same DB error. |
| D4 | Delete category soft-deletes **every item in it** with no warning, and no check for use in quotes, POS stock or purchase orders. | One click can remove items that other modules reference. |
| D5 | `deletedBy` is always null (comment says "later step"). | No record of who removed an item. |
| D6 | Two overloaded `update(...)` methods on the entity; the one the service calls does not set `updatedAt`. | Edits rely on the `@PreUpdate` hook only. Fragile. |
| D7 | Unit of measure is free text; the UI offers about 60 hard-coded units. | Same unit spelled differently across items; no reporting by unit. |
| D8 | Every write uses `SETTINGS_MANAGE`; reads use `INVOICE_CREATE` / POS permissions. No catalogue-specific permissions. | Cannot give someone catalogue editing without full settings access; no approval or price separation. |
| D9 | No audit trail or price history. A price edit overwrites silently. | Cannot answer "who changed this price and when". |
| D10 | `GET /categories` needs `INVOICE_CREATE`, so a POS-only user cannot load categories. | Likely a 403 on POS screens that show categories. |
| D11 | Header comments in `CatalogueService` and `PosService` disagree on whether `vat_exempt` is used. | Dead column, confusing for the next developer. |

## 3. Gap matrix against the roadmap

P0 = foundation, P1 = next stage, P2 = later. "Built" means working end to end today.

| Engine (roadmap) | Priority | Status | Gap |
|---|---|---|---|
| Product and service master | P0 | Partial | No item type (product / service / package / rental...), no lifecycle status, no brand, no internal code, no cost price. Description only. |
| SKU and identifier | P0 | Not built | Columns exist (D1) but unmapped; no uniqueness rule, no external identifiers, no aliases, no history. |
| Category and attribute | P0 | Partial | Flat categories only. No tree, no attribute definitions. |
| Variant management | P0 | Not built | |
| Pricing and tax classification | P0 | Minimal | One default price and a VAT %. No price lists, effective dates, minimum price, margin, or history. |
| Tenant security and audit | P0 | Partial | Tenant scoping is correct in every query. No audit, wrong permissions (D5, D8, D9). |
| Search and filtering | P0 | Minimal | Name only, unpaged, no filters or sort. |
| Data quality | P1 | Not built | |
| Import and export | P1 | Not built | (POS/CRM have their own imports; Catalogue has none.) |
| Media and documents | P1 | Not built | Should reuse the shared Document Service. |
| Approval and workflow | P1 | Not built | |
| Bundle and package | P1 | Not built | |
| Publication and channel | P1 | Not built | |
| Integration and sync | P1 | Partial | Facade exists for Invoicing and POS only; no events, no delta API. |
| Reporting | P1 | Not built | |
| Enrichment / intelligence | P2 | Not built | |

Screens: roadmap lists 6; **1** exists (a basic version of item list). Reports: roadmap lists 12; **0** exist.

## 4. Recommended build order (each slice = one reviewed patch with tests)

1. **Slice 1: Identity and integrity (P0).** Fixes D1 to D6, D8, D9 in part.
   Map sku, barcode and VAT-exempt; item type and lifecycle status (draft / active / suspended / discontinued / archived); partial unique indexes so deleted items stop blocking names, SKU and barcode; duplicate checks on create **and** update; server-side paged search by name, SKU, barcode, category, status; `deletedBy` filled from the signed-in user; category delete refuses when items are in use; CATALOGUE_* permissions (read, write, archive, price) added alongside the old ones so no existing role breaks; audit events for create, update, price change, status change, delete. Screen: SKU, barcode, type, status fields; paged table with filters.
2. **Slice 2: Units, categories tree, attributes (P0).** Unit-of-measure table; category parent/child; attribute definitions per category with validation.
3. **Slice 3: Pricing (P0).** Price lists, effective dates, minimum price, cost and margin (gated by CATALOGUE_PRICE_READ), price history; Invoicing keeps its price snapshot.
4. **Slice 4: Variants (P0).** Parent item plus variant matrix with its own SKU and barcode.
5. **Slice 5: Command dashboard and Product 360 workspace (P0/P1 UI).**
6. **Slice 6: Import and bulk update, with dry run (P1).**
7. **Slice 7: Data quality, approvals, completeness (P1).**
8. **Slice 8: Media, documents, bundles, publishing, reports (P1).**
9. P2 items only after the above are in use.

## 5. Decisions needed from the owner (none block Slice 1)

- Should approval (maker-checker) be **on by default** for price changes, or opt-in per tenant? Slice 3 depends on it.
- Existing roles: add the new CATALOGUE_* permissions to roles that currently have SETTINGS_MANAGE automatically (recommended), or leave to manual assignment?

## 6. Not verified yet

Nothing in this audit was run against a database; findings D2 and D3 come from reading the migration against the service code, and should be confirmed by the Slice 1 tests.

## 7. Slice 1 status (identity and integrity): built, not yet run against a database

| Finding | Status |
|---|---|
| D1 sku / barcode / vat_exempt not on the entity | Fixed: mapped, editable, searchable; POS barcode scan now matches sellable items only. |
| D2 / D3 deleted names block re-use; rename can hit the database error | Fixed: V476 swaps the table constraints for partial unique indexes (live rows only); create **and** update check name, SKU and barcode and answer 409 with the field's own message. |
| D4 category delete removes its items | Fixed: refused with a count while the category has items. |
| D5 `deletedBy` always null | Fixed for items and categories. |
| D6 two `update(...)` overloads, one skipping `updatedAt` | Fixed: one `update`, one `apply`. |
| D7 unit free text | Not yet (Slice 2). |
| D8 only SETTINGS_MANAGE | Partly: CATALOGUE_READ / WRITE / ARCHIVE / PRICE_READ / PRICE_WRITE added; SETTINGS_MANAGE still works everywhere. Approve / import / export / publish / admin come with their slices. |
| D9 no audit trail or price history | Audit events for create, edit, price change, status change, delete, with old and new values; History button on each item. Full price lists and effective dates are Slice 3. |
| D10 categories need INVOICE_CREATE | Fixed: POS and catalogue readers can load them. |
| D11 conflicting `vat_exempt` comments | Fixed: exempt is now a real flag that forces a 0% rate. |
| Extra: `ConflictException` returned HTTP 500 (no handler) | Fixed in `GlobalExceptionHandler` (409). This affects 10 other files that throw it. |

New: item type, lifecycle status (draft / active / suspended / discontinued / archived) with reasons, brand, cost price and margin (shown only with CATALOGUE_PRICE_READ), edit conflict detection (version), server-side paged search by name, SKU, barcode or brand with category / status / type filters and sorting, a Categories screen with item counts.

Behaviour changes to know about:
- `GET /catalogue/items` (used by quotes, recurring schedules, POS, supply chain pickers) now returns **active items only**. Suspended, discontinued, draft and archived items disappear from pickers, by design. Existing quotes and invoices still read them by id.
- Deleting a category no longer deletes its items.
- SKU is stored upper-case; name, SKU and barcode uniqueness ignore case.

Tests: 10 rules tests run and pass (standalone, Maven unavailable here); 17 service tests (Mockito) and 31 front-end tests written; the front-end tests pass, the service tests have **not been run**. The service, controller and repository code could not be compiled here (no Spring jars): the first `mvn` build will show any slip.
