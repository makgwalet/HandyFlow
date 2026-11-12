# Fuel: Internal Test Script (all four divisions)

Scenario: **Highveld Fuel Distributors (Pty) Ltd**, a company that wholesales and delivers fuel, runs two retail forecourts and a truck stop, from one depot.
Date of run: ______  Tester: ______  Build/commit: ______

**Honest note:** the backend has compiled and started on your machine, but the new Java tests and every screen below have not been walked through by me. "Expected" comes from reading the code and screens. Where I'm unsure it says **(record)**: do what's described and write down what the system answers. A surprising answer is a finding, not a tester error.

All data is fictional. Rates, prices, tolerances and tax figures below are **test inputs you key in**, not advice and not values the system supplies.

Attachment files are in `fuel-test-pack/`: `1-compliance/`, `2-supplier-invoices/`, `3-supplier-bank/`, `4-proof-of-delivery/`, `5-negative-files/`, `6-pos-import/`. Rules for every upload: 5 MB maximum; photos and signatures must be JPEG, PNG or WebP; documents may also be PDF; the system checks the real content, not the extension.

Order matters: sections 1 to 3 create the data the later sections use. Dates assume **today = 12 Nov 2026**; shift them if you run on another day, keeping the gaps.

---

## 0. Test users (create under Users & roles; password of your choice)

| Code | Name | Permissions |
|---|---|---|
| U-ADM | Alice Admin | FUEL_MANAGE, FUEL_CONFIGURATION_MANAGE, FUEL_MARGIN_READ, FUEL_COMPLIANCE_MANAGE, FUEL_COMPLIANCE_VERIFY, FUEL_SUPPLIER_APPROVE, FUEL_SUPPLIER_BANK_EDIT, FUEL_SUPPLIER_BANK_READ |
| U-OPS | Olivia Ops | FUEL_READ, FUEL_OPERATIONS_MANAGE, FUEL_STOCK_MANAGE, FUEL_STOCK_ADJUST, FUEL_PURCHASING_MANAGE, FUEL_SUPPLIER_INVOICE_MANAGE |
| U-APR | Andile Approver | FUEL_READ, FUEL_STOCK_ADJUST_APPROVE, FUEL_SUPPLIER_BANK_VERIFY, FUEL_SUPPLIER_APPROVE, FUEL_EVIDENCE_VERIFY, FUEL_VARIANCE_REVIEW |
| U-SAL | Sipho Sales | FUEL_READ, FUEL_CUSTOMERS_MANAGE, FUEL_ORDERS_MANAGE, FUEL_PRICING_MANAGE |
| U-CRD | Cora Credit | FUEL_READ, FUEL_ORDERS_MANAGE, FUEL_CREDIT_OVERRIDE |
| U-DSP | Dumisani Dispatcher | FUEL_READ, FUEL_DISTRIBUTION_MANAGE, FUEL_TRANSPORT_MANAGE, FUEL_DISPATCH |
| U-DRV | Thabo Driver | FUEL_DRIVER (plus FUEL_READ if the menu is hidden) |
| U-ATT | Lerato Attendant | FUEL_FORECOURT |
| U-SUP | Sandile Shift-Supervisor | FUEL_READ, FUEL_FORECOURT, FUEL_SHIFT_SUPERVISE |
| U-FCM | Fiona Forecourt-Manager | FUEL_READ, FUEL_FORECOURT_MANAGE, FUEL_SHIFT_SUPERVISE |
| U-TS | Tebogo Truck-Stop | FUEL_READ, FUEL_TRUCKSTOP_MANAGE |
| U-RO | Rita Read-only | FUEL_READ |
| U-NONE | Nobody | no FUEL permission |

Use U-ADM for setup. If a permission name is refused by the user screen, note it and use FUEL_MANAGE instead.

---

## 1. Setup (as U-ADM)

### 1.1 Setup wizard and divisions
| # | Key in | Expected |
|---|---|---|
| 1.1.1 | Fuel > Setup & divisions. Try **Next** with no division ticked | Refused: choose at least one division |
| 1.1.2 | Tick all four divisions. Business size `MEDIUM`, structure `ONE_ENTITY`. Next, then **Activate Fuel** | Setup active; menu shows Retail, Distribution, Truck stop groups |
| 1.1.3 | Turn **Truck Stop** off with reason `Test` (min 5 characters is not required here; record), then turn it on again | Menu entries for Truck stop disappear then return. Records kept |
| 1.1.4 | Sign in as U-NONE and open `/fuel` | Module hidden or refused |

### 1.2 Legal entity
| # | Key in | Expected |
|---|---|---|
| 1.2.1 | Fuel > Legal entities > Add: name `Highveld Fuel Distributors (Pty) Ltd`, registration `2019/456789/07`, tax no `9876543210`, VAT no `4123456789`, address `12 Refinery Road, Germiston 1401` | Created |
| 1.2.2 | Add second: name `Highveld Retail (Pty) Ltd`, registration `2021/222333/07` | Created |
| 1.2.3 | Add with a blank name | Refused |

### 1.3 Settings (Fuel > Settings)
| # | Field | Key in |
|---|---|---|
| 1.3.1 | Driver cost per hour (R) | `180` |
| 1.3.2 | Vehicle cost per km (R) | `9.50` |
| 1.3.3 | Fixed cost per delivery (R) | `250` |
| 1.3.4 | Overhead (% of revenue) | `4` |
| 1.3.5 | Typical trip hours | `3` |
| 1.3.6 | Supplier delivery tolerance (%) | `2` |
| 1.3.7 | Stock adjustments needing a second person (litres) | `100` |
| 1.3.8 | Load variance tolerance (%) | `0.5` |
| 1.3.9 | Forecourt cash tolerance (R) | `10` |
| 1.3.10 | Tick **require a separate person to close shifts** | ticked |
| 1.3.11 | Price change needing approval (%) | `5` |
| 1.3.12 | Minimum margin (%) | `3` |
| 1.3.13 | Tick **require supplier approval** | ticked |
Expected: all saved and reappear after refresh. Negative tests: Supplier delivery tolerance `11` refused (0 to 10); Overhead `101` refused; Typical trip hours `0` refused (0.1 to 24).

