# Platform Engines Initiative — Progress & Backlog

Tracks the four-part platform-engines request: Tenant Numbering Engine, PDF
skill, tenant-branded Email Engine, Admin/Support Console. See
`HandyFlow_Complete_Gap_Analysis.md` for the pre-existing full module gap
analysis this builds on (its "PDF Generation" and "Shared Notification
Service" cross-cutting sections are the source for items 2 and 3 below).

## 1. Tenant Numbering Engine — IN PROGRESS (reference migration complete)

**Done:**
- `V285__tenant_numbering_engine.sql` — adds `tenants.document_code`
  (backfilled from name/slug), creates `tenant_numbering_config` for
  per-tenant per-document-type overrides. Existing `tenant_number_sequences`
  counters untouched — no reset, no gap, no renumbering of issued documents.
- `Tenant.assignDocumentCode(...)` — immutable once set, by design (a
  document code must outlive a company name change).
- `TenantNumberingFacade` (public API, `identity` root package) +
  `TenantNumberingEngine` (impl) + `TenantDocumentCodeResolver` (separate
  bean so `REQUIRES_NEW` isn't silently dropped by self-invocation).
- **Reference migration**: `InvoiceNumberGenerator`, `QuoteNumberGenerator`,
  `CreditNoteNumberGenerator` (invoicing module) now call the facade instead
  of `TenantSequenceService` directly. Output changes from `INV-00001` to
  `FPS-INV-00001` (tenant code + type code); underlying `INVOICE` / `QUOTE` /
  `CREDIT_NOTE` sequence counters are unaffected.
- Unit tests: `TenantNumberingEngineTest` (formatting/fallback/override logic).

**Real bug this fixes:** `InvoiceNumberGenerator` (invoicing),
`BkNumberGenerator` (bookkeeping) and `FmNumberGenerator`
(facilities-management) each keep an independent `INVOICE`/`INVOICE_SEQUENCE`
counter and can each produce a document literally labelled `INV-00001` for
an unrelated purpose. A tenant running more than one of those modules can
have three different documents with the same visible number. Migrating a
generator to the engine assigns it a distinct `DEFAULT_TYPE_CODES` entry,
which removes the ambiguity for that generator's documents going forward.

**DONE — the three-way `INV-` collision itself is now fixed:**
- `BkNumberGenerator.nextInvoiceNumber` → migrated, default type code `CINV`.
- `FmNumberGenerator.nextInvoiceNumber` → migrated, default type code `FMINV`.
- Confirmed this needed no change to `TenantNumberingEngine`'s
  `DEFAULT_TYPE_CODES` map: `BK_INVOICE`/`FM_INVOICE` are already distinct
  sequence names, so each generator's own caller-supplied default type code
  (`CINV`/`FMINV`) is what the engine's fallback path uses — exactly the
  scenario that fallback was designed for.
- `nextClientCode`/`nextEntryNumber`/`nextWorkOrderNumber` on both classes
  are **left unmigrated on purpose** — see next item.

**DELIBERATELY NOT migrated — `FacilityNumberGenerator` (facilities
module), and the remaining `CLI-`/`JE-`/`WO-` sequences on the two classes
above:**
- `facilities`' `package-info.java` declares
  `allowedDependencies = {"shared", "billing", "evidence", "notifications"}`
  — **`identity` is not in that list.** Migrating `FacilityNumberGenerator`
  to `TenantNumberingFacade` would require adding `identity` to that
  module's declared Modulith boundary, which is a real architectural
  decision in its own right, not something that should happen as a side
  effect of a numbering fix. Left as-is; the `WO-` collision between
  `facilities` and `facilitiesmanagement` (suggested fix: `FWO`) is
  therefore **still live** — flagged here rather than silently resolved by
  quietly expanding a module boundary.
- Same reasoning is why `nextClientCode` (`CLI-`, shared by bookkeeping,
  facilitiesmanagement, and `TrainProvNumberGenerator`) and `nextEntryNumber`
  (`JE-`) weren't touched this pass — worth a second look, but each is a
  separate, smaller collision than the `INV-` one that was the actual
  reason this engine got built, and doing all of them at once risks the
  same "large, unverified, all-at-once rewrite" this whole initiative has
  been avoiding.

**Remaining generators not yet assessed for collisions or migrated**
(same pattern as the ones above; each is a small, independently-verifiable
change once each module's `allowedDependencies` is confirmed to include
`identity`):

| Module | Generator | Current prefix(es) | Suggested type code |
|---|---|---|---|
| hr | `EmployeeNumberGenerator`, `PayRunNumberGenerator` | `EMP-`, `PR-` | keep |
| accounting | `JournalNumberGenerator` | `JE-` | keep, but check vs. bookkeeping `JE-` |
| accountant | `FeeNoteNumberGenerator` | `FN-` | keep |
| fuel | `ReceiptNumberGenerator` | `FDR-` | keep |
| expenses | `ClaimNumberGenerator` | `EXP-` | keep |
| events | `EventNumberGenerator` | `EVT-` | keep |
| contracting | `ContractNumberGenerator` | `CTR-` | keep |
| bookings | `BookingNumberGenerator` | `BK-` | keep |
| collectionsagency | `CollAgencyNumberGenerator` | `CI` | keep |
| debtcollection | `DebtCollectionNumberGenerator` | `DC-` | keep |
| insurancebrokerage | `InsBrokNumberGenerator` | `IB-POL-`, `IB-CI-` | keep |
| legalcompliance | `LegalComplianceNumberGenerator` | `LM-`, `DSAR-` | keep |
| warehousing | `WhseNumberGenerator` | `WHI` | keep |

**DONE — `trainingprovider` vs `training` `CRS-`/`CERT-` collision, resolved
from one side:**
- `TrainProvNumberGenerator.nextCourseCode`/`nextCertificateNumber` →
  migrated, type codes `TPCRS`/`TPCERT` (matching the `TP` family
  `nextInvoiceNumber` already established with `TPI`).
- `training`'s own `TrainingNumberGenerator` was **not** touched — same
  reason as `facilities`: its `package-info.java` allowedDependencies
  doesn't include `identity`. Not needed here, though: since
  `trainingprovider`'s output is now
  `{tenantCode}-TPCRS-00001`/`{tenantCode}-TPCERT-00001`,
  `training`'s unchanged `CRS-00001`/`CERT-00001` is already
  unambiguous against it — this collision is fully resolved without
  needing to touch `training`'s boundary.
- `nextClientCode` (`CLI-`) and `nextDelegateNumber` (`DEL-`) on
  `TrainProvNumberGenerator` left unmigrated — `CLI-` is the same
  many-module collision already deprioritized elsewhere in this doc;
  `DEL-` isn't shared with any other generator.

**Module-boundary decision still open** — two generators now blocked on
it, not just one: `FacilityNumberGenerator` (`facilities`) for the
`facilities`/`facilitiesmanagement` `WO-` collision, and (were `training`
ever to need its own numbers to carry tenant identity, not just be
disambiguated from `trainingprovider`) `TrainingNumberGenerator`. Whoever
owns the Modulith boundary declarations should decide whether `identity`
gets added to `facilities` and `training`'s `allowedDependencies`, or
whether these stay on plain `TenantSequenceService` indefinitely.

**Known limitations (not silently glossed over):**
- Document codes backfilled by V285 are **not** checked for cross-tenant
  uniqueness at migration time (a blocking migration must not fail on a data
  collision). Two existing tenants can land on the same 4-letter code until
  one of them next issues a document, at which point
  `TenantDocumentCodeResolver` only prevents *new* collisions — it does not
  retroactively rename an already-visible code. A one-off audit query to
  find and manually resolve existing collisions is recommended before this
  ships to production tenants.
- No Settings UI yet for tenants to view/manage their `document_code` or
  `tenant_numbering_config` rows — API/DB only right now.
- `reset_policy = 'YEARLY'` is defined in the schema but not implemented in
  `TenantNumberingEngine` (documented as a stub, not silently ignored).
- **Not build/test-verified in this session** — this sandbox has no network
  access to Maven Central, so `./mvnw compile` / `./mvnw test` could not be
  run here. Manual review only (imports, brace balance, self-invocation
  check). Run `./mvnw test -Dtest=TenantNumberingEngineTest` and
  `./mvnw compile` locally before merging, plus the Modulith architecture
  test (`./mvnw test -Dtest=*ModularityTest*` or equivalent) since a new
  cross-module facade was added.

## 2. PDF generation skill — DONE

Created `.claude/skills/pdf-generation/SKILL.md`, matching the structure
this project's own `README.md` says skills live under. Grounded entirely in
what already exists in the codebase (not generic PDF advice):

- **Library**: iText 7 is the real standard (confirmed in `pom.xml` comment
  and dominant usage); OpenPDF (`com.lowagie`) is a legacy dependency used
  only by `projects`/`tasks` — skill says don't start new work with it.
- **Fonts**: `LiberationSans-{Regular,Bold}.ttf` already shipped under
  `src/main/resources/fonts/`, loaded with `FORCE_EMBEDDED` + `WINANSI`.
- **Brand colours**: `InvoicePdfService`'s palette (`NAVY #1B3A6B`,
  `TEAL #0D9488`, etc.) is the *exact same* token set
  `handyflow-web/README.md` documents for the frontend — skill tells future
  PDF work to reuse these constants rather than inventing new hex values,
  so PDFs and the web app stay visually consistent.
- **Real gotcha documented**: `TenantDetails.logoUrl()` is stored as a
  `data:` URI, not an HTTP(S) URL — confirmed in three separate files
  (`PdfReportService`, `QuotePdfService`, `SecurityPdfBrandingHelper`),
  each with the identical decode-first workaround. Skill captures the
  pattern once so a fourth copy doesn't get written.
- **Real gotcha found and documented**: several PDF generators
  (`Emp201PdfGenerator`, `PayslipPdfGenerator`, `ApPdfGenerator`) format
  Rand amounts via `NumberFormat.getInstance(new Locale("en","ZA"))`, which
  renders the thousands separator as a non-breaking space — outside the
  WinAnsi encoding used when embedding LiberationSans, so it can misrender.
  The rest of the codebase's manual `"R " + String.format(Locale.US,
  "%,.2f", ...)` pattern is the safe one; skill tells new code to use that
  and flags (not silently fixes) the inconsistent files.
- Points to `VatRateProvider` (shared) as the one legitimate VAT-rate
  source, and to the new `TenantNumberingFacade` for any document number.
- Points to `SecurityPdfBrandingHelper` as the one existing (module-local)
  attempt at a reusable branded-header component, and to
  `projects/api/PdfExportController.java` as the REST export convention to
  reuse.
- Lists the confirmed missing-PDF backlog from
  `HandyFlow_Complete_Gap_Analysis.md` so it isn't duplicated across docs.

**Not done as part of this**: actually extracting `SecurityPdfBrandingHelper`
into a shared, cross-module `PdfBrandingEngine`. The skill explicitly says
not to block ordinary feature work on that refactor, but also not to add a
54th independent copy of the same header logic — left as tracked platform
backlog, consistent with this codebase's own stated convention of only
extracting shared code once a *third* consumer needs it (see that file's
own doc comment).

## 3. Tenant-branded Email Engine — IN PROGRESS (reference migration complete)

**Real state found (more centralized than the original brief assumed):**
`EmailService` + `EmailTemplates.java` (46 template methods, one shared
`wrap()` header/footer) are already a genuine shared foundation — not
scattered per-module email code as a first read of the brief might suggest.
The real gap: `wrap()`'s header is unconditionally `"HandyFlow · Your
Business Operating System"` — none of the 46 templates accept any tenant
branding beyond an occasional name used inline in body copy — and **9
files** (`AdminInvoiceService`, `PmNotificationService`,
`ScmNotificationService`, `AccountingService`, `CreativeService`,
`ContractExpiryScheduler`, `MarketingService`, `ApRemittanceEmailService`,
`PosService`) build their own independent HTML wrapper instead of using
`EmailTemplates.wrap()` at all — confirmed by
`ScmNotificationService`'s own comment "same pattern as
PmNotificationService", i.e. that duplication was already noticed and not
fixed.

