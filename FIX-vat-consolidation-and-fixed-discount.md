# Fix record — VAT consolidation + FIXED-discount bug

Status: **Complete for the scope actually approved.** A materially larger
related finding was discovered mid-pass and deliberately NOT acted on
without checking in first — see §4.

## 1. Scope as approved

"Standalone VAT-consolidation + FIXED-discount fix" — small, contained,
no design decisions pending, out of the Pricing Engine's own unresolved
edge cases (pack/tier work explicitly deferred, per the earlier
Strategic Package Review discussion).

## 2. VAT consolidation

**Investigated first, not assumed.** Found **5 independent hardcoded
15% literals across 4 files** (the originating review document knew of
only 2):

| Location | Form | Reachable via real call path? |
|---|---|---|
| `AdminInvoiceService.VAT_RATE` | `0.15` (fraction) | Yes — used directly in tenant-invoice generation |
| `PosService.VAT_RATE_STANDARD` | `15` (percent) | Yes — POS sale-line VAT fallback |
| `CatalogueService.createItem()` | `"15.00"` (percent) | Yes |
| `CatalogueService.updateItem()` | `"15.00"` (percent) | Yes — a **second**, separate copy in the same file |
| `CatalogueItem.create()` (entity) | `"15.00"` (percent) | No — `CatalogueService` always resolves first; effectively dead |

**Fix**: added `shared/VatRateProvider.java` — single configurable
source (`app.tax.vat-rate-pct`, defaults to `15.00`), exposing both
`ratePercent()` (15.00) and `rateFraction()` (0.15) so every existing
call site's arithmetic shape stayed unchanged; only the literal was
swapped for a call. Deliberately not a database-backed historical-rate
table (see the class's own Javadoc for why that's over-scoped for what
was actually broken).

**Two bugs found and fixed while touching this code, not originally
scoped but directly adjacent:**
- `AdminInvoiceService`'s tenant-invoice PDF had a **separately
  hardcoded `"VAT (15%)"` label**, disconnected from the value actually
  used to compute the VAT amount — changing the rate would have made
  the label silently wrong while the number stayed right. Now built
  from the same rate.
- `PosService.processSale()` had two dead local variables
  (`netBeforeVat`, `vatOnNet`) computing a VAT figure that was never
  actually used anywhere — the very next line's own comment already
  says to use the item-level total instead. Removed rather than
  perpetuated with an updated-but-still-unused literal.

**A materially more significant finding, caught only by re-checking
each `PosPurchaseOrderItem`/`PosTransactionItem` hit individually rather
than trusting the grep**: `PosPurchaseOrderItem`'s own entity-level
`BigDecimal.valueOf(15)` fallback was **genuinely reachable** (unlike
`CatalogueItem`'s) — `CreatePurchaseOrderRequest.PurchaseOrderLine.vatRate()`
has no validation constraining it to non-null, and previously flowed
straight through to that fallback unresolved. Fixed by resolving the
default in `PosService.createPurchaseOrder()` before construction,
matching the `CatalogueService` pattern. `PosTransactionItem`'s
equivalent fallback was confirmed genuinely unreachable (both real
callers — `processSale()` and the refund path — always supply a
concrete value) and left as a documented defensive backstop.

## 3. FIXED-discount bug

**Confirmed independently** (not just accepted from the review
document): `AdminDiscountController`'s own Swagger docs advertise
discount codes as "PERCENT or FIXED," and `AdminLookupService.
createDiscount()` fully validates and persists FIXED-type codes with no
restriction. But `AdminDiscountService.resolveDiscount()`'s code-check
branch read `"PERCENT".equals(...) ? value : BigDecimal.ZERO; // FIXED
handled separately below` — and no such logic existed anywhere in the
file. **Every FIXED-type discount code ever created has silently been
worth 0% off, permanently.**

**Fix — deliberately does not touch `resolveDiscount()`'s resolution
algorithm** (Partnership > Volume > Code, highest-percentage-wins,
never stacks stays byte-for-byte identical), per the explicit
instruction from the prior Discount Engine Fix session not to redesign
that logic. Added a new private `resolveCodePercent()` helper that:
- Returns the value directly for `PERCENT` codes (unchanged behaviour).
- For `FIXED` codes, looks up the module's real `module_catalogue.
  monthly_price` (one extra query, same raw-JDBC style already used
  throughout this class) and converts the fixed rand amount to the
  equivalent percentage of that price, capped at 100%, so it compares
  like-for-like against Partnership/Volume using the exact same
  `>`-wins rule already in place.
- Falls back to 0% (not an exception) if the module's price can't be
  resolved — the same "no discount rather than guessing" posture every
  other failure path in this method already uses.

No signature change to `resolveDiscount()` and no change to either of
its two real callers (`AdminDiscountController.previewDiscount()`,
`DiscountFacadeImpl.resolveAndRecordDiscount()`) — the fix is fully
self-contained.

## 4. Found but NOT fixed — needs a scoping decision

A full-repo sweep for the same hardcoded-VAT pattern, done as a final
check before closing this out, found the pattern is **platform-wide**,
not confined to the 3 modules this pass touched. Confirmed genuine
(not grep noise — one hit, `HrService.java`'s `"15"`, was checked and
is unrelated: BCEA minimum annual leave days, not VAT) hits in at
least:

- `invoicing` — `QuoteService.java`, `CreditNote.java`, and others in
  the same grep hit list not yet individually verified
  (`RecurringScheduleService.java`, `RecurringSchedule.java`)
- `accountant` — `AccountantService.java`, `FeeNoteLine.java`
- `supplychain` — `ScPoLine.java`
- `payrollbureau`, `legalpractice`, `recruitmentagency`,
  `bookingagency` — flagged by grep, not yet individually verified

This was explicitly **not acted on** in this pass — expanding into 7+
additional modules without reading each one first would have silently
turned an approved "standalone, small fix" into a large, undiscussed
platform-wide change. Reported back as a distinct next decision rather
than assumed into scope.

## 5. Verify

- Manual brace/paren balance check across every changed file: all
  balanced.
- Every method/field/record signature referenced was cross-checked
  against the actual source read during this session (not assumed).
- **`mvn compile`/`mvn test` could not be executed in this
  sandbox** — Maven Central is not reachable from this environment
  (same constraint hit and documented during the earlier identity
  module pass). **Run `mvn test` before merging** — this has not been
  confirmed green by an actual build.
- Added first-ever backend unit tests for both touched areas:
  `VatRateProviderTest` (trivial value-conversion coverage) and
  `AdminDiscountServiceTest` (8 tests covering baseline resolution,
  PERCENT-code regression coverage, the new FIXED-code conversion
  including the 100% cap and price-unresolvable fallback, and — most
  importantly — that the resolution *order* is provably unchanged by
  mixing a FIXED code against Partnership in both directions).

## 6. Next decision needed

Whether to open a dedicated pass auditing the remaining ~7 modules
found in §4 for the same hardcoded-VAT pattern, before or instead of
returning to the Pricing Engine's own deferred pack/tier design work.