### 1.4 Depots (Stock & Ledger > Depots)
| # | Key in | Expected |
|---|---|---|
| 1.4.1 | Add: code `GRM`, name `Germiston Depot`, address `12 Refinery Road, Germiston` | Created |
| 1.4.2 | Add: code `SBK`, name `Sandton Forecourt`, address `1 Rivonia Road, Sandton`, **Supplied by** = `GRM - Germiston Depot` | Created, list shows Supplied by Germiston Depot |
| 1.4.3 | Add: code `RBK`, name `Rustenburg Forecourt`, address `8 Beyers Naude Drive, Rustenburg`, Supplied by `GRM` | Created |
| 1.4.4 | Add another with code `GRM` | Refused, code in use |
| 1.4.5 | Add code `BAD CODE!` | Refused (letters, digits, dot, dash, underscore) |
| 1.4.6 | Edit `GRM`, set Supplied by = `SBK` | Refused: would loop (SBK is supplied by GRM) |
| 1.4.7 | Edit `SBK`, set Supplied by = `SBK` | Not offered in the list, or refused |
| 1.4.8 | Edit `RBK`, clear Supplied by, save, then set it to `GRM` again | Both saved |

### 1.5 Tanks (Fuel > Tanks > Add tank)
| Name | Fuel | Capacity L | Location | Low % |
|---|---|---|---|---|
| `D1 Diesel Main` | DIESEL | 50000 | Germiston bay 1 | 20 |
| `P1 Petrol 95 Main` | PETROL | 30000 | Germiston bay 2 | 20 |
| `SD-1 Sandton Diesel` | DIESEL | 20000 | Sandton forecourt | 15 |
| `SP-1 Sandton Petrol` | PETROL | 20000 | Sandton forecourt | 15 |
| `RD-1 Rustenburg Diesel` | DIESEL | 15000 | Rustenburg forecourt | 20 |

Then Stock & Ledger > tank **Depot & safety**: D1 and P1 to `GRM`, safety stock `5000` and `3000`; SD-1 and SP-1 to `SBK`, safety `2000` each; RD-1 to `RBK`, safety `1500`.

| # | Case | Expected |
|---|---|---|
| 1.5.1 | Key all five and place them | Created, placed |
| 1.5.2 | Capacity `0` | Refused (greater than zero) |
| 1.5.3 | Low threshold `101` | Refused (0 to 100) |
| 1.5.4 | Blank name | Refused |
| 1.5.5 | Safety stock `-1` | Refused |

### 1.6 Suppliers (Fuel > Suppliers)
| Name | Contact | Phone | Email | Account no |
|---|---|---|---|---|
| `Karoo Petroleum Supply (Pty) Ltd` | Johan Pretorius | 0115550177 | `orders@karoopetroleum.example.co.za` | `HFD-KPS-01` |
| `Free State Fuels` | Mpho Dube | 0515550123 | `sales@fsfuels.example.co.za` | `HFD-FSF-07` |

| # | Case | Expected |
|---|---|---|
| 1.6.1 | Key both | Created; with supplier approval on, they may show **Awaiting approval** |
| 1.6.2 | As U-APR approve `Karoo Petroleum Supply`. Leave `Free State Fuels` unapproved | Karoo approved. (record: can a PO be raised against the unapproved one? Expect refused) |
| 1.6.3 | Blank name | Refused |

**Bank details (two-person).** As U-ADM open Karoo > Bank details > Propose: holder `Karoo Petroleum Supply (Pty) Ltd`, bank `First National Test Bank`, branch `250655`, account `62000000123`, type `CURRENT`, reason `New supplier`. Reference letter: `3-supplier-bank/Bank-Confirmation-Letter-KPS.pdf` (there is no upload field; it is only what the verifier checks against).

| # | Case | Expected |
|---|---|---|
| 1.6.4 | Propose as above | Status Proposed, number masked |
| 1.6.5 | Same user (U-ADM) tries to verify | Refused (a second person must verify) |
| 1.6.6 | U-APR verifies with note `Matches bank letter` | Verified. Note under 5 characters (`ok`) refused |
| 1.6.7 | U-RO opens the supplier | Number stays masked. U-ADM (has FUEL_SUPPLIER_BANK_READ) can reveal it |
| 1.6.8 | Propose a changed account number `62000000999`, then verify it as U-APR | Old details kept as history, new current |

### 1.7 Customers (CRM first, then Fuel > Customer accounts)
Create these in **CRM > Customers** first: `Gold Mine Ltd`, `Rooiberg Farms`, `Held Farm Co`.

| Customer | Type | Credit limit R | Terms days | Licence no | Notes |
|---|---|---|---|---|---|
| Gold Mine Ltd | Mining company | 250000 | 30 | `PERMIT-GM-001` | Main test customer |
| Rooiberg Farms | Commercial farm | 80000 | 14 | | Small credit limit |
| Held Farm Co | Commercial farm | 50000 | 30 | | Used for the credit hold |

| # | Case | Expected |
|---|---|---|
| 1.7.1 | Key all three | Created |
| 1.7.2 | Credit limit `-1` | Refused |
| 1.7.3 | Payment terms `366` | Refused (0 to 365) |
| 1.7.4 | Same customer twice | Refused (record the message) |
| 1.7.5 | Put **Held Farm Co** on credit hold, reason `Overdue 60 days` | Chip Credit hold shown |
| 1.7.6 | Open Gold Mine > Portal access: invite `buyer@goldmine.example.co.za` | Invite listed as Invite sent (Fuel portal is a separate script) |

