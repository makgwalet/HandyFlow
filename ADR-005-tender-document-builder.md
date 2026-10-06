# ADR-005: Tender document builder and submission package

**Status:** PROPOSED. Nothing is built. Section 6 lists the decisions needed before building.
**Date:** 2026-10-06. **Module:** `compliancetender` (client-side mirror later, per Part 8). Inspiration: screens 6 (Document Builder) and 8 (Submission Package) of the FlowPro mock-up.

## 1. The goal

Today "Export PDF" is a one-page summary of the tender, its requirement matrix and personnel. It is not what gets submitted. The builder lets the user choose which sections go into the submission, see them in order,
and produce **one package**: a single PDF made of generated pages (cover letter, company profile, compliance summary, personnel, pricing schedule) followed by the actual evidence files (certificates, CVs) for the selected items.

## 2. What the code already offers (read from source, 2026-10-06)

- **iText 7.2.5** (`itext7-core`) is on the classpath and `TenderPdfService` already draws branded pages from `TenantFacade` details (name, logo as a data URI only after ADR-003). iText's `PdfMerger` is in the same kernel and can append existing PDFs; images can be wrapped onto a page.
- **Evidence files**: every compliance document has an `evidenceId`; `EvidenceFacade.download(tenantId, id)` returns bytes, file name and content type. `compliancetender` already depends on `evidence`. No new module dependency is needed.
- **Readiness** (ADR-003) says which registrations and documents satisfy which requirement, as of the closing date. **Pricing** (ADR-004) gives a computed schedule. **Personnel** is a reference to HR with the role on this tender.
- **Snapshots** freeze requirements, personnel and (now) pricing at SUBMITTED.
- **Not present:** generated-file storage (nothing keeps a produced PDF), company profile text, cover-letter text, methodology, programme, experience/past projects, insurance and bank-letter document types (they would be ordinary compliance documents with those types).

## 3. Proposed shape

1. **Sections** are a fixed, ordered catalogue, each either GENERATED (we draw it) or ATTACHED (we append files the business already holds):
   | # | Section | Kind | Source |
   |---|---|---|---|
   | 01 | Cover letter | generated | tender + tenant details + free text the user edits |
   | 02 | Company profile | generated | tenant details + free text |
   | 03 | Compliance | generated list + attached | readiness result per requirement, then the satisfying documents' files |
   | 05 | Key personnel | generated | `TenderPersonnel` + HR name and number |
   | 08 | Pricing schedule | generated | ADR-004 schedule and build-up |
   | 09 | Supporting documents | attached | any compliance documents the user ticks |
   Experience, equipment, methodology and programme are NOT in the first slice: Projects and Fleet have no facade (compliancetender package-info), and methodology/programme are authored documents, which can be uploaded as supporting documents.
2. **A package is a record**, not just a download: the tender, the chosen sections and documents, the generated-at time and user, a version number, and a **manifest** (each part with its name, page count and, for attached files, the evidence id and a SHA-256 of the bytes). The PDF bytes are stored through `EvidenceFacade` (`sourceModule = compliancetender`, entity type `TenderPackage`), so no second storage engine appears.
3. **A draft package is regenerable; a SUBMITTED tender's package is final.** Building after SUBMITTED is refused (same lock as pricing); the package built last before submission is the one that is marked as submitted, alongside the snapshot.
4. **Readiness gate is advisory.** Building with missing or expired evidence is allowed but the cover page of section 03 says what is missing, and the builder screen lists it first. The builder never blocks on readiness (the user may know something we don't).
5. **Order is fixed by the catalogue** in the first slice; reordering (mock-up "Reorder") is later.
6. **Templates** ("Save as Template", the Tender Templates screen) are later: a saved selection of sections per tender type.

## 4. Technical approach and risks

- One new service `TenderPackageService` composes a `PdfDocument` per generated section (reusing `TenderPdfService`'s fonts and branding, extracted into a small shared helper rather than copied), then merges attached PDFs with `PdfMerger`; image evidence (JPEG/PNG) is placed on an A4 page; **other types (Word, Excel) cannot be merged** and are listed on the manifest as "not included (convert to PDF)", never silently dropped.
- Encrypted or corrupt PDFs fail one part, not the build: the part is reported as skipped with the reason, and the package is still produced.
- Memory: a package is built in memory. A cap (for example 25 MB total, 15 files) is needed, with a clear refusal above it. Larger tenders would need streaming, which is not proposed.
- Documents are read **at build time**, and the manifest records which evidence ids and hashes went in, so "what did we submit" is answerable later even if a document is replaced.

## 5. Not proposed

AI-generated responses ("Build Tender Response (AI Assistant)", screen 5): this needs a provider decision, a prompt-injection review of tender documents, and a human-edit workflow; it is a separate ADR. Approval workflow (screen 7) and analytics (screen 9) are also separate.
E-mailing the package, share links and archive are not proposed; Download only.

## 6. Decisions needed

1. Is the first slice right: sections 01, 02, 03, 05, 08, 09 only?
2. **Pricing in the package** is commercially sensitive and pricing endpoints need MANAGE/ADMIN (ADR-004). Proposal: building a package that includes pricing needs the same right; a person with READ can build one without it.
3. Cover letter and company profile: free text edited per tender and kept on the package, with a tenant-level default for the profile. OK, or should a profile live elsewhere (identity)?
4. Storage: store the PDF through `EvidenceFacade` (retained, auditable) or generate on demand only (nothing kept)? Proposal: store.
5. Size cap (25 MB / 15 files) and "Word/Excel are listed, not merged" behaviour acceptable?
6. Should the client-side (`complianceservices`) get the same builder in the same slice, or after?

## 7. Unverified

I could not compile or run Java in the session this was written in, so the `PdfMerger`, image-page and encrypted-PDF behaviour above comes from iText 7.2.5's documented API, not from running it. First build step: a small spike test with real sample PDFs.