**Done:**
- `V286__tenant_email_signature.sql` — `tenant_email_signature` table,
  **opt-in** (`enabled` defaults `false`): no existing tenant's email
  changes in appearance until they configure and enable a signature.
- `TenantEmailSignature` entity + repository (identity module).
- `TenantEmailBrandingFacade` (public API) + `TenantEmailBrandingEngine`
  impl — deliberately returns a plain, pre-escaped, pre-rendered HTML
  `String` (never null, `""` when unconfigured) rather than a DTO, so
  `EmailTemplates` — a pure static utility class with zero Spring/JPA
  dependencies today — doesn't have to start depending on identity-module
  types to use it.
- **Reference migration**: `EmailTemplates.quoteSentToClient(...)` — the
  real, customer-facing "quote sent" email in `QuoteService.sendQuote()` —
  now has an additive 6-arg overload taking `signatureHtml`; the original
  5-arg overload is kept and simply delegates with `""`, so nothing else
  calling the old signature needed to change. `QuoteService` now injects
  `TenantEmailBrandingFacade` and passes the rendered signature through.
- Unit tests: `TenantEmailBrandingEngineTest` (enabled/disabled/missing row,
  HTML escaping, blank-field omission) and
  `EmailTemplatesQuoteSentToClientTest` (confirms the 5-arg and 6-arg-empty
  paths render byte-identical output, and the 6-arg path appends/tolerates
  null correctly).