**Sites** (account > Add site):
| Account | Site | Address | Contact | Phone | Distance km | Latitude | Longitude | Hours | Instructions |
|---|---|---|---|---|---|---|---|---|---|
| Gold Mine Ltd | Pit 1 | Randfontein Road, Randfontein | P. Dlamini | 0825550101 | 85 | -26.1850 | 27.7020 | 06:00-18:00 | Report to the gatehouse, bring ID |
| Gold Mine Ltd | Pit 2 | Randfontein Road, Randfontein | P. Dlamini | 0825550101 | 92 | -26.1620 | 27.6540 | 06:00-18:00 | Hard hat and vest |
| Gold Mine Ltd | Workshop | Randfontein Industrial | T. Radebe | 0825550102 | 80 | -26.1900 | 27.7300 | 07:00-16:00 | |
| Rooiberg Farms | Main farm | R24, Rooiberg | J. van Wyk | 0825550103 | 140 | -25.6500 | 27.2100 | 06:00-17:00 | Phone 30 minutes before |

| # | Case | Expected |
|---|---|---|
| 1.7.7 | Key all four | Created |
| 1.7.8 | Latitude `-26.1850` with the longitude blank | Refused (both or neither) |
| 1.7.9 | Latitude `120`, longitude `27` | Refused (range) |
| 1.7.10 | Distance `6000` | Refused (0 to 5000) |

### 1.8 Compliance items (Fuel > Compliance) with attachments
Key each item, then attach the file with **Evidence / Attach**.

| Type | Title | Reference | Subject | Activity | Authority | Issued | Expires | File |
|---|---|---|---|---|---|---|---|---|
| LICENCE | `Wholesale licence` | WL-2026-004417 | COMPANY, label `Highveld Fuel Distributors` | WHOLESALE | `Controller of Petroleum Products` | 2026-01-15 | 2027-01-14 | `1-compliance/Wholesale-Licence-HFD-2026.pdf` |
| LICENCE | `Site licence Germiston` | SL-2026-009821 | DEPOT, `GRM` | STORAGE | `Controller of Petroleum Products` | 2026-02-01 | 2027-01-31 | `1-compliance/Site-Licence-Germiston-Depot.pdf` |
| CALIBRATION | `Tank D1 calibration` | CAL-77310 | TANK, `D1 Diesel Main` | | | 2026-03-10 | 2027-03-09 | `1-compliance/Calibration-Certificate-Tank-D1.pdf` |
| INSURANCE | `Tanker HF-01 insurance` | INS-55-2209 | TANKER (after 5.1) | | | 2026-07-01 | 2027-06-30 | `1-compliance/Insurance-Tanker-HF01.pdf` |
| INSPECTION | `Dip stick inspection` | INSP-001 | OTHER, `Germiston depot` | | | 2026-10-01 | **today + 20 days (2026-12-02)** | none |

| # | Case | Expected |
|---|---|---|
| 1.8.1 | Key and attach the first four | Saved; file shows name and size |
| 1.8.2 | The inspection due in 20 days | Shows **Expiring soon** |
| 1.8.3 | Add a LICENCE expiring `2025-01-09` with `Expired-Wholesale-Licence-OLD.pdf` | Saved as **Expired** (record: or refused) |
| 1.8.4 | Verify an item as a different person (U-APR, needs FUEL_COMPLIANCE_VERIFY) | Verified. Same person who attached it: refused (record) |
| 1.8.5 | Attach `5-negative-files/fake-photo.jpg` | Refused (not a real image) |
| 1.8.6 | Attach `5-negative-files/macro-installer.exe` | Refused |
| 1.8.7 | Attach `5-negative-files/too-big-image-over-5MB.jpg` | Refused: over 5 MB |
| 1.8.8 | Attach `5-negative-files/empty.pdf` | Refused: empty |
| 1.8.9 | Expiry date blank | Refused |
| 1.8.10 | Link the wholesale licence to legal entity `Highveld Fuel Distributors (Pty) Ltd` and the site licence to depot GRM | Saved (F01 linkage) |

### 1.9 Price lists (Fuel > Price lists, as U-SAL)
| # | Key in | Expected |
|---|---|---|
| 1.9.1 | Standard: DIESEL, price `22.80`, valid from `2026-11-01`, no end | Saved |
| 1.9.2 | Standard: PETROL, `24.90`, from `2026-11-01` | Saved |
| 1.9.3 | Contract price: account Gold Mine Ltd, DIESEL, `22.40`, from `2026-11-01`, note `Contract GM-2026` | Saved |
| 1.9.4 | Volume break: Standard DIESEL, minimum litres `10000`, price `22.60`, from `2026-11-01` | Saved |
| 1.9.5 | Price `-1` | Refused |
| 1.9.6 | Change standard DIESEL to `24.50` (+7.5%, above the 5% control) | Goes to **waiting for approval**, not live (record). U-ADM or an approver approves it. Then set it back to `22.80` (record how) |
| 1.9.7 | Set a DIESEL price of `22.00` once stock cost is known (after 2.1: stock cost about R21.60) | Refused or flagged by the minimum margin guard (3% above cost is about R22.25) (record) |
| 1.9.8 | Valid-to earlier than valid-from | Refused (record) |

