package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.application.internal.submission.PackagePlan.PlannedSection;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackage;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageFile;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageFileRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.BuildTenderPackageRequest;
import za.co.handyflow.platform.compliancetender.dto.SubmissionProfileRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderPackagePlanResponse;
import za.co.handyflow.platform.compliancetender.dto.TenderPackageResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a tender's submission package (ADR-005): collects the chosen sections, draws the text ones, plans and
 * validates, merges PDFs and images into one combined PDF, keeps Word/Excel and other originals as they are,
 * and stores the result with a manifest. Each build is a new immutable version.
 * <p>
 * Packages are only built while the tender can still change (the same statuses as pricing); after submission
 * the existing versions remain readable and downloadable.
 * <p>
 * UNVERIFIED in the authoring session: no Spring, iText or database was available to compile or run this. Its
 * pure parts (planning, validation, manifest, naming, limits) are tested separately and do run.
 */
@Slf4j
@Service
public class TenderPackageService {

    static final Set<String> BUILDABLE_STATUSES = Set.of("DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT");
    static final String CONTENT_TYPE = "application/pdf";

    private final TenderRepository tenderRepository;
    private final TenderPackageRepository packageRepository;
    private final TenderPackageFileRepository fileRepository;
    private final Map<String, SectionSource> sources = new HashMap<>();
    private final SectionRenderer renderer;
    private final LetterheadProvider letterheads;
    private final TenderPackageStorage storage;
    private final SubmissionProfileService profiles;
    private final PackageInputsProvider inputs;
    private final SectionCatalogue catalogue = SectionCatalogue.v1();
    private final long systemMaxFileBytes;
    private final long systemMaxTotalBytes;

    public TenderPackageService(TenderRepository tenderRepository, TenderPackageRepository packageRepository,
                                TenderPackageFileRepository fileRepository, List<SectionSource> sectionSources,
                                SectionRenderer renderer, LetterheadProvider letterheads, TenderPackageStorage storage, SubmissionProfileService profiles, PackageInputsProvider inputs,
                                @Value("${handyflow.tender.package.system-max-file-mb:100}") long systemMaxFileMb,
                                @Value("${handyflow.tender.package.system-max-total-mb:250}") long systemMaxTotalMb) {
        this.tenderRepository = tenderRepository;
        this.packageRepository = packageRepository;
        this.fileRepository = fileRepository;
        for (SectionSource s : sectionSources) sources.put(s.type().key(), s);
        this.renderer = renderer;
        this.letterheads = letterheads;
        this.storage = storage;
        this.profiles = profiles;
        this.inputs = inputs;
        this.systemMaxFileBytes = systemMaxFileMb * 1024 * 1024;
        this.systemMaxTotalBytes = systemMaxTotalMb * 1024 * 1024;
    }

    /** Everything a build would do, short of merging and storing. */
    @Transactional(readOnly = true)
    public TenderPackagePlanResponse preview(TenantId tenantId, UUID tenderId, boolean mayIncludePricing, BuildTenderPackageRequest request) {
        Prepared p = prepare(tenantId, tenderId, mayIncludePricing, request);
        return response(p, p.plan.canBuild(), p.plan.submissionReady(), p.plan.issues(), null);
    }