**Found and fixed, this session — a real, systemic bug independent of the
header work above:** while migrating more templates, a broader look at
this file turned up several methods interpolating a client/company/first
name directly into HTML with **no escaping at all** — unlike the
established pattern (`quoteSentToClient` and most of this file already
escape tenant-entered strings). A maliciously- or carelessly-named
client/contact/company record would render as live HTML in a real
customer's inbox — content-injection / phishing-enablement, not a
cosmetic gap.

Confirmed by direct code read and fixed:
- `feeNote` — `clientName`
- `paymentReceived` — `clientName`
- `clientOnboardingWelcome` — `clientName`, `firmName`
- `invoiceGeneratedWithPdf` — `companyName`, `customerName` (this one
  doesn't use `wrap()`/`wrapForTenant()` at all — it's a fully standalone
  table-based HTML document with its own tenant-branded header, which is
  *why* it stood out: the header already showed `companyName` correctly,
  just unescaped)
- `registrationConfirmation` — `firstName`, `companyName`

`EmailTemplatesEscapingTest` covers all five with a `<script>` payload.

**All 10 candidates from that scan now verified by direct code read —
5 false positives, 5 more real bugs, all fixed:**
- **Already safe (false positives — the scan's method-boundary heuristic
  breaks on nested CSS braces):** `userInvitation`, `accountSuspended`,
  `planChanged` (`tenantName` escaped; `oldPlanName`/`newPlanName` are not,
  but those come from a fixed internal plan catalogue, not free text —
  not treated as a bug), `quoteExpiry`, `invoiceGenerated`.