### 1.10 Access limits (Fuel > Access limits)
Do this after section 2 so depots have tanks.
| # | Key in | Expected |
|---|---|---|
| 1.10.1 | Person U-OPS: tick Distribution only. Save | Listed with Distribution |
| 1.10.2 | Sign in as U-OPS, open Retail > Forecourt | Refused (403), menu entry hidden after reload |
| 1.10.3 | Person U-OPS: clear all ticks, save | Unrestricted again |
| 1.10.4 | Person U-FCM: tick depot `SBK` only | Saved |
| 1.10.5 | Sign in as U-FCM: Stock & Ledger > Depots and Fuel > Tanks | Only SBK and its tanks (SD-1, SP-1) listed |
| 1.10.6 | As U-FCM request tank D1 directly (open its dip or receive) | Refused: limited to other depots |
| 1.10.7 | Restore U-FCM to no limits | Everything visible again |

---

## 2. Stock (as U-OPS)

### 2.1 Receipts (Fuel > Stock In)
Tank `D1 Diesel Main`, supplier Karoo. Tank starts empty.
| # | Key in | Expected |
|---|---|---|
| 2.1.1 | Litres `20000`, price `21.00`, received `today 07:00`, delivery note `DN-KPS-5501`, invoice ref `KPS-88000` | D1 holds 20 000 L at average cost R21.00 |
| 2.1.2 | Litres `10000`, price `22.50`, received `today 12:00`, delivery note `DN-KPS-5502`, freight `3000`, freight note `Road freight Germiston` | D1 holds 30 000 L. Average cost about **R21.60** (freight R0.30/L added; 21.00 and 22.80 weighted) (record the exact figure) |
| 2.1.3 | Litres `0` | Refused |
| 2.1.4 | Price `0` | Refused |
| 2.1.5 | Litres `25000` (would exceed capacity 50 000 with 30 000 held) | Refused: over capacity (record) |
| 2.1.6 | Freight `-5` | Refused |
| 2.1.7 | Reverse the 12:00 receipt with reason `Keyed in error test` | Tank drops 10 000 L; reversal is a new ledger line. Re-key the receipt after |
| 2.1.8 | Receive on P1 Petrol: `15000` L at `23.40` | P1 15 000 L |
| 2.1.9 | Receive on SD-1: `8000` L at `21.80`; on SP-1: `8000` L at `23.60`; on RD-1: `6000` L at `21.90` | Forecourt tanks stocked |

### 2.2 Dispatch, dip, adjustment, transfer, reservation
| # | Key in | Expected |
|---|---|---|
| 2.2.1 | Dispatch from D1: litres `500`, price blank, dispatched now, recipient `Workshop truck`, authorised by `Olivia Ops`, odometer `182340` | D1 29 500 L (if 2.1.7 re-keyed) |
| 2.2.2 | Dispatch `0` litres | Refused |
| 2.2.3 | Dispatch more than tank holds (`99999`) | Refused: insufficient stock |
| 2.2.4 | Dip reading D1: actual `29380`, read now, read by `Tebogo`, notes `Morning dip` | Variance **-120 L** against the book of 29 500 (about -0.41%) recorded |
| 2.2.5 | Dip `-1` | Refused |
| 2.2.6 | Adjust D1 by `-50` L, reason `Stocktake loss` | Applied directly (under the 100 L control) |
| 2.2.7 | Adjust D1 by `-150` L, reason `Evaporation and spill` | **Waiting for a second person** (over 100 L) (record) |
| 2.2.8 | U-OPS tries to approve their own request | Refused. U-APR approves with a note | D1 drops 150 L |
| 2.2.9 | Adjust with reason `abc` | Refused (5 to 500 characters) |
| 2.2.10 | Transfer D1 to SD-1: `2000` L, reason `Top up Sandton` | D1 -2 000, SD-1 +2 000 (same fuel) |
| 2.2.11 | Transfer D1 to P1 (diesel into petrol) | Refused (different fuel) (record) |
| 2.2.12 | Transfer D1 to D1 | Refused |
| 2.2.13 | Reserve on D1: `3000` L for Gold Mine Ltd, note `Pit 1 order`, expires `2026-11-20` | Available-to-promise falls by 3 000 |
| 2.2.14 | Reserve more than available (`60000`) | Refused |
| 2.2.15 | Check Stock position | Physical, reserved, safety and available figures add up: available = physical - reserved - safety |

### 2.3 Reorder rules (Fuel > Reorder rules)
| # | Key in | Expected |
|---|---|---|
| 2.3.1 | Rule on D1: reorder below `20000` L, order `10000` L, supplier Karoo, **enabled** | Saved |
| 2.3.2 | Click **Check now** | D1 holds about 27 000, above the level: no draft. Edit the level to `40000`, check again: a **draft requisition** appears (never an order) |
| 2.3.3 | Open Requisitions, approve or discard the draft | Status changes; no PO is created automatically |

---

## 3. Buying (as U-OPS, supplier invoices with attachments)

### 3.1 Purchase orders
| # | Key in | Expected |
|---|---|---|
| 3.1.1 | PO: supplier Karoo, DIESEL, litres `10000`, price `21.45`, expected `2026-11-15`, notes `Test PO 1` | Created, may need approval |
| 3.1.2 | Approve (U-APR or per your permissions) then place | Status Ordered |
| 3.1.3 | PO to `Free State Fuels` (unapproved supplier) | Refused (record) |
| 3.1.4 | Litres `0` | Refused |
| 3.1.5 | Receive on D1 against PO 1: litres `10300` | Refused: over tolerance 2% (3% over) |
| 3.1.6 | Receive `10150` (1.5% over) | Accepted. (record: PO status) |
| 3.1.7 | Receive `10000` against a fresh PO 1b of the same size, price `21.45` | PO goes **Received** |
| 3.1.8 | Second PO (PO 2): Karoo, DIESEL, `12000` L, `21.45`. Receive `11800` L against it | PO **Part received**, 200 L outstanding |

### 3.2 Supplier invoices (Fuel > Supplier invoices)
Capture each with the matching PDF open for reference (the invoice PDFs are source documents; the capture screen takes the figures, not a file upload, so also note the filename in notes).