    @Transactional
    public TenderPackagePlanResponse build(TenantId tenantId, UUID tenderId, boolean mayIncludePricing, BuildTenderPackageRequest request,
                                           UUID userId, String userName) {
        Tender tender = tender(tenantId, tenderId);
        if (!BUILDABLE_STATUSES.contains(tender.getStatus())) {
            throw new IllegalStateException("A package can only be built while the tender can still change. This tender is " + tender.getStatus() + ".");
        }
        Prepared p = prepare(tenantId, tenderId, mayIncludePricing, request);
        if (!p.plan.canBuild()) return response(p, false, false, p.plan.issues(), null);

        // PDFs and images are merged in package order; everything else is kept as an original and listed.
        List<SectionContent.Attachment> ordered = new ArrayList<>();
        for (PlannedSection s : p.plan.sections()) if (s.content().available()) ordered.addAll(s.content().attachments());
        List<PdfPackageMerger.Input> toMerge = new ArrayList<>();
        List<SectionContent.Attachment> mergedAttachments = new ArrayList<>();
        List<SectionContent.Attachment> originals = new ArrayList<>();
        for (SectionContent.Attachment a : ordered) {
            FileKind kind = a.file().kind();
            if (kind == FileKind.PDF || kind == FileKind.IMAGE) {
                toMerge.add(new PdfPackageMerger.Input(a.file().fileName(), a.bytes()));
                mergedAttachments.add(a);
            } else {
                originals.add(a);
            }
        }
        if (toMerge.isEmpty()) throw new BusinessException("There is nothing to merge. Add at least one section or PDF document.");

        PdfPackageMerger.Merged merged;
        try {
            merged = PdfPackageMerger.merge(toMerge);
        } catch (PackageMergeException e) {
            throw new BusinessException("The package could not be built: " + e.getMessage());
        }

        int version = packageRepository.maxVersion(tenantId, tenderId) + 1;
        String combinedName = PackageNaming.combinedFileName(tender.getTenderNumber(), version);
        PackageFile combined = new PackageFile("COMBINED", combinedName, merged.pdf().length, PackageManifest.sha256Hex(merged.pdf()),
                null, PdfHealth.OK, merged.pages());

        // what the portal receives is the combined PDF plus the originals: limits on size, count and names apply to that
        List<PackageFile> delivered = new ArrayList<>();
        delivered.add(combined);
        for (SectionContent.Attachment o : originals) delivered.add(o.file());
        List<PackageIssue> issues = new ArrayList<>(p.plan.issues());
        issues.addAll(PackageValidator.validate(p.effective.profile(), delivered));
        boolean canStore = PackageValidator.canBuild(issues);
        if (!canStore) return response(p, false, false, issues, null);

        // the manifest lists every input in package order, with page counts filled in from the merge
        List<PackageFile> manifestFiles = new ArrayList<>();
        int mergeIndex = 0;
        for (SectionContent.Attachment a : ordered) {
            PackageFile f = a.file();
            if (f.kind() == FileKind.PDF || f.kind() == FileKind.IMAGE) {
                f = new PackageFile(f.sectionKey(), f.fileName(), f.sizeBytes(), f.sha256(), f.evidenceId(), f.pdfHealth(), merged.pagesPerInput().get(mergeIndex++));
            }
            manifestFiles.add(f);
        }
        PackageManifest manifest = PackageManifest.of(manifestFiles);

        UUID packageId = UUID.randomUUID();
        TenderPackageStorage.Stored stored = storage.store(tenantId, tenderId, packageId, combinedName, CONTENT_TYPE, merged.pdf());

        boolean includesPricing = p.plan.sections().stream().anyMatch(s -> s.type().needsPricingAuthority() && s.content().available());
        boolean ready = p.plan.submissionReady();
        TenderPackage pkg = TenderPackage.create(packageId, tenantId, tenderId, version, ready, includesPricing, p.effective.profile().name(),
                ProfileJson.of(p.effective.profile()), ProfileJson.issues(issues), manifest.packageHash(), combinedName, combined.sha256(), CONTENT_TYPE,
                stored.sizeBytes(), merged.pages(), stored.storageKey(), userId, userName);
        pkg.recordInputs(inputs.current(tenantId, tender).encode());
        List<TenderPackageFile> savedFiles = new ArrayList<>();
        try {
            packageRepository.save(pkg);
            int seq = 1;
            for (PackageManifest.Entry e : manifest.entries()) {
                PackageFile source = manifestFiles.get(e.position() - 1);
                savedFiles.add(fileRepository.save(TenderPackageFile.create(tenantId, packageId, seq++, e.sectionKey(), e.fileName(),
                        sourceOf(source, p.generated), e.sizeBytes(), e.sha256(), e.pages(), source.evidenceId())));
            }
        } catch (RuntimeException e) {
            // the record could not be written, so the stored bytes would be an orphan nobody can find
            try { storage.delete(stored.storageKey()); } catch (RuntimeException ignored) { log.warn("Could not remove orphaned package bytes {}", stored.storageKey()); }
            throw e;
        }
        log.info("Tender package built tender={} version={} ready={} files={} tenant={}", tenderId, version, ready, manifest.entries().size(), tenantId);
        return response(p, true, ready, issues, toResponse(pkg, savedFiles, List.of()));
    }