- **Real, fixed this pass:** `taxDeadlineReminder` (`clientName`),
  `clientDeadlineReminder` (`firmName`), `tcsPinExpiryReminder`
  (`clientName`), `ficaDocumentExpiryReminder` (`clientName`, `fileName`),
  `portalInvite` (`clientName`, `firmName`). Same fix pattern as the first
  five, same argument order preserved — only the escaping changed.
  `EmailTemplatesEscapingTest` extended to cover all five.

That's 10 real unescaped-interpolation bugs found and fixed across this
file (the original 5 plus these 5), all confirmed by reading the actual
code rather than trusting the scan.

**Then went further: a wider manual scan of every `public static String`
method in this file** (not just the two batches above) turned up **19
more** of the same bug, spanning three modules:
- **Auth (3):** `passwordReset` (`firstName`), `passwordChanged`
  (`firstName`), `pilotCountdown` (`firstName`).
- **Contracting (6):** `contractSigningInvitation`, `contractFullyExecuted`,
  `contractTerminated` (all `partyName`), `contractDeclined`,
  `contractAmendmentRequested` (both `ownerName` + `partyName`),
  `contractSigningTurnNotification` (`partyName`).
- **Property/lease (9):** `leaseCreated`, `leaseTerminated` (also fixed
  its `reason` param, same category of bug though outside the original
  name-list), `leaseRenewed`, `leaseExpiringTenant`, `leaseExpiringLandlord`
  (all `lesseeName`, most also `propertyName`), `rentEscalation`,
  `rentReceipt`, `rentPartialPayment`, `rentOverdueReminder` (all
  `lesseeName`).