| # | Key in | Expected |
|---|---|---|
| 3.2.1 | Supplier Karoo, PO 1b, invoice no `KPS-88121`, date `2026-11-10`, due `2026-12-10`, litres `10000`, price `21.45`, other charges `0`, VAT `32175.00`, notes `INV-KPS-88121-matches.pdf` | Capture accepted. Match: **matched** to PO and receipt (goods R214 500.00; total R246 675.00) |
| 3.2.2 | Approve it (as U-APR / invoice manager) | Approved; then **hand off to Accounts Payable** produces a bill reference (record: needs the Accounting module) |
| 3.2.3 | Capture `KPS-88122`, litres `10000`, price `22.10`, VAT `33150.00` against the same PO | Flags **price higher than PO** (+R0.65/L, R6 500.00) |
| 3.2.4 | Decline or hold it with reason `Price above PO` | Status changes; reason stored |
| 3.2.5 | Capture `KPS-88123` against PO 2: litres `12000`, price `21.45`, other `150.00`, VAT `38632.50` | Flags **billed 12 000 L, received 11 800 L** (variance 200 L) |
| 3.2.6 | Capture `KPS-88121` again | Refused: duplicate invoice number for the supplier (record) |
| 3.2.7 | Litres `0`; price `1001` | Refused (price over 1000) |
| 3.2.8 | VAT `-1` | Refused |

---

## 4. Selling (as U-SAL / U-CRD)

### 4.1 Enquiries and quotes (Fuel > Enquiries)
| # | Key in | Expected |
|---|---|---|
| 4.1.1 | New enquiry: account Gold Mine Ltd, DIESEL, litres `5000`, requested `2026-11-18`, source `PHONE`, notes `Pit 1 refill` | Status New |
| 4.1.2 | Walk-in: no account, contact `Walk-in farmer`, phone `0825550199`, DIESEL, `1000` | Accepted (needs a contact name) |
| 4.1.3 | Litres `0` | Refused |
| 4.1.4 | Quote the Gold Mine enquiry (site Pit 1, price blank, valid until `2026-11-20`, reference `GM-PO-7781`) | A quote order created at **R22.40** (contract price); history shows version 1 |
| 4.1.5 | Revise the quote price to `22.30` | Version 2 kept; version 1 still visible |
| 4.1.6 | Record acceptance: accepted by `P. Dlamini`, channel `EMAIL`, reference `GM-PO-7781`, accepted on `2026-11-12` | Acceptance recorded; order can be confirmed |
| 4.1.7 | Close the walk-in enquiry with reason `Customer went elsewhere` | Closed |

### 4.2 Orders and credit (Fuel > Orders)
| # | Key in | Expected |
|---|---|---|
| 4.2.1 | New order: Gold Mine Ltd, site Pit 1, DIESEL, `5000` L, price blank, requested `2026-11-18`, customer ref `GM-PO-7790` | Price resolves to **R22.40**; value R112 000.00 |
| 4.2.2 | Order for Gold Mine, DIESEL, `10000` L | Volume break check: contract price still applies (record which of 22.40 / 22.60 wins) |
| 4.2.3 | Confirm 4.2.1 | Credit check passes (R112 000 within R250 000) |
| 4.2.4 | Order for Held Farm Co | Refused or blocked: account on credit hold |
| 4.2.5 | Order for Rooiberg Farms, DIESEL `5000` L at standard R22.80 = R114 000 | Blocked at confirm: over the R80 000 limit |
| 4.2.6 | U-CRD overrides with a reason | Confirmed, override recorded (record how the reason is asked) |
| 4.2.7 | Allocate 4.2.1 from tank D1 (5000 L) | Reservation created; available falls |
| 4.2.8 | Cancel an order with reason | Cancelled, allocation released |
| 4.2.9 | Price per litre `-1` on an order | Refused |

### 4.3 Contracts (Fuel > Contracts)
| # | Key in | Expected |
|---|---|---|
| 4.3.1 | New: account Gold Mine Ltd, number `GM-2026-01`, title `Gold Mine diesel supply 2026`, start `2026-11-01`, end `2027-10-31`, fuel DIESEL, committed litres `120000`, terms `Monthly call-offs` | Created. **Holds no price** |
| 4.3.2 | Same number again | Refused |
| 4.3.3 | End before start | Refused |
| 4.3.4 | After deliveries (section 5), open the contract | Shows litres delivered against the commitment and days left |
| 4.3.5 | Cancel with reason `Test cancel` (on a second contract) | Cancelled; reason stored |

---

## 5. Distribution (as U-DSP and U-DRV)

### 5.1 Tankers (Planning & tankers)
| # | Key in | Expected |
|---|---|---|
| 5.1.1 | Tanker: registration `GP 123-456`, name `HF-01`, compartments `10000`, `8000`, `6000` | Created, capacity 24 000 L |
| 5.1.2 | Tanker `GP 789-012`, name `HF-02`, one compartment `15000` | Created |
| 5.1.3 | Compartment `0` | Refused |
| 5.1.4 | Registration blank | Refused |
| 5.1.5 | Link HF-01 to a Fleet vehicle (if one exists) | Registration follows Fleet |
| 5.1.6 | Add the tanker insurance item from 1.8 now | Saved against TANKER HF-01 |