    @Transactional(readOnly = true)
    public List<TenderPackageResponse> list(TenantId tenantId, UUID tenderId) {
        Tender tender = tender(tenantId, tenderId);
        PackageInputs now = inputs.current(tenantId, tender);
        return packageRepository.findByTender(tenantId, tenderId).stream()
                .map(pkg -> toResponse(pkg, fileRepository.findByPackage(tenantId, pkg.getId()), PackageInputs.changes(PackageInputs.parse(pkg.getInputsFingerprint()), now))).toList();
    }

    @Transactional(readOnly = true)
    public TenderPackageResponse get(TenantId tenantId, UUID packageId) {
        TenderPackage pkg = findPackage(tenantId, packageId);
        PackageInputs now = inputs.current(tenantId, tender(tenantId, pkg.getTenderId()));
        return toResponse(pkg, fileRepository.findByPackage(tenantId, packageId), PackageInputs.changes(PackageInputs.parse(pkg.getInputsFingerprint()), now));
    }

    @Transactional(readOnly = true)
    public TenderPackageStorage.Loaded download(TenantId tenantId, UUID packageId) {
        TenderPackage pkg = findPackage(tenantId, packageId);
        byte[] bytes = storage.load(pkg.getStorageKey());
        // a stored package must still be what was built; a mismatch means it was altered or damaged and must not be handed out as the record
        if (!PackageManifest.sha256Hex(bytes).equals(pkg.getFileSha256())) {
            throw new IllegalStateException("The stored package no longer matches its recorded hash.");
        }
        return new TenderPackageStorage.Loaded(bytes, pkg.getFileName(), pkg.getContentType());
    }

    // ---- internals

    private record Prepared(Tender tender, List<PlannedSection> sections, Set<PackageFile> generated, PackagePlan plan,
                            SubmissionProfileResolver.Effective effective) {}

    private Prepared prepare(TenantId tenantId, UUID tenderId, boolean mayIncludePricing, BuildTenderPackageRequest request) {
        Tender tender = tender(tenantId, tenderId);
        List<String> keys = request.sectionKeys() == null || request.sectionKeys().isEmpty()
                ? catalogue.inDefaultOrder().stream().map(SectionType::key).toList() : request.sectionKeys();
        if (new HashSet<>(keys).size() != keys.size()) throw new IllegalArgumentException("A section can only be chosen once.");

        BuildContext ctx = new BuildContext(tenantId, tenderId, mayIncludePricing, request.coverLetterText(), request.companyProfileText(), request.documentIds());
        Letterhead letterhead = letterheads.forTender(tenantId, tender);
        List<PlannedSection> planned = new ArrayList<>();
        Set<PackageFile> generated = new HashSet<>();
        int position = 0;
        for (String key : keys) {
            SectionType type = catalogue.find(key).orElseThrow(() -> new IllegalArgumentException("Unknown section: " + key));
            SectionSource source = sources.get(key);
            if (source == null) throw new IllegalStateException("No source is registered for section " + key);
            position++;
            SectionContent content = source.load(ctx);
            if (content.available() && content.hasText()) {
                byte[] pdf = renderer.render(letterhead, type, content.text());
                PdfPackageMerger.Inspection inspection = PdfPackageMerger.inspect(pdf);
                PackageFile file = new PackageFile(key, PackageNaming.sectionFileName(position, type), pdf.length, PackageManifest.sha256Hex(pdf),
                        null, inspection.health(), inspection.pages());
                generated.add(file);
                List<SectionContent.Attachment> all = new ArrayList<>();
                all.add(new SectionContent.Attachment(file, pdf));
                all.addAll(content.attachments());
                content = SectionContent.of(content.text(), all);
            }
            planned.add(new PlannedSection(type, content));
        }

        SubmissionProfile saved = profiles.load(tenantId, request.submissionProfileId());
        SubmissionProfile override = overrideOf(request.limits());
        SubmissionProfileResolver.Effective effective = SubmissionProfileResolver.resolve(null, saved, override, systemMaxFileBytes, systemMaxTotalBytes);
        PackagePlan plan = PackagePlanner.plan(planned, effective.profile(), tender.isRequiresPricing() || request.pricingRequired());
        return new Prepared(tender, planned, generated, plan, effective);
    }

