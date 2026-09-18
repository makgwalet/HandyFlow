---
name: pdf-generation
description: Use this skill whenever generating, extending, or modifying a PDF document anywhere in the HandyFlow platform backend — invoices, quotes, payslips, incident reports, certificates, statements, or any new business document a module needs to export. Covers the platform's established iText 7 conventions, tenant branding, South African currency/VAT formatting, and the existing reusable components. Read this before writing any new *PdfService / *PdfGenerator class.
---

# HandyFlow PDF Generation

## The one rule that matters most

**Never create a new PDF generator from scratch if an existing shared
component already covers what you need.** HandyFlow has 53+ PDF generator
classes today, most of them independently reinventing the same header/
footer/branding/totals-table logic. Every one you add without reusing what
exists makes the eventual consolidation (see "Known duplication" below)
larger. Before writing a line of PDF code:

1. Search for an existing generator producing a visually similar document
   (`find src/main/java -iname "*Pdf*.java"`).
2. Check whether `SecurityPdfBrandingHelper` (security module) already
   covers your branded-header need — see below.
3. Only write new layout code for what's genuinely new to your document.

## Library: iText 7, not OpenPDF

`pom.xml` standardises on **iText 7** (`com.itextpdf:itext7-core:7.2.5`) and
it's what the large majority of PDF generators use. **OpenPDF**
(`com.github.librepdf:openpdf`, package `com.lowagie.text.*`) exists only as
a legacy dependency used by the `projects` and `tasks` modules — don't start
new work with it. If you're touching an OpenPDF-based file for an unrelated
fix, leave the library choice alone; don't mix libraries in one file.

```java
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;

ByteArrayOutputStream baos = new ByteArrayOutputStream();
Document doc = new Document(new PdfDocument(new PdfWriter(baos)), PageSize.A4);
doc.setMargins(0, 0, 40, 0); // full-bleed header, standard footer margin
// ... build sections ...
doc.close();
return baos.toByteArray();
```

## Fonts — always embed, never rely on system fonts

The platform ships `LiberationSans-Regular.ttf` and `LiberationSans-Bold.ttf`
under `src/main/resources/fonts/`. Load them from the classpath and force
embedding — a PDF that references a non-embedded font can render
inconsistently (or with missing glyphs) on a machine that doesn't have it:

```java
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;

PdfFont regular = PdfFontFactory.createFont(
        Objects.requireNonNull(getClass().getResourceAsStream("/fonts/LiberationSans-Regular.ttf"))
                .readAllBytes(),
        PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
PdfFont bold = PdfFontFactory.createFont(
        Objects.requireNonNull(getClass().getResourceAsStream("/fonts/LiberationSans-Bold.ttf"))
                .readAllBytes(),
        PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
```

Do not introduce a third font family without a real design reason — it adds
another set of TTFs to embed and a visual inconsistency across tenant
documents.

## Brand colours — reuse the platform's actual tokens

`InvoicePdfService` already defines the exact same brand palette the
frontend (`handyflow-web/README.md`) documents as its design tokens. Reuse
these constants (copy them, don't invent new hex values) so every PDF looks
like it belongs to the same product as the web app:

```java
private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B); // primary
private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88); // accent/CTA
private static final DeviceRgb TEAL_LIGHT = new DeviceRgb(0xCC, 0xFB, 0xF1);
private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC); // page bg
private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0); // borders
private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B); // secondary text
private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A); // body text
```

These are the tenant's *brand* colours only in the loose sense that this is
HandyFlow's own brand — true **tenant-specific** branding (a tenant's own
colours/logo on their documents) is a real gap: see "Known duplication and
gaps" below. Don't hardcode a tenant's name or assume `HandyFlow` should
appear prominently — see the branding section next.

## Tenant branding — logo, name, and the data-URI gotcha

