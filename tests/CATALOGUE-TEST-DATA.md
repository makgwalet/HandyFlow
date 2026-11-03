# Catalogue test data: end to end through the screens

Three CSV files go with this guide. Everything else is typed into the screens, so every validation runs the way a user would meet it. "Expect a refusal" means the screen should show a plain message and save nothing.

Do the parts in order. Each part needs the one before it.

## Before you start
- Use a **test tenant**, or one you can throw away.
- Under **Settings**, leave "price changes need approval" **off** for now. Part 9 turns it on.
- For Parts 9 and 10 you need **two users**: one with `CATALOGUE_PRICE_WRITE`, one with `CATALOGUE_PRICE_APPROVE` / `CATALOGUE_APPROVE` (Decision 2: nobody gets these automatically, so grant them by hand).
- Have two small pictures ready (a JPG and a PNG under 5 MB), one PDF, and one `.txt` file for the refusal tests.

## 1. Units (Units screen)
Create these. If the tenant already has some, skip those.

| Code  | Name         | Kind   | Decimals |
| ----- | ------------ | ------ | -------- |
| EA    | Each         | Count  | 0        |
| BAG   | Bag          | Count  | 0        |
| BOX   | Box          | Count  | 0        |
| KG    | Kilogram     | Weight | 2        |
| M2    | Square metre | Area   | 2        |
| HR    | Hour         | Time   | 2        |
| DAY   | Day          | Time   | 0        |
| MONTH | Month        | Time   | 0        |

**Expect a refusal:** a blank code · a second `EA` (duplicate) · decimals of 5 (the limit is 4) · a code of 51 characters.

## 2. Categories (Categories screen)
Create parents first, then children under them (pick the parent when adding).

- Building Materials → Cement & Concrete, Aggregates, Bricks & Blocks
- Hardware → Fasteners, Hand & Power Tools
- Paint & Finishes
- Electrical
- Plumbing
- Safety & Consumables
- Services → Installation & Labour, Delivery & Fees, Plans, Hire

**Expect a refusal:** a blank name · a duplicate name under the same parent · a name over 100 characters · a category made its own child, or moved under one of its own children · nesting deeper than 5 levels.

## 3. Price lists (Price lists screen)
The default list (usually Retail) probably exists. Add:

| Code       | Name       |
| ---------- | ---------- |
| wholesale  | Wholesale  |
| contractor | Contractor |
| webshop    | Web shop   |

**Expect a refusal:** a blank code or name · a duplicate code · a code over 30 characters · a name over 60.

## 4. Items by import (Import screen)
1. Download the template and compare its header with `catalogue-test-items.csv` (13 columns).
2. **Upload `catalogue-test-items-invalid.csv` first**, mode "create only", and press preview. Nothing should be saved by a preview.
   - Expect **6 rows to be creatable and 12 to fail** at preview, each with its reason: no name, negative price, a price with three decimals, a price that is not a number, VAT of 150, an unknown type, an unknown status, an unknown category, a yes/no cell that says "maybe", a cost that is not a number, a repeated SKU, a repeated name.
   - Three more are caught by the server rather than at preview, so they may only fail on the real run: `BAD-009` (unit FURLONG is not set up), `BAD-014` (SKU over 50 characters) and the 60-character SKU. Report to me what you actually see.
   - `BAD-015` is a good row among bad ones. Check whether "import only the valid rows" (if offered) imports just that one.
3. **Upload `catalogue-test-items.csv`**. Expect **37 creates, 0 errors**. Run it.
4. Run the same file again in "create only" mode: every row should now be refused as already existing. In "create or update" mode every row should show **unchanged**.
5. Upload `catalogue-test-items-invalid.csv` again, now that `CEM-001` exists: the `CEM-001` row must be refused in create-only mode.

What the clean file gives you (so the other screens have something to show):
- **Items:** 37 across products, services, labour, a subscription, rentals and consumables.
- **Low margins** (for the Low margin report): `TLS-004` (about 7%), `TLS-003` (about 19%).
- **No cost:** `SRV-004`, `SRV-005`, `SRV-006`.
- **Free item:** `SRV-005` at R 0.00 (cannot be published; a good refusal test).
- **VAT exempt:** `SRV-006`.
- **Draft:** `TLS-005`.
- **Gaps for Data quality and Suggestions:** `CEM-004` has no category and no brand, and sits beside three cement items, so Suggestions should propose the cement category and, for the SKU, `CEM-nnn`. `ELC-002`, `TLS-005` and a few others have no description.
- **No pictures anywhere**, so the "no picture" report lists everything.

## 5. Items by hand (Items screen → New item)
Create these individually, to see the form validations.

| What | Values |
|---|---|
| A normal product | Name `Roof Sealant 5L`, SKU `PNT-004`, unit EA, category Paint & Finishes, price 389.00, cost 270.00, VAT 15 |
| A service | Name `Gate Motor Installation`, unit EA, type Service, price 3500.00 (no SKU: see what it does) |
| A draft | Name `Concept Item`, status Draft, price 1.00 |

**Expect a refusal:**
- blank name · blank unit · blank price (the price is required)
- negative price · negative cost · VAT of 101 · VAT of -1
- a name over 255 characters · a SKU over 50 characters
- a SKU already used (`CEM-001`) · a barcode already used (copy one from the file)
- the same name as an existing item (see whether it warns or refuses)
- cost above price: see whether it warns (it should not silently hide the negative margin)

**Edit checks:** change a price and watch the history on Product 360; archive an item and see it leave the default list; restore it.

## 6. Variants (Product 360 → Variants)
Use `TLS-001 Claw Hammer` or `PNT-003 Paint Brush 50mm`, or create `Work Boots` with options.

