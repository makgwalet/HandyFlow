# CLIN-DOC-001A: Apache PDF rendering spike

Status: recommended, pending the spike. Tracks the renderer decision recorded in the backlog as CLINIC-DEC-023 (a review note sent 2026-10-13 calls it DEC-026; the backlog numbers are kept until the register is reconciled).

Source: the renderer review note supplied 2026-10-13. The licence statements below come from that note and the projects' own pages; they have **not** been independently checked, and a legal review of every direct and transitive dependency is still required.

## Recommendation under test
Move away from iText (AGPL or commercial licence). Prefer **Apache PDFBox** (Apache 2.0) as the core library. Prototype **Apache FOP** (Apache 2.0) for structured paginated layout. **OpenHTMLtoPDF** (LGPL 2.1+) and **OpenPDF** (MPL 2.0 or LGPL 2.1+) are conditional alternatives, not the default under a permissive-only policy. The engine stays behind `DocumentRenderer`, so the choice can change without rewriting any document.

Not approved yet: final dependency versions, the transitive licence inventory, the production renderer, and any cryptographic signing component. PDFBox alone is not a signing service: certificates, signer identity, timestamping, verification and any advanced-signature requirement need their own design.

## What exists in the code (patch 0192)
- `DocumentModel`: a document described with no PDF library (letterhead, headings, text, key/value details, tables with a repeating header, rule, space, signature block).
- `DocumentRenderer`: `name()` and `render(model)`. Implementations: `ITextDocumentRenderer` (the baseline, standard Helvetica). The PDFBox and FOP adapters are the work of this spike.
- `DocumentModelRules`: validates a model before any renderer sees it, and gives the plain text a renderer's output must contain.
- `ReferenceDocuments.all()`: the reference pack below, synthetic data only.

## Reference pack (all synthetic)
1. One-page sick note, practice letterhead and branch letterhead (`sick-note-practice`, `sick-note-branch`).
2. Two-page referral with a long summary (`referral-two-pages`).
3. Prescription with many medicine rows and a very long instruction (`prescription-long-instruction`).
4. Medical report with a repeating table and a signature block (`medical-report-tables`).
5. Document with logo slot, embedded Arial-compatible font, page numbers and footer (`branded-footer-numbers`). The pack ships no logo; the spike adds one.
6. Long names, missing optional fields and special characters (`edge-cases`).

## Acceptance criteria
- All required content is present and readable (compare extracted PDF text with `DocumentModelRules.text(model)`).
- No overlapping text, clipped fields or orphaned signature blocks.
- Table headers repeat across pages.
- Fonts render the same in the deployed container, and are embedded.
- Text is searchable and selectable.
- Rendering the same input twice gives the same document content.
- Memory and time are measured for realistic workloads.
- Every direct and transitive licence is recorded.
- The chosen implementation supports the required integrity and signature workflow, or a separately approved signing component is documented.
- A browser preview is not evidence of pagination: the server PDF is what is tested.

## Migration plan (not started beyond step 2)
1. Inventory (done below). 2. Abstraction: `DocumentRenderer`, the model, the iText adapter (patch 0192). 3. First permissive renderer passing the pack. 4. Move document types one at a time, simplest first (letters, then referrals and reports, then prescriptions and other regulated documents). 5. Compare content, pagination, branding, accessibility and signature behaviour against approved baselines. 6. Remove iText only when every consumer has moved and regression tests pass.

Issued PDFs are never regenerated: the migration changes future rendering only. A signed or issued document is read back from storage.

## iText inventory (counted 2026-10-13)
- 37 main source files import `com.itextpdf` before this patch (38 with the new adapter); 8 are in the clinic module (`ClinicPdfService`, `ClinicClaimSubmissionPdfService`, `ClinicPatientInvoicePdfService`, `ClinicConsultationSummaryPdfService`, `ClinicStatementOfAccountPdfService`, `ClinicLabSummaryPdfService`, `ClinicLetterPdfService`, `ClinicReferralPdfService`). 5 test files use it.
- The same pom also declares OpenPDF 1.3.30; 24 main files use it (`com.lowagie` / `com.github.librepdf`). Two PDF libraries are therefore in use today, and both have to be considered in the licence review.
- By module: clinic 8 (9 with the adapter), security 8, invoicing 5, compliancetender 3, hr 2, crm 2, and one each in supplychain, legalcompliance, fuel, events, contracting, complianceservices, bookings, admin, accounting.
- Outside the clinic these are not touched by this work.