### 5.2 Delivery run
| # | Key in | Expected |
|---|---|---|
| 5.2.1 | From order 4.2.1 create delivery, scheduled `2026-11-18`, window `08:00-12:00` | Delivery Scheduled |
| 5.2.2 | Planning: assign tanker HF-01, load `5000` L of diesel from D1 into compartments 1 (`5000`) | Loaded 5 000 L; D1 stock unchanged until delivery completes (record) |
| 5.2.3 | Load more than a compartment holds (`11000` into compartment 1) | Refused |
| 5.2.4 | Assign a driver | Appears in the driver's My loads |
| 5.2.5 | U-DRV: My loads > start trip | Status In transit |
| 5.2.6 | U-DRV completes: litres delivered `4990`, receiver `P. Dlamini`, badge `GM-4471`, meter start `120450`, meter end `125440` | Delivered. Variance 10 L (0.2%) is **inside** the 0.5% tolerance: no variance case |
| 5.2.7 | Repeat on a second delivery with litres delivered `4970`, meter `120450` to `125420` | Variance 30 L (0.6%) is **outside** tolerance: appears under **Load variances** |
| 5.2.8 | Complete with meter end less than start | Refused (record) |
| 5.2.9 | Litres delivered `0` | Refused |

### 5.3 Proof of delivery attachments (on the delivery or driver screen)
| # | Attach | Expected |
|---|---|---|
| 5.3.1 | PHOTO `4-proof-of-delivery/pod-photo-1.jpg` | Stored, thumbnail shown |
| 5.3.2 | PHOTO `pod-photo-2.png` and `pod-photo-3.webp` | Stored |
| 5.3.3 | SIGNATURE `signature-receiver-P-Dlamini.png` | Stored, shown as signature |
| 5.3.4 | DOCUMENT `Signed-Delivery-Note-DN-1001.pdf` | Stored |
| 5.3.5 | PHOTO `5-negative-files/real-pdf-used-as-PHOTO.pdf` | Refused: PDF only as a DOCUMENT |
| 5.3.6 | `fake-photo.jpg`, `not-a-pdf.pdf`, `page.html`, `macro-installer.exe`, `empty.pdf` | Each refused |
| 5.3.7 | `too-big-image-over-5MB.jpg` | Refused: over 5 MB |
| 5.3.8 | The very long named PNG | Stored with a cleaned, shortened name (record) |
| 5.3.9 | Attach a file to a delivery still Scheduled (not In transit or Delivered) | Refused |
| 5.3.10 | Download each stored file | Opens with the right type; extension matches real content |
| 5.3.11 | U-APR (FUEL_EVIDENCE_VERIFY) verifies the evidence | Marked verified; the uploader cannot verify their own (record) |
| 5.3.12 | **Offline queue**: in the browser dev tools set the network to Offline, complete a delivery and attach a photo, then go back online | Queued then sent once; **no duplicate** record |

### 5.4 Failed delivery and returned fuel
| # | Key in | Expected |
|---|---|---|
| 5.4.1 | On a third delivery record a **failed attempt**: reason `RECEIVER_ABSENT`, note `Nobody at gate`, litres returned `1500`, reschedule `2026-11-19` | Attempt 1 recorded; delivery rescheduled |
| 5.4.2 | Reason `NOT_A_REASON` | Refused |
| 5.4.3 | Distribution > Returned fuel | Shows 1 500 L still to confirm |
| 5.4.4 | Confirm `1500` L into tank D1 | Recorded; no stock movement is booked (record exactly what Stock shows) |
| 5.4.5 | Confirm `2000` L (more than declared) | Refused |
| 5.4.6 | Confirm the same amount again with the same request | No double entry |

### 5.5 Routes, suggestion, costs
| # | Key in | Expected |
|---|---|---|
| 5.5.1 | Route: tanker HF-01, driver, date `2026-11-18`, stops Pit 1, Pit 2, Workshop in that order | Route created with 3 stops |
| 5.5.2 | Click **Suggest order**, start position latitude `-26.2200`, longitude `28.1700` | A suggested order with total km and saving against the current order. Nothing changes until you apply it. (record the suggested sequence) |
| 5.5.3 | Apply the suggestion | Stop order updates; windows are still respected |
| 5.5.4 | Suggest on a route whose sites have no coordinates | Falls back to windows and stored distance with a note |
| 5.5.5 | Route costs: record **VEHICLE** `1850.00`, **DRIVER** `920.00`, **TOLLS** `310.00` against the route | Saved; actual vs estimate shown |
| 5.5.6 | Void the toll entry with reason `Entered twice` and add `310.00` again | Old one struck through, new one live |
| 5.5.7 | Amount `-5` | Refused |
| 5.5.8 | Cost to serve: open Cost to serve and the By customer section, choose **By litres**, then **Equal per stop**, then **Site distance** | Shares of the R3 080 route cost add up exactly to the route cost each time (cent-exact). Routes with no actual costs are counted as skipped, never estimated |
| 5.5.9 | Landed cost (Insights) for the route | Per-litre cost = cost at sale + route costs; margin per litre shown (record) |

---

## 6. Retail forecourt (U-ATT, U-SUP, U-FCM)

### 6.1 Pumps (as U-FCM)
| Pump | Tank | Price R/L |
|---|---|---|
| `Pump 1` | SP-1 Sandton Petrol | 23.50 |
| `Pump 2` | SD-1 Sandton Diesel | 22.10 |
| `Pump 3` | SP-1 Sandton Petrol | 23.50 |
| `Pump 5` | SD-1 Sandton Diesel | 22.10 (kept for the import tests in section 7) |
| `Pump R1` | RD-1 Rustenburg Diesel | 22.10 |

| # | Case | Expected |
|---|---|---|
| 6.1.1 | Key all five | Created |
| 6.1.2 | Same name `Pump 1` again | Refused |
| 6.1.3 | Price `0` | Refused |

