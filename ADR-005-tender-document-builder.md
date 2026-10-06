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

## 9. Decision record (2026-10-06, after the decision matrix)

The owner supplied a decision matrix with recommended answers. Recorded here as the working decisions. Where one touches an earlier ADR it is marked, and nothing marked **open** is built until it is settled.

**Decision 1 - First slice: APPROVED.** Cover Letter, Company Profile, Compliance, Key Personnel, Pricing, Supporting Documents. Built as `TenderSection` -> `SectionType` -> `SectionSource` -> `SectionRenderer`, so Experience, Equipment, Methodology, Programme, HSE and others are added later as new section types without changing the package service. "Not in V1" is a scope decision, not an architectural limit.

**Decision 2 - Pricing: APPROVED.** Including pricing needs COMPLIANCE_MANAGE/ADMIN (matches ADR-004). READ users may build a draft package without pricing. A tender that requires pricing cannot be marked submission-ready without a valid pricing section; the draft shows "Submission incomplete" with the reason.

**Decision 3 - Company profile: CHANGED.** Reusable company text belongs to a shared Business Profile, not to the tender. The tender chooses "use current profile" or "customise for this tender"; the chosen text is frozen into the package and snapshot. The cover letter stays tender-specific. **Open:** the repo has no Business Profile today (only onboarding code mentions one). Until it exists, V1 reads a tenant-level company-profile text stored in one place behind a `CompanyProfileSource` interface, so the Business Profile can replace it without touching the builder.

**Decision 4 - Storage: APPROVED.** Final packages are retained and auditable (package id, tender, version, generated by/at, section list, evidence ids, document versions, file hashes, pricing version, snapshot id, package hash). V1 uses `EvidenceFacade` behind a `TenderPackageStorage` interface.

**Decision 5 - Size and file limits: CHANGED.** 25 MB / 15 files are not business limits (already withdrawn in section 8). Limits come from the applicable submission profile and tender instructions; the system maximum stays an operational ceiling. Over-limit builds report the actual numbers. Optimisation is a later phase and never silently removes content.

**Decision 6 - Word/Excel: APPROVED WITH CHANGE.** No Office-to-PDF conversion in V1. Originals are retained and included where permitted or required, shown as "Original file retained", never silently dropped.

**Decision 7 - Submission profiles: APPROVED (replaces section 8 question 7).** Reusable named profiles, with a tender-specific override. Three levels: global defaults, tenant profile, tender override. The effective profile is frozen into the submission snapshot. Profile numbers are entered by users from tender instructions; none are hardcoded or assumed from a portal name.

**Decision 8 - Build order: APPROVED, with one point open.** Own-tender builder first as the first vertical slice; client-service side immediately after.
- **Open (owner's call):** the matrix wants engines built on a shared `BusinessSubject` (TENANT/CLIENT). That reverses Part 8, which kept parallel `ClientXxx` entities and shared only pure logic. This ADR does not decide it. The first slice is unaffected: it uses tender-scoped entities plus pure, repository-free classes (profile validator, manifest builder, section catalogue), which both a `BusinessSubject` design and the Part 8 design can consume. The decision is needed before the client-service builder, not before the own-tender slice.

**Build sequence for the first slice:** section architecture and catalogue; `TenderPackageStorage` over `EvidenceFacade`; submission profile entity and pure validator; manifest with SHA-256 and package hash; PDF merge spike on real sample PDFs (encrypted, scanned, oversized) before the merge is relied on; images placed into PDF; Office originals retained; immutable package once submitted; then the UI.

**Later phases (design when reached):** package optimisation, client review and approval, acknowledgement tracking, reporting.

## 10. Build status: first foundations (2026-10-06)

Built under `compliancetender/application/internal/submission/`: `SectionType`, `SectionCatalogue` (the six V1 sections), `SectionSource` and `SectionRenderer` (interfaces, no implementations yet), `SubmissionProfile` and `SubmissionProfileResolver` (global, tenant, tender override, then the system ceiling), `PackageValidator` and `PackageIssue`, `PackageManifest` (SHA-256 per file and a package hash), `TenderPackageStorage` with `EvidenceTenderPackageStorage`, and `PdfPackageMerger`. No entities, endpoints, migration or UI yet.

**Verified:** the pure classes compile and their 21 tests pass in the authoring session, and deliberately broken copies of the boundary checks were caught by the tests.
**Not verified (could not run):** `PdfPackageMerger`, `EvidenceTenderPackageStorage` and their tests. Maven Central was unreachable from the authoring session, so iText and Spring could not be compiled. `PdfPackageMergerTest` is the PDF spike; its first Maven run is the real answer. It generates its own PDFs (plain, user-password, owner-password-only, truncated, image) so no samples are needed, but it cannot cover real scanned or oddly produced tender PDFs. The owner-password-only case is not asserted to a result because iText's behaviour there was not certain.

**Finding that affects decision 4/5:** `EvidenceService` rejects any single file over 20 MB. Packages stored through `EvidenceTenderPackageStorage` therefore cannot exceed 20 MB, which is below the sizes decision 5 expects (hundreds of MB). The storage interface exists so this is a swap, not a rewrite, but a storage without that ceiling is needed before large packages work. Options: raise or parameterise Evidence's cap (changes a shared module), or store packages through `FileStorageService` with a package table of its own. Not decided here.

**Next:** package and section tables (migration V314), `TenderPackageService` (collect sections, validate, merge, store, manifest), endpoints, then the UI.
