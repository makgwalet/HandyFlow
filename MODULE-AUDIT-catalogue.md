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

## 9. Slice 3 status (pricing): built, not yet run against a database

| Item | Status |
|---|---|
| Price lists | `catalogue_price_lists` per tenant. The default list (Retail) is the item's `default_price`, so Invoicing, POS and Supply Chain read exactly what they read before. Other lists (Wholesale, Trade, ...) hold their price as the latest applied change. Code is fixed once created; lists are switched off, or deleted only while they have no price history. |
| Effective dates | A price change has an effective date (today or later, at most two years ahead). A change dated today is in force at once; a later one is SCHEDULED and a nightly job (00:05 South African time, catches up missed nights) puts it into force. |
| Minimum price | Optional floor per item (`min_price`). No list can be priced below it, checked when proposing, again when approving, and again when a scheduled change is applied (a change that has fallen below a raised minimum is cancelled with a note, not applied). |
| Cost and margin | Shown in the pricing screen only with CATALOGUE_PRICE_READ. Margin is on price, one decimal. VAT-inclusive price shown per list. |
| Price history | Every price change is a row in `catalogue_price_changes` (old, new, effective date, reason, who proposed, who decided, status) and also a line in the item's history. A price edited on the item form (allowed only when approval is off) also leaves a row. Invoices keep their own price snapshot; nothing here rewrites a past invoice. |
| Owner decision 1: approval setting | `catalogue_settings.price_approval_required`, **per tenant**, not a global setting. Existing tenants were given `false` by V479 (current behaviour kept). A tenant with no row, which is every tenant created from now on, is treated as `true`. Changed on the Price lists screen by CATALOGUE_ADMIN; each change is written to history. When on: a price change waits as PENDING; the proposer cannot approve or reject their own change (enforced in the service, returns 403, whatever permissions they hold); approving needs CATALOGUE_PRICE_APPROVE; a rejection needs a reason; the price field on the item form is read-only (prices change through Pricing). When off: a change is applied or scheduled at once. Permissions, validation, minimum price, history and date rules apply in both modes. |
| Permissions | View prices/cost/margin: CATALOGUE_PRICE_READ. Propose, set minimum: CATALOGUE_PRICE_WRITE. Approve/reject: CATALOGUE_PRICE_APPROVE. Price lists and the setting: CATALOGUE_ADMIN. V479 grants none of the price permissions (decision 2); see the grant example in the delivery note. |

Deliberate deviation to know about: the approval step is built inside the Catalogue (a status on the price change, permission-gated, proposer is never the approver) and **not** on the platform's generic approvals engine. That engine's module may depend only on `shared` and has no price-change type, so using it would have needed a cross-module dependency the architecture rules forbid; the Catalogue's own workflow stays inside its module boundary. If you later want approvals from several modules in one inbox, this is the place to connect them.

Known limits:
- Thresholds (only approve above X%, above a rand value, or by margin impact) are not built; the data needed (old price, new price, cost) is already on each change. Today the rule is all-or-nothing per tenant.
- Only one change may wait for approval per item and list; a second proposal is refused until the first is decided or withdrawn.
- A change cannot be approved after its date has passed; it must be rejected and proposed again.
- The nightly job applies all due changes in one transaction; one failure leaves them all for the next night and is logged.
- A tenant's first open of the price lists creates the default list; two simultaneous first opens could fail once.
- New tenants default to approval required, so a new tenant with one user needs a second user, or an administrator switching the setting off, before a price can change.
- Quotes, invoices and POS still read only the default price. Choosing another list when invoicing is not built.

Tests: 11 pricing rules tests run and pass (standalone). 37 service tests (Mockito: 34 in the new pricing service, 3 added to the catalogue service) are written but **not run**. Front end: 94 catalogue tests pass (28 new), lint and type check clean for these files. The backend was not compiled with Spring here (only syntax-checked); V479 has not been run against a database.

## 10. Slice 4 status (variants): built, not yet run against a database