### 6.2 Shift: balanced
| # | Key in | Expected |
|---|---|---|
| 6.2.1 | U-ATT opens shift: attendant `Lerato Mokoena`, opening meters Pump 1 `10000.00`, Pump 2 `5000.00`, Pump 3 `20000.00` | Shift SH-... Open |
| 6.2.2 | Open a second shift on Pump 1 | Refused: already on an open shift |
| 6.2.3 | Opening meter blank | Refused |
| 6.2.4 | **U-ATT closes** (same person who opened), closing Pump 1 `10250.00` test litres `5`, Pump 2 `5400.00`, Pump 3 `20180.00`; cash `7000.00`, card `9500.00`, account `1500.00`, other `500.00`, fleet card `327.50` | Refused: the person who opened cannot close |
| 6.2.5 | **U-SUP closes** with the same figures | Litres sold **825 L** (Pump 1 245 net, Pump 2 400, Pump 3 180). Expected takings **R18 827.50** (5 757.50 + 8 840.00 + 4 230.00). Tenders add to R18 827.50: **variance R0.00** |
| 6.2.6 | Check tank SP-1 and SD-1 in Stock | SP-1 down 425 L (245 + 180), SD-1 down 400 L; one ledger line per tank |
| 6.2.7 | Closing meter below opening | Refused |
| 6.2.8 | Test litres larger than the meter moved | Refused |

### 6.3 Shift: short takings, adjustments, review
| # | Key in | Expected |
|---|---|---|
| 6.3.1 | Open a new shift: Pump 1 `10250.00`, Pump 2 `5400.00`, Pump 3 `20180.00`, attendant `Lerato Mokoena` | Opening gap 0 (matches previous close) |
| 6.3.2 | U-SUP closes: Pump 1 `10350.00` (100 L), Pump 2 `5450.00` (50 L), Pump 3 `20180.00` (0). Expected R2 350 + R1 105 = **R3 455.00**. Cash `3000.00`, card `0`, account `0`, other `0`, fleet `0`, no reason | Refused: variance R455 over the R10 tolerance needs a reason |
| 6.3.3 | Same with variance reason `Till short, counted twice` | Closed with variance **-R455.00**, flagged for review |
| 6.3.4 | Record an adjustment on that shift: kind `REFUND`, amount `120.00`, reason `Customer refund pump 2` | Pending supervisor approval |
| 6.3.5 | U-SUP approves it; recompute | Expected cash reduces by the approved amount (record the new variance) |
| 6.3.6 | Adjustment kind `DISCOUNT` with amount `-5` | Refused |
| 6.3.7 | Supervisor review of the flagged shift with a note | Review status updated |
| 6.3.8 | Reverse a shift with reason `Wrong meter keyed` | Shift Reversed; stock restored; the reversal is a new line |
| 6.3.9 | Forecourt Summary for 30 days | Litres, sales, variance, flagged shifts; by fuel |

### 6.4 Tank reconciliation and supply link
| # | Key in | Expected |
|---|---|---|
| 6.4.1 | Open the reconciliation for SP-1 | Book movements (receipts, pump meters) and a dip variance |
| 6.4.2 | Insinsight: Group report > **Forecourt sales by supplying depot** | Sandton and Rustenburg grouped under **Germiston Depot**; litres match shift totals; sales R shown only to FUEL_MARGIN_READ |
| 6.4.3 | Clear Sandton's Supplied by | The same sales move to **No supplier set** with a warning |

---

## 7. Pump / POS import (U-ADM, U-FCM, and a command window)
Files in `6-pos-import/`. Scripts: `send-shift.ps1` (PowerShell) or `send-shift.sh` (curl). Base URL `http://localhost:8080`.

| # | Do | Expected |
|---|---|---|
| 7.1 | Fuel > Imported shifts > Keys: name `Sandton POS`, **Create key** | Key `hf_fuel_...` shown **once**. Copy it. List shows only the prefix |
| 7.2 | `.\send-shift.ps1 -Key "<key>" -File .\shift-1-valid.json` | HTTP 200; status PENDING, duplicate false |
| 7.3 | Run it again, same file | HTTP 200; **duplicate true**, same import id, nothing new in the queue |
| 7.4 | Send `shift-3-same-id-changed.json` | HTTP **409**: same reference, different content |
| 7.5 | Send `shift-4-closing-below-opening.json` | HTTP 400, nothing staged |
| 7.6 | Send `shift-2-unknown-pump.json` | HTTP 200 PENDING with a problem listed (`Pump "Pump 9" is not set up`) |
| 7.7 | Send with a wrong key (`hf_fuel_wrong`) | HTTP **401**. Same answer with no header at all |
| 7.8 | Imported shifts shows 2 waiting | Both listed; stock and shifts **unchanged** so far |
| 7.9 | As U-SUP open shift 1 > Details | Pump 5, opening 1000, closing 1180, 180 L; cash R1 478.00 + card R2 500.00 = R3 978.00 |
| 7.10 | Accept shift 1 | A closed shift exists; SD-1 down 180 L; takings R3 978.00 = 180 L x R22.10: **variance R0.00** |
| 7.11 | Accept the unknown-pump import | Refused: pump not set up; stays waiting |
| 7.12 | Reject it with reason `Test` | Reason under 5 characters refused. With `Unknown pump test` rejected |
| 7.13 | Send `shift-5-short-takings.json` (opening 1180 continues from shift 1) and accept without a reason | Refused: variance R152.00 needs a reason |
| 7.14 | Accept with variance reason `Counted short` | Accepted, variance recorded |
| 7.15 | Revoke the key in the Keys list, then send shift-1 again | HTTP **401** |
| 7.16 | Turn Retail off in Setup, send with a valid key | Refused (division not active). Turn Retail back on |
| 7.17 | U-ATT (no supervisor permission) opens Imported shifts | No Accept or Reject buttons (or refused) |

---

## 8. Truck stop (U-TS)