- **Accountant (1):** `documentRequestCreated` (`firmName`, and its
  `description` param, same reasoning as `leaseTerminated`'s `reason`).

Every one of these 19 was individually verified against its exact
`.formatted(...)` argument list before and after the fix, to confirm the
count and order didn't shift — only the escaping changed. Re-ran the
detection script after all fixes: it now reports exactly 2 remaining
"hits", both confirmed false positives — `documentRequestCreated`'s
`clientName` parameter is genuinely unused anywhere in that method's
template (dead parameter, not a bug), and `quoteSentToClient`'s 5-arg
overload delegates safely to its already-escaped 6-arg sibling.

`EmailTemplatesEscapingTest` now covers all 29 fixes (10 + 19) with the
same `<script>` payload pattern.

**29 real, confirmed, fixed HTML-escaping bugs in this file, total, this
session.** No further automated scanning was done beyond this pass — the
remaining ~15 methods in this file not mentioned anywhere in this doc
haven't been checked either way and shouldn't be assumed safe, though the
name-parameter list this scan searched for (`clientName`, `companyName`,
`firmName`, `tenantName`, `partyName`, `ownerName`, `lesseeName`,
`invitedByName`, `firstName`, `customerName`, `fileName`,
`propertyName`) is a reasonable guess at coverage, not a guarantee —
a method using a differently-named parameter for the same kind of data
would not have been caught by this method.

**NOT yet done — remaining backlog:**
1. ~~`wrap()`'s own header still always says "HandyFlow"~~ — **DONE, this
   session.** Rather than editing `wrap()` itself (which would change all
   46 templates' output in one unverified pass — the exact risk this item
   originally flagged), added a separate `wrapForTenant(content,
   tenantCompanyName)` method: same styling, but the header `<h1>` shows
   the tenant's own (HTML-escaped) company name instead of "HandyFlow",
   and the footer now reads "Sent by {tenant} · Powered by HandyFlow" —
   crediting both rather than hiding the platform entirely, a deliberate
   choice (see the method's own Javadoc) rather than an oversight.
   `quoteSentToClient` (both overloads, since the 5-arg one delegates to
   the 6-arg) is the first template migrated to it — same
   reference-migration discipline as everything else in this initiative.
   Text-only header for this pass; embedding the tenant's logo (a
   `data:` URI — see the PDF skill's documented gotcha) into every
   outbound email is a separate decision (size, spam-filter behaviour of
   large inline images) deliberately left for later.
   `EmailTemplatesQuoteSentToClientTest` updated: confirms the header now
   shows the tenant name, confirms the company name is escaped, and — a
   real behavioural change worth calling out — **the 5-arg overload's
   output is no longer byte-identical to before this change**, since it
   delegates to the now-branded 6-arg overload. It's still identical to
   the 6-arg-with-empty-signature output, which is the invariant that
   actually matters. The other 45 templates are still on plain `wrap()`
   and unaffected — this is one template's header, not a global change.
2. **The other 45 `EmailTemplates` methods** don't yet accept a signature
   parameter or use `wrapForTenant` — migrate the highest-value customer-facing
   ones next (invoice-issued, invoice-overdue, booking confirmation) using
   the exact same additive-overload + `wrapForTenant` pattern as
   `quoteSentToClient`.
3. **1 of the 9 files with independent inline HTML migrated, 8 remain:**
   `AdminInvoiceService` — done, this session. New
   `EmailTemplates.subscriptionInvoiceEmail(...)`, built on `wrap()` (not
   `wrapForTenant()` — this is the one template in this whole initiative
   where plain HandyFlow branding is *correct*: it's HandyFlow's own
   subscription invoice to a tenant, not a tenant billing their customer).
   The original inline template had a parameterized header subtitle
   ("Tax Invoice — {period}") that `wrap()`'s fixed header can't
   reproduce — moved into the body's `.highlight` box instead, the same
   place every other template already puts document-specific details, so
   nothing was lost. Also fixed an unescaped `tenantName` found in the
   process (30th escaping bug this session — same category as the other
   29, just found later). `EmailTemplatesSubscriptionInvoiceTest` covers
   both content and escaping.

   **`ContractExpiryScheduler` — also DONE, this session.** New
   `EmailTemplates.contractRenewalReminder(...)`, built on plain `wrap()`
   — safe here because the original already showed a literal "HandyFlow"
   header identical to `wrap()`'s own, so migrating it changes zero
   branding, only deduplicates ~15 lines of CSS. Fixed two more unescaped
   fields (`title`, `contractNumber` — 31st and 32nd escaping bugs this
   session) in the process. `contracting`'s own `package-info.java`
   `allowedDependencies` doesn't include `identity` (same boundary gap as
   `facilities`/`training`), so this one couldn't become properly
   tenant-branded even though it probably should be (a contract belongs
   to the tenant, not HandyFlow) — tracked as a real, not-yet-resolved
   gap, not silently worked around. `EmailTemplatesContractRenewalReminderTest`
   covers content, pluralisation, and escaping.

   **`ApRemittanceEmailService` — partially addressed, deliberately NOT
   migrated onto a shared template.** Same `identity`-boundary gap as
   `ContractExpiryScheduler` (confirmed: `ap`'s `allowedDependencies` is
   `{"shared", "notifications", "accounting", "approvals"}`, no
   `identity`) — but this one's original document is genuinely brand
   *neutral* today (no "HandyFlow" text at all, just a plain navy
   "Remittance Advice" header), unlike `ContractExpiryScheduler`'s. Since
   it can't be given the tenant's own name without the boundary change,
   forcing it onto `wrap()` would have actively **added** unwanted
   HandyFlow branding that isn't there today — a real regression, not a
   neutral migration. Left its own inline HTML in place, but fixed the
   confirmed real bug that doesn't depend on the boundary question: two
   unescaped fields, `supplierName` and `paymentRef` (33rd and 34th
   escaping bugs this session). Method visibility widened from `private`
   to package-private so `ApRemittanceEmailServiceEscapingTest` can
   exercise it directly without mocking the full send pipeline.

   **`PosService` — excluded from this backlog item entirely, not just
   deferred.** Its `buildHtmlReceipt(...)` isn't an email at all — it's a
   printable POS receipt (monospace font, 300px width, meant for a
   terminal print pipeline), a fundamentally different document type that
   `wrap()`'s branded-email layout would actively break if forced onto
   it. Real gap still worth flagging separately, though, found while
   checking this: `tenantName`/`tenantAddress`/`tenantPhone` are appended
   via raw `StringBuilder.append(...)` with **no escaping at all** — same
   bug category as everywhere else, different mechanism (string
   concatenation, not `.formatted()`), different risk profile (rendered
   in the POS terminal's own browser session, not emailed to an outside
   inbox) — **not fixed this session**, flagged for its own pass since it
   sits outside what this backlog item was actually about.

   Remaining 6 (of the original 9, now that `AdminInvoiceService`,
   `ContractExpiryScheduler` are migrated and `PosService` is
   reclassified as out of scope): `PmNotificationService`,
   `ScmNotificationService`, `AccountingService`, `CreativeService`,
   `MarketingService`, `ApRemittanceEmailService` (escaping fixed, still
   blocked on the boundary question for real migration) — each needs its
   own reference migration (or a documented reason it can't, e.g.
   `ScmNotificationService`'s distinct amber "Supply Chain" accent colour
   may be an intentional sub-brand, not simply an oversight — confirm
   with product before merging its styling into the shared teal palette).
4. **No `tenant_email_signature` Settings UI** yet — API/DB only.
5. **No sender-identity-per-tenant** (`fromAddress`/`fromName` in
   `EmailService` are still global, single values) and **no delivery
   observability** (`email_messages` table from the original brief) — both
   real, larger pieces of platform work, unstarted.
6. **Not build/test-verified in this session** — same Maven Central network
   limitation as deliverable 1. Run
   `./mvnw test -Dtest=TenantEmailBrandingEngineTest,EmailTemplatesQuoteSentToClientTest`

   and `./mvnw compile` locally, plus the Modulith architecture test (a new
   cross-module facade was added, same as deliverable 1).

## 4. Admin/Support Console enhancements — IN PROGRESS (real bug found + fixed; diagnostic engine shipped)

**Real state found — much further along than the brief assumed:** the
`admin` module already has tenant search, per-tenant detail (modules,
users, MRR, recent audit events — `AdminService.getTenantDetail`), module
activate/deactivate, discount management, a full TOTP-gated superadmin
login flow, an audit log (`AdminAuditLog`), and — critically — an already
fairly sophisticated **impersonation session** design
(`AdminImpersonationSession`: admin, tenant, reason, IP, started/ended,
15-minute expiry, a `readOnly: true` JWT claim). Section 17 and most of
section 18 of the original brief describe things that already exist.

**Real, previously-undiscovered bug found and fixed:** the impersonation
JWT `AdminAuthService.generateImpersonationToken()` issues has no
`"permissions"` claim (by design — it's meant to be read-only). But
`JwtService.extractPermissions()` did:
```java
Set.copyOf((List<String>) claims.get("permissions"))
```
— which throws a `NullPointerException` when that claim is absent.
`JwtAuthFilter` catches the exception broadly, logs it, and falls through
*unauthenticated* rather than surfacing it. Net effect: **the entire "view
tenant read-only" impersonation feature — despite the session tracking,
audit log, and TOTP-gated login already built around it — could never
actually reach a single business endpoint.** It fails silently, not loudly,
so nothing in logs or tests flagged it as broken.

**Fixed:** `extractPermissions` is now null-safe (empty set instead of
throwing) — `JwtServiceTest` has a regression test built from the exact
claim shape `generateImpersonationToken()` produces.

**Follow-up (this session): the read-only-authorization design gap above
is now fixed, not just flagged.** Two real bugs were involved, not one:

1. **No permissions claim at all** (the NPE above) — fixed earlier.
2. **A second, previously-undiscovered bug found while designing the fix
   for #1's follow-on**: `TenantContext.getCurrentUserId()` does
   `UUID.fromString(id)`, and the impersonation token's subject is the
   literal string `"IMPERSONATION"` — not a user UUID, because there is no
   real user. Even after #1's fix, the *first* line of code anywhere in a
   request that called `getCurrentUserId()` during an impersonation
   session threw a raw, confusing parse exception. **Fixed**:
   `JwtService.isImpersonation(token)` (new), `JwtAuthFilter` now sets
   `TenantContext.setImpersonation(...)`, and `getCurrentUserId()` checks
   that flag first and throws a clear, intentional
   `IllegalStateException` ("no real user — this is a read-only support
   impersonation session") instead of a raw UUID-parse failure.
   `TenantContextTest` covers both the normal and impersonation paths.

**Fixed properly, not by guessing at permission names:** the impersonation
JWT now carries a real `"permissions"` claim, built from
`SELECT name FROM permissions WHERE is_read_only = true` — migration
`V287` adds that column to `permissions` (`Permission.readOnly`,
`PermissionRepository.findByReadOnlyTrue()`), backfilled by suffix
(`_READ` / `_VIEW` — the two patterns actually confirmed to exist in this
codebase, including in `Permission.java`'s own doc comment) and
explicitly documented as a **heuristic requiring human review**, not a
guarantee — the migration's own comment tells whoever owns this to run
`SELECT name FROM permissions WHERE is_read_only = true ORDER BY name`
and confirm nothing on that list can mutate data before impersonation is
relied on for real tenant support. `AdminAuthService.generateImpersonationToken`
reads that list via `JdbcTemplate` (not a `PermissionRepository` import —
`admin`'s `package-info.java` `allowedDependencies` is `{"shared"}` only,
same Modulith-boundary discipline as every other cross-module read in
this module). `AdminAuthServiceImpersonationTest` confirms the token
actually carries the real permission names end to end.

**Shipped: Tenant Diagnostic Engine** (`AdminTenantDiagnosticService`,
`GET /api/v1/admin/tenants/{slugOrId}/diagnostics`) — per brief section 19.
Scoped to checks against tables confirmed to actually exist (no
job-execution-history or email-delivery-tracking table exists yet, so
"failed scheduled jobs" / "email delivery status" checks from the brief's
wishlist are NOT included — fabricating those against nonexistent data
would be worse than not having them):
- `TENANT_STATUS` (critical) — ACTIVE/TRIAL vs SUSPENDED/CANCELLED
- `HAS_USERS` (critical) — at least one user account
- `HAS_ACTIVE_MODULES` (critical) — at least one active module
- `DOCUMENT_CODE` (informational) — ties in deliverable 1
- `EMAIL_SIGNATURE` (informational) — ties in deliverable 3
- `VAT_NUMBER`, `LOGO` (informational)

Each check carries a `CRITICAL` vs `INFORMATIONAL` severity rather than
collapsing into a single score, matching the brief's explicit instruction
("not as a simplistic score... but as a technical diagnostic status").
Unit tests (`AdminTenantDiagnosticServiceTest`) cover a healthy tenant, a
suspended tenant, unconfigured opt-in items rendering as informational
(not critical), and the not-found path.

**NOT done — remaining backlog:**
1. **The `V287` backfill needs a human review pass before production use**
   — see above. This is the one item in this whole session where I'm
   explicitly saying "don't trust the default, go check it" rather than
   shipping something I'm confident is correct outright.
2. No UI for toggling a permission's `is_read_only` flag yet — direct SQL
   only. A support engineer noticing a permission is mis-flagged today has
   to fix it with `UPDATE permissions SET is_read_only = ... WHERE name = ...`.
3. "Fix it for me" actions (brief section 20) — not started; each one
   (resend verification email, retry failed email, regenerate PDF, etc.)
   is its own small feature with its own blast radius and deserves its own
   session rather than being rushed here.
4. No frontend for the new diagnostics endpoint yet — API only.
5. **Not build/test-verified in this session** — same Maven Central
   limitation as the other deliverables, and this one touches shared JWT
   authentication and TenantContext, used everywhere — treat it with the
   most care of anything in this whole initiative. Run
   `./mvnw test -Dtest=JwtServiceTest,TenantContextTest,AdminAuthServiceImpersonationTest,AdminTenantDiagnosticServiceTest`
   first, then the full suite, before merging. The `V287` migration itself
   also needs to actually run against a real database before anyone trusts
   its backfill — check the review query above.

---
*Last updated by Claude — migrated ContractExpiryScheduler onto a shared template; fixed escaping in ApRemittanceEmailService in place (blocked from full migration by a module-boundary gap); excluded PosService from this backlog item (it's a receipt, not an email) while flagging its own separate escaping gap; 34 total escaping bugs fixed in this session across all files touched.*