    private static SubmissionProfile overrideOf(SubmissionProfileRequest r) {
        if (r == null) return null;
        return new SubmissionProfile(r.name(), r.allowedExtensions(), r.maxFileBytes(), r.maxTotalBytes(), r.maxFileCount(), r.zipAllowed(), r.maxFileNameLength());
    }

    private static TenderPackageFile.Source sourceOf(PackageFile f, Set<PackageFile> generated) {
        if (generated.stream().anyMatch(g -> g.fileName().equals(f.fileName()) && g.sha256().equals(f.sha256()))) return TenderPackageFile.Source.GENERATED;
        FileKind kind = f.kind();
        return kind == FileKind.PDF || kind == FileKind.IMAGE ? TenderPackageFile.Source.ATTACHED : TenderPackageFile.Source.ORIGINAL;
    }

    private TenderPackagePlanResponse response(Prepared p, boolean canBuild, boolean ready, List<PackageIssue> issues, TenderPackageResponse built) {
        List<TenderPackagePlanResponse.SectionStatus> sections = p.sections.stream().map(s -> new TenderPackagePlanResponse.SectionStatus(
                s.type().key(), s.type().title(), s.content().available(), s.content().unavailableReason(), s.content().attachments().size())).toList();
        List<TenderPackagePlanResponse.Issue> out = issues.stream().map(i -> new TenderPackagePlanResponse.Issue(i.severity().name(), i.code(), i.message(), i.fileName())).toList();
        return new TenderPackagePlanResponse(canBuild, ready, sections, out, p.effective.ceilingNotes(), built);
    }

    private static TenderPackageResponse toResponse(TenderPackage pkg, List<TenderPackageFile> files, List<String> staleReasons) {
        return new TenderPackageResponse(pkg.getId(), pkg.getTenderId(), pkg.getVersionNo(), pkg.isSubmissionReady(), pkg.isIncludesPricing(),
                pkg.getProfileName(), pkg.getPackageHash(), pkg.getFileName(), pkg.getSizeBytes(), pkg.getPageCount(), pkg.getCreatedAt(), pkg.getCreatedByName(),
                files.stream().map(f -> new TenderPackageResponse.FileEntry(f.getSequenceNo(), f.getSectionKey(), f.getFileName(), f.getSourceType().name(),
                        f.getSizeBytes(), f.getSha256(), f.getPages())).toList(),
                !staleReasons.isEmpty(), staleReasons);
    }

    private Tender tender(TenantId tenantId, UUID tenderId) {
        return tenderRepository.findByIdForTenant(tenantId, tenderId).orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
    }

    private TenderPackage findPackage(TenantId tenantId, UUID packageId) {
        return packageRepository.findByIdForTenant(tenantId, packageId).orElseThrow(() -> new ResourceNotFoundException("TenderPackage", packageId.toString()));
    }
}
