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

**Module-boundary decision — RESOLVED, `facilities` side actioned.** The
business decision was made (see strategic roadmap backlog, Part 0,
Decision 2 — "tenant branding is a platform capability, not a
module-by-module exception," which settles the same underlying "can
`identity` be added to a module's boundary for this kind of reason"
question this numbering collision was also blocked on). `facilities`'
`allowedDependencies` now includes `identity`, and
`FacilityNumberGenerator.nextWorkOrderNumber` is migrated — same
resolution pattern as `trainingprovider`/`training`: this side moves to
a tenant-prefixed, distinctly-coded format (`{tenantCode}-FWO-00001`),
which fully disambiguates against `facilitiesmanagement`'s unchanged
plain `WO-00001` without needing to touch that module at all.
`TrainingNumberGenerator` remains unmigrated — lower priority, since (as
already noted) the `training`/`trainingprovider` collision itself was
already fully resolved from the `trainingprovider` side alone; migrating
`TrainingNumberGenerator` would only be about giving `training`'s own
numbers tenant-identity branding, not fixing a live collision.

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

   **`ApRemittanceEmailService` — UPDATE: now fully migrated, this
   session, once the boundary question was resolved.** Originally left
   with escaping fixed in place but its own inline HTML retained, because
   `ap`'s `allowedDependencies` didn't include `identity` and this
   document was genuinely brand-*neutral* (no "HandyFlow" text at all,
   just a plain navy "Remittance Advice" header) — forcing it onto
   `wrap()` at the time would have actively added unwanted branding that
   wasn't there. With the business decision made (strategic roadmap
   backlog, Part 0, Decision 2 — tenant branding is a platform
   capability), `ap`'s boundary now includes `identity`,
   `ApRemittanceEmailService` injects `TenantFacade`, and the email is
   built via a new `EmailTemplates.remittanceAdvice(...)` (built on
   `wrapForTenant()`, correctly — this is the tenant's own AP department
   paying a supplier). Falls back to a generic "Your Supplier" label
   rather than throwing if the tenant's profile is incomplete — a missing
   company name shouldn't block a supplier from being paid and told about
   it. `ApRemittanceEmailService`'s own duplicated inline HTML is deleted
   entirely, not just left with escaping patched — the file is off the
   "9 files with independent inline HTML" list for good, not partially.
   `EmailTemplatesRemittanceAdviceTest` covers the template directly;
   `ApRemittanceEmailServiceTest` (replacing the old
   `ApRemittanceEmailServiceEscapingTest`, whose target method no longer
   exists) covers the tenant-name resolution and its fallback.

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

   **`AccountingService` — checked, deliberately NOT migrated; 6
   escaping bugs fixed in place.** Its 4 templates (`vatReminderEmail`,
   `overdueArEmail`, `lowBalanceEmail`, `vatOverdueEmail`) each use a
   distinct severity colour — purple, red, red, dark-red — that doesn't
   exist anywhere in `wrap()`'s single navy design. That's real
   at-a-glance-in-an-inbox severity signalling (a VAT reminder reads as
   informational, a VAT-overdue escalation reads as urgent, purely from
   colour before the recipient even opens it), not incidental CSS
   duplication — forcing all 4 into one shared header would be a design
   regression, not a cleanup, so left as-is. Confirmed `accounting`'s own
   `allowedDependencies` DOES include `identity` (unlike most of the
   others checked this session), so this one isn't blocked by a module
   boundary — it's blocked by an actual design difference worth a
   product decision. Fixed 6 confirmed unescaped fields across the 4
   methods: `company` (all 4), `customerName` (in the aging table),
   `bankName` and `accountName` (in the low-balance table) — same bug
   category as the 34 already found. All 4 methods widened
   `private` → package-private for direct test access.
   `AccountingServiceEscapingTest` covers all 4.

   **`ScmNotificationService` — confirmed genuinely intentional
   sub-brand, NOT migrated; 1 real bug fixed centrally.** Its explicit
   `HandyFlow · Supply Chain` amber eyebrow label in the header confirms
   what was only a guess before — this is a deliberate sub-brand, not an
   oversight, so left unmigrated (matches the caution already on record).
   `supplychain`'s own `allowedDependencies` also includes `identity`
   (confirmed, so this isn't blocked by a boundary either — it's a real
   design choice). While checking it, found its own `kv(key, value)`
   helper already escaped `key` via its own hand-rolled `esc()` but never
   escaped `value` — exactly where the real risk is: `supplierName`, a
   free-text `"Reason"` field, and `"Approved By"` are genuinely
   user-entered strings passed through it at 6 of `kv()`'s 14 call sites
   in this file (checked every one — none passes a pre-built HTML
   fragment as `value`, so escaping centrally in `kv()` itself is safe
   for all of them). One fix protects every current and future call
   site, rather than fixing each of the 14 individually. `kv()` widened
   to package-private; `ScmNotificationServiceEscapingTest` covers it.

   **ALL 9 ORIGINALLY-FLAGGED FILES NOW ASSESSED — this backlog item is
   complete in the sense that every file has a settled, documented
   outcome; none are still "unchecked."** Final round:

   **`PmNotificationService`** — same shape as `ScmNotificationService`:
   confirmed intentional sub-brand (explicit "HandyFlow · Project
   Management" eyebrow), not migrated. Same `kv()` bug (escaped `key`,
   never `value`) — but this file's `kv()` has a real complication
   `ScmNotificationService`'s didn't: one call site
   (`notifyRiskEscalated`'s "Rating" badge) legitimately passes a
   pre-built `<span style='color:...'>` HTML fragment as `value`, which a
   blind centralized-escape fix would have broken. Checked what `rating`
   actually is first — a DB-column value compared against the literal
   `"RED"`, i.e. enum-constrained, not free text — so that one call site
   was moved to a new `kvRawValue()` helper instead, and `kv()` itself now
   safely escapes `value` for its other 8 call sites.
   `PmNotificationServiceEscapingTest` covers it.

   **`CreativeService`** — not migrated: per-message `<h1>` heading text
   differs by email type (`wrap()`'s single fixed header can't reproduce
   that), and `creative`'s `allowedDependencies` doesn't include
   `identity` either. Most of this file already escapes carefully and
   consistently via `HtmlUtils.htmlEscape` — genuinely better discipline
   than most files checked this session — but 4 of its more complex
   templates (`buildRejectionNotificationEmail`,
   `buildUnapprovedReminderEmail`, `buildApprovalEmail`,
   `buildApproverEmail`) had `jobTitle`, `tenantName`, `approverName`, and
   a free-text `customMessage` field (a note a tenant can attach when
   requesting approval — entered by a real person, definitely not safe to
   skip) all unescaped. All 4 methods and their bugs fixed; all 4 widened
   `private` → package-private. `CreativeServiceEscapingTest` covers all
   four.

   **`MarketingService`** — the most significant finding of this entire
   backlog item, not just this file. Its `personalise(...)` method is the
   **central template-merge function used for every marketing campaign
   send** — it substitutes `{{first_name}}`, `{{name}}`, `{{email}}`, and
   `{{company_name}}` straight into a campaign's HTML body for every
   recipient, with no escaping at all. Unlike almost everything else found
   this session (typically entered by trusted business staff), a
   marketing contact's own name is plausibly **self-entered through a
   public signup form** — this is a genuine stored-XSS-via-mailing-list
   vector, and by far the widest blast radius of any escaping bug found
   (one send can reach thousands of recipients, each one's own data
   re-injected unescaped into the same campaign body). Fixed centrally,
   with `{{unsubscribe_url}}` deliberately left unescaped since it's a URL
   going into an `href` attribute, not text content — escaping it would
   have double-encoded the URL and risked breaking the actual unsubscribe
   link. Also fixed a smaller, separate bug in the same file:
   `buildUnsubscribeConfirmationEmail`'s `greeting` (built from the
   contact's own name) and `tenantName` were both unescaped too. Neither
   this method nor `personalise()` were migrated onto `wrap()` —
   `personalise()` merges into an arbitrary campaign body a marketing user
   wrote themselves, not a fixed layout, so the question doesn't apply the
   same way; `buildUnsubscribeConfirmationEmail` is brand-neutral today,
   and `marketing`'s `allowedDependencies` doesn't include `identity`
   either — the same boundary gap `ap` had until it was resolved (see
   below), still open here. `personalise()` widened to package-private.
   `MarketingServiceEscapingTest` covers both.

   **Final status of all 9 — UPDATE, `ApRemittanceEmailService` migrated
   once the boundary decision was resolved (see above):** 3 fully
   migrated onto shared `EmailTemplates` methods (`AdminInvoiceService`,
   `ContractExpiryScheduler`, `ApRemittanceEmailService`); 4 checked and
   deliberately left on their own inline HTML with a confirmed, real
   reason each (`AccountingService`, `ScmNotificationService`,
   `PmNotificationService`, `CreativeService`
   — either genuine design/branding differences `wrap()` can't reproduce,
   or a module-boundary gap, or both — `marketing`'s `buildUnsubscribeConfirmationEmail`
   is the remaining brand-neutral, boundary-blocked case, same shape
   `ApRemittanceEmailService` used to be); 1 reclassified as out of scope
   entirely (`PosService` — a receipt, not an email); every one of the 9
   had its actual escaping checked line by line, and every real bug found
   was fixed, not just catalogued. Roughly 56 confirmed, fixed
   HTML-escaping bugs across `EmailTemplates` and these 9 files combined,
   this session.
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
2. **DONE, this session.** Added `GET /api/v1/admin/permissions` (lists
   every permission with its `is_read_only` flag) and
   `PATCH /api/v1/admin/permissions/{name}/read-only` (toggles it, with
   an audit log entry — same `audit(...)` pattern every other admin
   write action in this class already uses). This is exactly the kind of
   action that doesn't hit the "fix it for me" structural blocker
   documented in the next item: `is_read_only` is a plain column with no
   invariants beyond itself, so a direct `JdbcTemplate` read/patch is the
   right amount of machinery here, unlike an action that needs another
   module's actual business logic. `AdminServicePermissionsTest` covers
   both endpoints' service methods, including the not-found case.
3. **"Fix it for me" actions (brief section 20) — investigated this
   session, not implemented, for a real structural reason rather than
   just being skipped.** Checked several concrete candidates
   (resend-verification-email, regenerate PDF, unlock user — this
   codebase has no account-lockout mechanism at all, so that one doesn't
   even apply) and found the same blocker every time: a genuine "fix it
   for me" action needs to trigger real business logic in another
   module — `EmailVerificationService.createToken(...)` for a resend,
   `InvoicePdfService`'s actual generation path for a regenerate — not
   just read or patch a row with SQL the way `AdminTenantDiagnosticService`
   and the rest of this admin work do. `admin`'s own
   `package-info.java allowedDependencies` is `{"shared"}` only — no
   `identity`, no `invoicing`, nothing. That's fine for reads (this
   session's `AdminTenantDiagnosticService` and everything else in
   `AdminAuthService`/`AdminService` already reach other modules' *data*
   via `JdbcTemplate`, deliberately, as documented throughout this
   session) but it structurally can't support *actions* that need to run
   another module's actual business logic — reimplementing
   `EmailVerificationToken`'s expiry/invariant logic in raw SQL inside
   `admin` to route around this would be duplicating, and risking
   diverging from, the real logic, which is exactly the kind of shortcut
   this whole session has been deliberately avoiding.

   This isn't a one-off case (like the earlier `facilities`/`training`/
   `contracting` numbering or branding gaps, each blocking one field on
   one email) — it's structural to this entire backlog category, since
   nearly every plausible "fix it for me" action needs a write into
   some other module's real domain logic. Two real options, not
   something to pick unilaterally given the blast radius of an admin
   tool that can trigger writes across every module:
   - **Widen `admin`'s `allowedDependencies`** to include whichever
     modules a given action needs (e.g. `identity` for verification
     resends), and call the module's already-public facade
     (`TenantNumberingFacade`, `TenantEmailBrandingFacade`, or a new
     small facade following the same pattern, e.g. an
     `EmailVerificationFacade`) the same way any other module does.
     Simplest, most consistent with this session's existing patterns,
     but does mean `admin` — already the one module in this codebase
     that legitimately crosses every other module's boundary for
     support purposes — starts doing so at the Java level for writes,
     not just JDBC reads.
   - **Keep `admin` decoupled and route actions through HTTP instead** —
     the Admin Console frontend calls a normal tenant-facing endpoint
     using the same impersonation-token mechanism this session's
     impersonation fix already established, rather than the backend's
     `admin` module calling another module's Java API directly. Keeps
     the Modulith boundary completely untouched, but means "fix it for
     me" actions only work for whatever the impersonation token's
     read-only-or-not-yet-decided authority set actually permits — and
     today that's read-only by design (see the impersonation section
     above), so this option is blocked on that same authority-design
     decision being resolved first, for actions that need to write.

   No code changed for this item — flagging the real blocker clearly and
   concretely, with two named options, is worth more here than a partial
   implementation that either quietly widens a module boundary or
   reimplements another module's business rules in raw SQL.
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
*Last updated by Claude — the "start with the easy ones" pass: with tenant branding now a resolved business decision (strategic roadmap backlog, Part 0, Decision 2), widened `ap` and `facilities`' module boundaries and completed the two numbering/branding fixes that were blocked purely on that decision — `ApRemittanceEmailService` is now properly tenant-branded and off the inline-HTML list for good; `FacilityNumberGenerator` now resolves its `WO-` collision with `facilitiesmanagement`.*
