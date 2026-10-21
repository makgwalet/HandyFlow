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

## 5. Owner decisions (2026-10-21)

**Decision 1: price-change maker-checker is configurable per tenant.**
Setting `CATALOGUE_PRICE_APPROVAL_REQUIRED` (true/false), stored per tenant, not as a global application setting. Existing tenants default to **false** (today's behaviour is kept); new tenants default to **true**; an authorised tenant administrator can change it. When it is on, the person who proposed a change cannot approve it. Permissions, validation, audit history and effective-date rules always apply. Later: thresholds by percentage change, monetary value or margin impact. **Built in Slice 3** (it needs the price-change workflow), on the existing approvals engine rather than a second mechanism. Changing the setting needs CATALOGUE_ADMIN, which is separate from CATALOGUE_PRICE_APPROVE (managing settings is not approving prices).

**Decision 2: controlled automatic migration of Catalogue permissions** (V477, done).

| Existing authority | Receives | Not implied |
|---|---|---|
| SETTINGS_MANAGE | CATALOGUE_READ, WRITE, ARCHIVE, IMPORT, EXPORT, PUBLISH | APPROVE, PRICE_APPROVE, ADMIN, PRICE_READ, PRICE_WRITE |
| Role named `ADMIN` (per tenant) | CATALOGUE_ADMIN | |
| (no pricing-administration permission exists) | nobody gets PRICE_READ / PRICE_WRITE automatically | |
| (no catalogue approval authority exists) | nobody gets APPROVE / PRICE_APPROVE automatically | |

How this was read, so it can be corrected:
- **Pricing administration:** none exists in the system, so PRICE_READ and PRICE_WRITE are assigned by hand. V476 had granted both to every SETTINGS_MANAGE role; V477 removes exactly those grants. Effect for existing users: they stop seeing cost price and margin until a role is given CATALOGUE_PRICE_READ. This is the one visible behaviour change.
- **Approval authority:** the only generic one is `APPROVALS_MANAGE`, which configures approval rules; it does not mean "may approve a price". Granting approval to rule-configurers would blur the separation the owner asked for, so APPROVE / PRICE_APPROVE start empty. V477 carries the one-line SQL to grant them to a chosen role.
- **Tenant administration:** the role named `ADMIN` (the same convention V41 and V246 use). A tenant whose administrators use a differently named role gets no CATALOGUE_ADMIN until it is assigned, which means they cannot manage units or attributes yet.
- Migration is additive apart from the price withdrawal above; manual grants and memberships are untouched; it can run twice. `CATALOGUE-MIGRATION-CHECKS.sql` verifies the result per tenant (run it once Flyway has applied V476 to V478).
- Future Catalogue permissions are not granted by implication: each new permission is added in its own slice with its own mapping row in this table.

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

## 8. Slice 2 status (units, category tree, attributes): built, not yet run against a database

| Item | Status |
|---|---|
| D7 unit is free text | Fixed. `catalogue_units` per tenant (code, name, kind, decimals, on/off). Items must use a unit from the list; the code stays as text on the item so POS, Supply Chain and Invoicing keep working unchanged. The unit list is filled from the units the tenant's items already use (spellings merged, most common wins) plus a standard set of 12; other tenants get the standard set on first open. A unit in use cannot be deleted, only switched off; an item may keep a unit that was switched off later. |
| Category tree | `parent_id`, up to 5 levels, no loops (a category cannot move under itself or its own sub-categories), names unique among siblings only. Filtering by a category includes everything below it. Deleting a category is refused while it has items, sub-categories or attributes. |
| Attribute definitions | Types text, number (min/max), yes/no, date, pick-from-a-list. Per category (inherited by sub-categories) or for every item. Code and type are fixed after creation. Required attributes are enforced for any item that is not a draft, including when a draft is activated. An attribute with values is switched off, not deleted. |
| Item form | Unit is a list from the tenant's units; the attribute fields that apply to the chosen category appear and are checked in the browser and again on the server. |
| Permissions | Units and attributes are managed with CATALOGUE_ADMIN; anyone who can read the catalogue can read them. The two Setup screens appear in the sidebar only for CATALOGUE_ADMIN. |

Behaviour changes to know about:
- Creating or editing an item with a unit that is not in the tenant's list is refused with a message. The standard set is created automatically, so "each", "hour", "kg" and so on work straight away.
- Existing items' unit text is rewritten to the canonical spelling (e.g. "EACH" and " each" become "each").
- Category names are now unique per parent; V476's tenant-wide index is replaced (no existing data can violate the new one).
- Quantity decimals on a unit are stored and shown but not enforced yet (quotes, POS and purchase orders still accept what they accepted before).

Tests: 19 rules tests run and pass (standalone, Maven unavailable here). 45 service tests (Mockito: 19 in the new structure service, 26 in the catalogue service including the Slice 1 ones I changed) are written but **not run**. Front end: 66 catalogue tests pass, lint clean. Backend service, controller, entity and repository code has not been compiled (no Spring jars here); V477 and V478 have not been run against a database.