### 8.1 Timed services and reservations (Truck stop)
Needs a **Bookings** service first (Bookings module): create service `Secure Parking`, 12 hours, with 2 bays (staff), then mark it a truck stop service.
| # | Key in | Expected |
|---|---|---|
| 8.1.1 | Truck stop > add offering: kind `PARKING` for that service | Listed |
| 8.1.2 | Reserve: vehicle `GP 55 TS GP`, driver `Sipho Nkosi`, phone `0825550123`, free slot tomorrow | Booking created in Bookings, shown here |
| 8.1.3 | Reserve the same bay and slot again | Refused by Bookings (no double booking) |
| 8.1.4 | Cancel, complete and mark no-show on three reservations | Each status changes via Bookings |

### 8.2 Overnight stays
| # | Key in | Expected |
|---|---|---|
| 8.2.1 | Overnight stays > try to book with no rate set | Refused: set a nightly rate first |
| 8.2.2 | Settings: nightly rate `180.00`, bays `2` | Saved |
| 8.2.3 | Book: vehicle `GP 55 TS GP`, driver `Sipho Nkosi`, phone `0825550123`, arrival `2026-11-14`, departure `2026-11-16`, note `Test stay` | Booked; **2 nights, R360.00** |
| 8.2.4 | Book `GP 66 TS GP` for the same two nights, then a third truck `GP 77 TS GP` | Third refused: no free bay on the named night |
| 8.2.5 | Departure on or before arrival | Refused |
| 8.2.6 | Change the rate to `200.00` | The booked stay **keeps R180**; a new stay uses R200 |
| 8.2.7 | Check in on `2026-11-14` (try one day early) | Early refused; on the day accepted |
| 8.2.8 | Check out on `2026-11-17` | Nights run to the actual date: **3 nights, R540.00**; bay released |
| 8.2.9 | Cancel a booked stay: reason `ab` then `Driver changed plans` | Short reason refused; valid one cancels |
| 8.2.10 | No-show before the arrival date | Refused; on or after arrival accepted |
| 8.2.11 | Yard today panel | Counts in the yard, arriving, leaving, free bays |

---

## 9. Reports and insight (as U-ADM, then U-RO)
| # | Open | Expected |
|---|---|---|
| 9.1 | Dashboard | Tank levels, low-stock chips, open variances, today's figures |
| 9.2 | Manager overview | Figures agree with sections 2 to 6 |
| 9.3 | Cost & Margin, Cost to serve, Landed cost | Visible to U-ADM only (FUEL_MARGIN_READ); U-RO does not see the menu entries |
| 9.4 | Group report (30 days) | Row per legal entity; unassigned bucket if depots have no entity; money columns only for FUEL_MARGIN_READ |
| 9.5 | Link depot GRM to the legal entity (Setup) and reopen | Deliveries and stock move under that entity |
| 9.6 | Receipt PDF for a delivery | Prints with correct litres, names and special characters |
| 9.7 | Invoicing: Delivered, not invoiced | Lists completed deliveries; creating an invoice moves them off the list |

---

## 10. Security and negative quick list
- U-RO opens every screen: can read, no Add/Save/Accept buttons; direct API writes refused.
- U-NONE: module hidden, `/fuel` direct URL refused.
- U-ATT: can open and view Forecourt, cannot close their own shift, cannot approve adjustments.
- U-FCM limited to depot SBK (1.10): other depots' tanks, dips and receipts refused even by direct URL.
- Double-click **Save** on a receipt, order and shift close: only one record each.
- Refresh in the middle of a form: nothing half-saved.
- 500+ characters in notes: refused or cut cleanly.
- Special characters in names: `O'Reilly & Sons "Fuel"`, `Ñandú Café`: saved and shown correctly, including on PDFs.
- Browser back after completing a delivery or closing a shift: no duplicate.
- Set the browser to another language or a narrow phone width on My loads: usable.
- Division off (Retail, Distribution, Truck stop): API calls for that division return 403 even with a valid permission.
- Customer portal (separate script): Gold Mine's invite works; a customer without Fuel entitlement is refused.

---

## Result sheet

| Case | Pass / Fail / Blocked | Note (what the system said) | Screenshot # |
|---|---|---|---|
| 1.1.1 | | | |
| ... | | | |

Defects: write the case number, what you did, what you expected, what happened, user, time.

## Reference: the numbers to check by hand
| Check | Figure |
|---|---|
| D1 average cost after 2.1.1 and 2.1.2 | (20 000 x 21.00 + 10 000 x (22.50 + 0.30)) / 30 000 = R21.60 |
| Dip variance 2.2.4 | 29 380 - 29 500 = -120 L (-0.41%) |
| Order 4.2.1 value | 5 000 x 22.40 = R112 000.00 |
| Invoice KPS-88121 | 10 000 x 21.45 = R214 500.00; VAT R32 175.00; total R246 675.00 |
| Invoice KPS-88122 price gap | (22.10 - 21.45) x 10 000 = R6 500.00 |
| Invoice KPS-88123 | 12 000 x 21.45 = R257 400.00 + R150.00 = R257 550.00; VAT R38 632.50 |
| Delivery variance 5.2.6 / 5.2.7 | 10/5 000 = 0.2% (inside 0.5%); 30/5 000 = 0.6% (outside) |
| Route actual cost 5.5.5 | 1 850.00 + 920.00 + 310.00 = R3 080.00 |
| Shift 6.2.5 | 245 x 23.50 + 400 x 22.10 + 180 x 23.50 = R18 827.50; 825 L |
| Shift 6.3.2 | 100 x 23.50 + 50 x 22.10 = R3 455.00; paid R3 000.00; short R455.00 |
| Import shift 1 | 180 x 22.10 = R3 978.00 |
| Import shift 5 | 120 x 22.10 = R2 652.00; paid R2 500.00; short R152.00 |
| Overnight stay | 2 nights x R180.00 = R360.00; checked out late 3 nights = R540.00 |