| Item | Status |
|---|---|
| Model | A variant **is a catalogue item** (own id, SKU, barcode, price, cost, status, history) with `parent_id` set, named "Parent — Large / Red". Quotes, invoices, POS and Supply Chain therefore sell variants with no change, scanning a variant's barcode finds that variant, and Slice 3 price lists, minimum price and approval apply to each variant as they do to any item. One level only (a variant cannot have variants). |
| Parent | A parent that has live variants is not offered in pickers, POS scan or the facade's search (worked out from its variants, not stored). Its own price is only the default for new variants. Existing documents that reference the parent keep working (lookup by id is unchanged). |
| Options | Up to 3 options per parent (Size, Colour...), up to 20 values each, up to 100 variants created at once and 200 per parent. Values are remembered per option (`catalogue_variant_options`). Once variants exist, later additions must use the same options (values can be added). |
| Matrix | Screen: name the options and list values; the combinations are shown with a suggested SKU (parent SKU plus three letters of each value) and the parent's price; tick the ones to create, edit SKU, barcode and price, create. Combinations that already exist are left out. A combination is unique per parent ignoring case (database index plus service check). |
| Following the parent | Renaming the parent renames its variants; its category, unit, brand, type and VAT are copied to them. Their SKU, barcode, price and cost stay their own. Suspending, discontinuing or archiving a parent does the same to its variants where that move is allowed (a draft variant is left alone). Deleting a parent deletes its variants. A variant cannot be activated while its parent is not active. |
| Editing a variant | SKU, barcode, price, cost through the parent's Variants dialog; this calls the ordinary item update, so duplicate checks, version check, price history and the "prices change through approval" rule all apply. Deleting a variant is the normal item delete; when the last one goes the parent is a normal item again. |
| Lists | The catalogue list shows top-level items with "N variants"; a search also finds variants (so a variant's SKU or barcode finds it), shown with their label. |

Known limits:
- Attribute values (Slice 2) are held on the parent only; variants do not carry their own, and required-attribute checks are not applied to variants.
- Converting an existing item into a parent removes it from pickers from then on; if it was being sold directly, sell one of its variants instead (create a variant for the current form of the product).
- Option names and values cannot be renamed or removed once variants exist; delete the variants and recreate them.
- The stock and POS screens show the variant's full name ("Tee shirt — S / Red"); they do not show the option breakdown.
- Bulk price change across all variants of a parent is not built (each variant is priced on its own; Slice 6 import covers bulk).

Tests: 8 variant rules tests run and pass (standalone). 19 service tests (Mockito: 14 in the new variant service, 5 added to the catalogue service) are written but **not run**. Front end: 113 catalogue tests pass (19 new), lint and type check clean for these files. The backend was not compiled with Spring here (syntax-checked only); V480 has not been run against a database.

## 11. Slice 5 status (dashboard and Product 360): built, not yet run against a database

| Area | What it does |
|---|---|
| Dashboard | New first screen of the module (`GET /api/v1/catalogue/dashboard`, `CATALOGUE_READ`). Totals: items (top-level, not archived), products and services, variants, active SKUs (active and sellable, so a parent with variants is not counted), drafts, incomplete records, discontinued (and in the last 30 days). Items over the last 12 months, items per top-level category (with "No category"), the six most recently changed items and the ten latest activity lines. Quick actions: Add item (writers), Review price changes (price users). |
| Price queue | "Price changes waiting / scheduled" is sent only to users with a price permission; others get nothing and the tile is not drawn. |
| Product 360 | "Open" on any item (also `?item=ID`). Header: SKU, status, type, category path or "Variant of X", approval state, completeness ring. Tabs: Overview (details and what is outstanding), Attributes, Variants, Pricing (price users only; hidden for items sold through variants), History. Changing variants or prices opens the existing dialogs from the tab. `GET /items/{id}/overview` backs it. |
| Completeness | Score = share of weighted checks passed (`CompletenessRules`): description 20, category 15, SKU 20, price above zero 20, and for goods (product, consumable) also barcode 15 and brand 10. The dashboard "incomplete records" count uses the same checks in SQL for items still in use (not archived or discontinued, not parents with variants). Required attributes that are empty are listed as outstanding but do not change the score or the dashboard count. |
| History leak fixed | The item history (and the new activity feed) used to show cost and the price-approval trail to anyone with read access. Without `CATALOGUE_PRICE_READ`, price workflow events now show no detail and cost / minimum price segments are removed (`HistoryRules`). |

Not built, and why it is not on the screens:
- No tiles for failed imports or sync errors (there is no import or sync yet; Slice 6), and no "drafts awaiting approval" (approval exists for price changes only; item approval is Slice 7).
- Media, Relationships, Publishing and Audit tabs are not built (Slice 8). History is the audit trail for now.
- Growth is a plain bar list of totals, not a chart library; the category view is bars, not a donut.
- Counts are computed on request; they are cheap for normal catalogues but are not cached.

Tests: completeness (7), history redaction (7) and dashboard rules (6) pass standalone. 6 service tests (Mockito) are written but **not run**. Front end: 126 catalogue tests pass (13 new), lint and type check clean. The backend was not compiled with Spring here and the new JPQL queries have not run against a database.

## 12. Slice 6 status (import, export, bulk change): built, not yet run against a database

| Area | What it does |
|---|---|
| Import | Section "Import & export" (needs `CATALOGUE_IMPORT` or `SETTINGS_MANAGE`). The browser reads a CSV (comma, semicolon or tab; quotes and a byte-order mark are handled; header names such as "Item Code", "Selling Price", "VAT %" are recognised) and sends its text. **Check file** returns a dry run, row by row: new, update, no change, or a problem with the reason. **Import** applies the same plan. Up to 2,000 rows and 2 MB per file. |
| Matching | By SKU (case does not matter). Empty cells on an existing item leave it as it is. Modes: add new only, update existing only, or both. A file that repeats a SKU, name or barcode, or clashes with an existing item, is flagged on the row. Categories are matched by full path ("Building > Cement") or by name when only one category has it; unknown categories are a problem (nothing is created silently). |
| Rules kept | Each row is saved through the ordinary create / update (duplicate checks, version check, required attributes, history), one row at a time, so a row that fails when saved is reported and the rest still go in. **Price changes to existing items are refused when price approval is on**; new items may carry a price. Cost is accepted only from users with `CATALOGUE_PRICE_WRITE`. Variants: name, category, unit, brand, type and VAT columns are ignored (they follow the main item). The status column is used for new items only. |
| Problems | The import will not run while rows have problems unless "leave out" is ticked; left-out rows and rows that failed on saving are listed with their reason. Every applied import is recorded (`catalogue_import_jobs`, V481; dry runs are not). |
| Export / template | `GET /export` (needs `CATALOGUE_EXPORT` or `SETTINGS_MANAGE`): all items that are not archived, same columns as the template, so an export can be edited and imported back. Cost is left blank for users without `CATALOGUE_PRICE_READ`. Text starting with `= + @ -` is written with a leading apostrophe so spreadsheets do not run it as a formula, and read back without it. |
| Bulk change | Tick items in the list, **Change selected**: status (with a reason where one is needed), category, or prices by a percentage (-90 to +500, rounded to the cent, from a date, with a reason). Up to 200 items. **Check first** shows what would happen to each item; **Apply** then makes the change item by item through the normal rules. Price changes go through the price workflow, so with approval on each one waits for an approver; the minimum price is enforced. Items sold through variants are refused for price (select the variants). |

Limits to know:
- The dry run checks the rules it can see (duplicates, permissions, status moves, minimum price). A few checks only run when saving, such as required attributes for activation, so a row can pass the check and still fail when applied; that is reported as "could not be saved".
- No attributes, variants or price-list columns in the file yet; per-list prices and variant creation are not imported.
- The file is held in memory and processed in one request; large catalogues should be split (2,000 rows).
- There is no undo for an applied import beyond editing the items again; the item history shows every change.
- Export of the whole catalogue is allowed to anyone with the export permission; it is logged in the application log, not in the item history.

Tests: 15 import rules tests and 3 bulk rules tests pass standalone. 16 service tests (Mockito) are written but **not run**. Front end: 139 catalogue tests pass (13 new), lint and type check clean. The backend was not compiled with Spring here and V481 and the new queries have not run against a database.