Get tenant details through `TenantFacade` (identity module's public API),
never by reaching into `identity`'s internal repository:

```java
TenantDetails tenant = tenantFacade.findTenantDetails(tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));
```

**Critical gotcha, confirmed in three separate PDF services
(`PdfReportService`, `QuotePdfService`, `SecurityPdfBrandingHelper`):**
`TenantDetails.logoUrl()` is stored as a **data URI**
(`data:<mime>;base64,<data>`), not a real HTTP(S) URL. Loading it as a URL
directly will fail. Always branch on the prefix:

```java
private byte[] decodeLogoBytes(String logoUrl) throws Exception {
    if (logoUrl.startsWith("data:")) {
        int commaIdx = logoUrl.indexOf(',');
        if (commaIdx < 0) throw new IllegalArgumentException("Malformed data URI");
        return java.util.Base64.getDecoder().decode(logoUrl.substring(commaIdx + 1));
    }
    try (var in = new URL(logoUrl).openStream()) {
        return in.readAllBytes();
    }
}
```

Always null-check and try/catch logo loading — a missing or malformed logo
must degrade to a text-only header, never fail the whole document:

```java
try {
    return new Image(ImageDataFactory.create(decodeLogoBytes(tenant.logoUrl())))
            .setMaxHeight(36).setAutoScale(false);
} catch (Exception ex) {
    log.warn("Could not load tenant logo tenant={}: {}", tenant.slug(), ex.getMessage());
    return null; // caller renders company name only
}
```

**If your module already has 2+ PDF generators needing a branded header**,
reuse or extend `za.co.handyflow.platform.security.application.internal.SecurityPdfBrandingHelper`
as your reference implementation (it's the one existing example of exactly
this being extracted out of copy-paste, with its own doc comment explaining
when that extraction was worth it — read it before deciding whether to copy
its pattern into your module or, if this is the third+ module needing it,
propose promoting it to `shared`).

## Currency — South African Rand, and a real encoding trap

Use the manual format pattern already used consistently across the
codebase, **not** `NumberFormat.getInstance(new Locale("en", "ZA"))`:

```java
private String formatZar(BigDecimal value) {
    if (value == null) return "R 0.00";
    return "R " + String.format(java.util.Locale.US, "%,.2f",
            value.setScale(2, RoundingMode.HALF_UP));
}
```

**Why not the `en-ZA` locale formatter:** it renders group separators as a
non-breaking space (`\u00A0`), which is outside the WinAnsi font encoding
used when embedding LiberationSans. Rendered through a WinAnsi-encoded font,
that character can come out wrong or missing. A handful of existing PDF
generators (`Emp201PdfGenerator`, `PayslipPdfGenerator`, `ApPdfGenerator`)
still use the `en-ZA` `NumberFormat` — don't copy that pattern into new
code; if you're touching one of those files anyway, flag it rather than
silently leaving inconsistent currency rendering across tenant documents.

## VAT — never hardcode the rate

`za.co.handyflow.platform.shared.VatRateProvider` is the single source of
truth for the standard rate (15%, since April 2018). It was created because
the rate used to be hardcoded independently in at least four places. Use it
for any VAT calculation; never write `0.15` or `15` as a literal in a new
PDF service.

## Document numbers — use the Tenant Numbering Engine

If your PDF needs a document number (invoice, fee note, work order, etc.),
generate it through `za.co.handyflow.platform.identity.TenantNumberingFacade`
(see `PLATFORM-ENGINES-PROGRESS.md`), not a bespoke `*NumberGenerator` class
calling `TenantSequenceService` directly. This is what actually keeps two
different document types from visibly colliding (e.g. two different
modules both producing "INV-00001") — see that file for the full writeup
and the list of default type codes already assigned.

## Section structure — the pattern nearly every generator follows

`InvoicePdfService.buildPdf(...)` is the fullest reference implementation.
Structure new documents the same way, only including the sections that
apply:

```
addHeader(...)          // full-bleed brand bar: logo/company left, doc type + number + date right
addAddressBlock(...)    // "From" (tenant) / "To" (customer) two-column block
addLineItemsTable(...)  // repeating-header table; see pagination note below
addTotalsBlock(...)     // subtotal / VAT / total, right-aligned
addPaymentAndTerms(...) // banking details, payment terms, notes — omit if not applicable
addFooter(...)          // small, right-aligned, muted: "Generated by X — <timestamp>"
```

## Multi-page tables

For any table that can grow unbounded (line items, guard attendance rows,
etc.), make sure the header row repeats on every page — iText 7's `Table`
does this via a dedicated header row added with `table.addHeaderCell(...)`
before the body rows, or `table.setSkipFirstHeader(false)` if you want the
header on page 1 too. Test with a document that genuinely spans 3+ pages,
not just a 2-3 row example — a common bug is a header that repeats
correctly at 2 pages but breaks mid-row at 3+.

## REST endpoint convention

Reuse the pattern in `projects/api/PdfExportController.java` for exposing a
generated PDF — `ResponseEntity<byte[]>`, `MediaType.APPLICATION_PDF`,
`Content-Disposition: attachment`, no caching:

```java
private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .contentLength(content.length)
            .body(content);
}
```

Guard every export endpoint with `@PreAuthorize` on the relevant module
permission, same as `PdfExportController` does — a PDF endpoint is still an
authorization boundary, frontend visibility is not one.

## South African tax invoice requirements (when the document is a "Tax Invoice")

Per SARS requirements, any document labelled a tax invoice must show:
supplier's name, address and VAT registration number; the words "TAX
INVOICE"; a unique sequential invoice number and date; recipient's name and
address; a description, quantity and price for each line item; and VAT
charged shown separately from the VAT-exclusive amount (not just a single
inclusive total). `InvoicePdfService` already does this — use it as the
reference when building a new tax-invoice-style document (fee notes,
statements, POS receipts). This is general awareness of the requirement,
not tax advice — confirm specifics with the tenant's own accountant for
anything beyond standard formatting.

## Known duplication and gaps (don't silently re-introduce them)

- **53 PDF generator classes**, most with their own copy of header/logo/
  currency-formatting logic. `SecurityPdfBrandingHelper` is the only
  existing attempt at extraction, and it's module-local. A cross-module
  `PdfBrandingEngine` in `shared` is real future platform work, not done —
  don't block a normal feature PR on doing that refactor, but don't add a
  54th independent copy of the same header logic either.
- **True per-tenant branding** (a tenant's own accent colour, not just their
  logo) doesn't exist yet — every PDF uses HandyFlow's own NAVY/TEAL
  palette regardless of tenant. Out of scope for a single document's PDF
  work; flag it if a tenant-facing request specifically asks for it.
- **Missing documents by module** (from `HandyFlow_Complete_Gap_Analysis.md`,
  "PDF Generation" section) — check there before assuming a document type
  doesn't exist yet. Highest-value confirmed gaps: PO PDF (SCM — blocks
  supplier email send), IRP5 (HR — year-end legal requirement), SARS
  logbook (Fleet — data model already complete, just not packaged),
  Referral letter (Clinic — daily volume), Incident report (Security/
  Earthmoving).