1. Add option **Size** with 40, 41, 42, 43 and option **Colour** with Black, Brown. Generate: expect 8 variants. Give each a SKU and price.
2. Expect a refusal: a variant SKU that already exists · two variants with identical options · a blank option value · more than 50 values in an option.
3. Check that a variant cannot be a part of a package and cannot be published on its own.

## 7. Packages (Product 360 → Package)
Create a new item `Tiling Starter Pack`, type **Package**, price 899.00. Add parts:

| Part | Quantity |
|---|---|
| `SRV-001 Tile Installation per m2` | 5 |
| `PLB-003 PTFE Thread Tape` | 2 |
| `CSM-001 Safety Gloves Leather Pair` | 1 |

**Expect a refusal:** a package inside a package · a part that has variants · more than 30 parts · quantity 0, negative, or above 9999.999 · more than three decimals · the same part twice (see what it does) · the package as a part of itself.
Then open one of the parts and check **Used in** lists the pack. Try archiving that part and see what the pack shows.

## 8. Pictures and documents (Product 360 → Media)
On `CEM-001` and `PNT-001`:
- Upload a JPG, a PNG. Make one the main picture; remove it; check another can take its place.
- Upload the PDF as a document.
- **Expect a refusal:** the `.txt` file · a file over 5 MB as a picture (10 MB as a document) · a 13th picture · an 11th document.
- Open a picture and a PDF to check they download or display and your sign-in is needed.

## 9. Price changes and approvals
1. Pick `TLS-004`. As the price writer, propose a new price on the Retail list: 69.00 effective today. With approval off it should apply at once and show in history.
2. Settings → turn **price approval on**. Propose 74.00 on `TLS-004`, effective in 7 days, with a reason. Expect it to be **pending**, with the price unchanged.
3. As the **same user**, try to approve it: expect a refusal (proposer is not the approver).
4. As the second user, **approve**: it becomes scheduled. Propose another and **reject** it with a note. Propose a third and **cancel** it.
5. **Expect a refusal on proposing:** negative price · price blank · a date in the past (see what it does) · a price on a list that is switched off.
6. Set a **minimum price** on `TLS-004` above a proposed price and check the screen warns or refuses.
7. Open the Margin view as a user **without** `CATALOGUE_PRICE_READ`: they should see the selling price but no cost and no margin.

Also try item approval: turn "new items need approval" on, create an item as a normal user (it should wait as draft/pending), approve it as the second user, and reject another.

## 10. Channels and publishing (Channels screen)
Create:

| Code | Name | Kind | Price list |
|---|---|---|---|
| `pos-main` | Main till | POS | Retail |
| `web-shop` | Web shop | Online shop | Web shop |
| `marketplace-a` | Online marketplace | Marketplace | Web shop |
| `trade-print` | Trade price sheet | Printed list | Wholesale |

**Expect a refusal on channels:** a code with capital letters or spaces (it must be lower case letters, digits and hyphens, 2 to 30 characters, starting with a letter or digit) · a duplicate code · a blank name · a 21st channel.

**Give prices on the other lists first** (Product 360 → Pricing), for at least `CEM-001`, `CEM-002`, `PNT-001`, `TLS-003`, `SRV-001`, or items will not be ready for those channels:
- Web shop: `CEM-001` 105.00, `PNT-001` 729.00, `TLS-003` 1599.00
- Wholesale: `CEM-001` 92.00, `CEM-002` 168.00

**Publish tests** (use "Check" first; it is a dry run):
- To **pos-main**: `CEM-001` (has SKU and barcode) should be ready. `TLS-005` (draft) should not: "Only active items can be published."
- To **web-shop**: `CEM-001` without a picture should fail with "It needs a picture." Upload a picture and it should pass. `ELC-002` should fail on no description and no picture. `SRV-005` (R 0) should fail on no selling price.
- To **trade-print**: `CEM-002` should pass; `CEM-003` (no wholesale price) should fail with "no selling price on this channel's price list."
- A variant on its own should be refused with "Publish the main item. Its variants go with it."
- A channel that has items cannot be deleted; switch it off instead. Check the feed afterwards (`/channels/web-shop/feed`).

## 11. Reports and suggestions
- **Suggestions:** `CEM-004` should show a category suggestion with its reason (similar cement items) and a SKU suggestion. Apply the category: it should refuse next time because the field is no longer empty. The price hint shows only for users with price access and cannot be applied.
- **Reports:** run all twelve once. Check: items without a picture lists nearly everything; low margin lists `TLS-004` and `TLS-003`; items without a cost lists the three services; the CSV downloads only for a user with export. A user without price access should see the price reports switched off.
- **Data quality:** should list `CEM-004` (no category, no brand), items with no description, the free quote item.

## 12. Bulk changes and update import
- **Bulk:** select all five paint and tool items, apply a category change, then a price rise of 5%. With approval on, expect the price part to go to approvals.
- **Update import:** upload `catalogue-test-items-update.csv` in "create or update" mode.
  - `CEM-001`: price changes to 104.50 (should go through approval if it is on).
  - `TLS-004`: price and cost change.
  - `PNT-001`: **unchanged**.
  - `NEW-001`: **created**.
- **Export:** export items, open the file, and check it re-imports as "unchanged".

## What to send me
For anything that behaves differently from what I describe here (a refusal that does not happen, a message that is unclear, a screen that breaks), send the screen, the exact values you typed and what you expected. The messages in this guide for channels and the import come from the code; the rest are the behaviour I expect, not something I have run. Nothing here has been run against a database yet.
