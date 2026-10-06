# ADR-005: Tender document builder and submission package

**Status:** PROPOSED (revised 2026-10-06 after an external review; see section 8). Nothing is built. Sections 6 and 8 list the decisions needed before building.
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

## 8. Revision after an external review (2026-10-06)

An outside review of this ADR argued the output should be a **validated submission package** shaped by the tender's own instructions, not "a PDF". Checked against the code and the earlier ADRs, I accept most of it and change the proposal as follows.

**Accepted (changes sections 3 and 4):**
1. **Submission profile from V1.** A tender carries the rules its instructions set: allowed file types, per-file and total size limits, whether ZIP is allowed, whether pricing must also be supplied as Excel, required folder categories. They are **entered per tender from the tender instructions, never hardcoded** and never assumed from a portal name. The reviewer's example (an Eskom tender: PDF only, 500 MB per file, 4 GB total, ZIP refused) is exactly why; I have not verified that tender myself and am not encoding any portal's numbers.
2. **Limits are layered, smallest wins:** system maximum, then the tender's own limits. The 25 MB / 15 file figure is withdrawn as a product rule. The system maximum is an operational setting (memory) and the build refuses with the actual numbers when a limit is exceeded.
3. **Output modes:** combined PDF, individual numbered PDFs, ZIP (only when the profile allows it), and original files kept alongside (for example the pricing Excel). Which modes are offered depends on the profile; an unavailable mode says why.
4. **Validation before output:** mandatory requirements, expiry against the closing date (ADR-003 already does this), duplicate files, file sizes against the profile, unreadable or encrypted PDFs, file names. Blocking issues stop the build; warnings do not. Readiness stays advisory as before; **profile violations are blocking** because the portal would reject the submission.
5. **Package is immutable once submitted**, with a manifest (file, pages, size, SHA-256) and a package hash. Already in section 3.2; now also the snapshot link.
6. **Non-PDF originals are retained, not dropped.** Word and Excel are kept as originals where the profile wants them; converting them to PDF needs an Office conversion engine that does not exist in this platform today and is not in V1.
7. **Main button wording:** "Build submission package", not "Export PDF".

**Accepted in principle, to be designed when reached (not V1 of this ADR):** client review and comments per section, approval workflow, optimisation with quality levels (never silent loss of quality), acknowledgement and tracking after submission, and the reports in the review (win rate, turnaround, missing documents, lost-tender analysis).

**Not accepted as written, with reasons:**
- **A separate "Tender Production & Submission Engine" with about twenty named engines.** This repeats the earlier pattern of speculative architecture: each of those becomes a module only when there is code that needs it. The first slice is one service plus the pure validators (a profile checker and a manifest builder), kept free of repositories like `businessreadiness` and `TenderPriceCalculator` so they can be tested without a database.
- **Fixed readiness percentages** (for example "72% tender readiness", "92% match", a 0 to 100 bid score). ADR-003 chose facts over scores, and nothing in the platform can yet justify the weighting. Match suggestions and a bid/no-bid recommendation would need a separate decision on how they are computed.
- **Tender document ingestion and automatic requirement extraction** from uploaded tender packs: needs document parsing (and almost certainly AI) with its own provider, accuracy and prompt-injection decisions; it is the "V2" in the review and stays a separate ADR.
- **One engine for tenant and client sides.** Agreed in direction, with the Part 8 decision unchanged: the shared parts are pure logic (profile validation, manifest, section catalogue) used by both modules, while each module keeps its own entities. A single shared "subject" entity would reverse Part 8, which is the owner's call.

**What I could not check:** the review's claims about specific South African portals' current limits come from the reviewer, not from me.

**Additional decisions needed**
7. Submission profile: entered per tender by hand (proposed) or also saved as reusable named profiles (for example "Eskom", "eTender")? Saved profiles invite stale numbers.
8. Is it acceptable that Word/Excel conversion is out of V1 (originals are kept and listed)?
9. Which comes first: the builder for the tenant's own tenders (this ADR's scope) or the client-service side the review is written for?
