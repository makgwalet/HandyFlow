package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackage;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageFile;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageFileRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.BuildTenderPackageRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderPackagePlanResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import za.co.handyflow.platform.compliancetender.dto.TenderPackageResponse;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The build service with its repositories mocked and real iText for drawing and merging. First Maven run is the
 * check (nothing here could be compiled in the authoring session); the pure rules it relies on are tested elsewhere.
 */
class TenderPackageServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID tenderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private final TenderRepository tenders = mock(TenderRepository.class);
    private final TenderPackageRepository packages = mock(TenderPackageRepository.class);
    private final TenderPackageFileRepository files = mock(TenderPackageFileRepository.class);
    private final TenderPackageStorage storage = mock(TenderPackageStorage.class);
    private final SubmissionProfileService profiles = mock(SubmissionProfileService.class);
    private final Tender tender = mock(Tender.class);

    private static final PackageInputs CURRENT_INPUTS = new PackageInputs("d1", "r1", "p1", "k1");

    private byte[] attachedPdf;
    private TenderPackageService service;

    private static byte[] pdf(int pages, WriterProperties props) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = props == null ? new PdfWriter(out) : new PdfWriter(out, props);
        try (PdfDocument doc = new PdfDocument(writer); Document layout = new Document(doc)) {
            for (int i = 1; i <= pages; i++) {
                layout.add(new Paragraph("Page " + i));
                if (i < pages) layout.add(new com.itextpdf.layout.element.AreaBreak());
            }
        }
        return out.toByteArray();
    }

    private SectionSource textSource(SectionType type, String text) {
        return new SectionSource() {
            @Override public SectionType type() { return type; }
            @Override public SectionContent load(BuildContext ctx) { return SectionContent.textOnly(text); }
        };
    }

    private SectionSource unavailableSource(SectionType type, String reason) {
        return new SectionSource() {
            @Override public SectionType type() { return type; }
            @Override public SectionContent load(BuildContext ctx) { return SectionContent.unavailable(reason); }
        };
    }

    private SectionSource documentSource(String fileName, byte[] bytes) {
        return new SectionSource() {
            @Override public SectionType type() { return SectionCatalogue.SUPPORTING_DOCUMENTS; }
            @Override public SectionContent load(BuildContext ctx) {
                PdfPackageMerger.Inspection i = PdfPackageMerger.inspect(bytes);
                PackageFile f = new PackageFile(type().key(), fileName, bytes.length, PackageManifest.sha256Hex(bytes), UUID.randomUUID(), i.health(), i.pages());
                return SectionContent.of(null, List.of(new SectionContent.Attachment(f, bytes)));
            }
        };
    }

    private TenderPackageService serviceWith(SectionSource... sources) {
        return new TenderPackageService(tenders, packages, files, List.of(sources), new TextSectionRenderer(), (tenantId, t) -> new Letterhead("Zeta Civils (Pty) Ltd", List.of("Tel: 012 000 0000"), null, "TND-1", "Road", "SANRAL", null, "21 December 2026"), storage, profiles, (tenantId, t) -> CURRENT_INPUTS, 100, 250);
    }

    private static BuildTenderPackageRequest request(List<String> keys, boolean pricingRequired) {
        return new BuildTenderPackageRequest(keys, null, null, List.of(), null, null, pricingRequired, false, false);
    }

    @BeforeEach
    void setUp() {
        attachedPdf = pdf(2, null);
        when(tender.getStatus()).thenReturn("IN_PREPARATION");
        when(tender.getName()).thenReturn("Road upgrade");
        when(tender.getTenderNumber()).thenReturn("TND-0042");
        when(tenders.findByIdForTenant(tenant, tenderId)).thenReturn(Optional.of(tender));
        when(packages.maxVersion(tenant, tenderId)).thenReturn(0);
        when(packages.save(any(TenderPackage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(files.save(any(TenderPackageFile.class))).thenAnswer(inv -> inv.getArgument(0));
        service = serviceWith(
                textSource(SectionCatalogue.COVER_LETTER, "Dear Sir"),
                unavailableSource(SectionCatalogue.PRICING, "including pricing needs manage permission"),
                documentSource("tax-clearance.pdf", attachedPdf));
    }

    @Test
    @DisplayName("a READ user's preview: pricing is reported unavailable, the package is not ready, and nothing is stored")
    void previewForReadUser() {
        TenderPackagePlanResponse r = service.preview(tenant, tenderId, false, request(List.of("COVER_LETTER", "PRICING", "SUPPORTING_DOCUMENTS"), true));

        assertThat(r.canBuild()).isEqualTo(true);
        assertThat(r.submissionReady()).isEqualTo(false);
        assertThat(r.issues().stream().anyMatch(i -> i.code().equals("SECTION_UNAVAILABLE"))).isEqualTo(true);
        assertThat(r.issues().stream().anyMatch(i -> i.code().equals("PRICING_REQUIRED"))).isEqualTo(true);
        assertThat(r.built()).isNull();
        verify(storage, never()).store(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("building merges the drawn section and the document, stores once as version 1 and records every file in order")
    void builds() {
        when(storage.store(any(), any(), any(), any(), any(), any())).thenReturn(new TenderPackageStorage.Stored("key-1", 1234, "x"));
        TenderPackagePlanResponse r = service.build(tenant, tenderId, true, request(List.of("COVER_LETTER", "SUPPORTING_DOCUMENTS"), false), userId, "Sam");

        assertThat(r.built()).isNotNull();
        assertThat(r.built().versionNo()).isEqualTo(1);
        assertThat(r.submissionReady()).isEqualTo(true);
        assertThat(r.built().fileName()).isEqualTo("tnd-0042-submission-v1.pdf");
        assertThat(r.built().packageHash().length()).isEqualTo(64);
        assertThat(r.built().files().size()).isEqualTo(2);
        assertThat(r.built().files().get(0).source()).isEqualTo("GENERATED");
        assertThat(r.built().files().get(0).fileName()).isEqualTo("01-cover-letter.pdf");
        assertThat(r.built().files().get(1).source()).isEqualTo("ATTACHED");
        assertThat(r.built().files().get(1).pages()).isEqualTo(2);
        verify(storage, times(1)).store(any(), any(), any(), any(), any(), any());
        verify(files, times(2)).save(any(TenderPackageFile.class));
    }

    @Test
    @DisplayName("each build is the next version")
    void nextVersion() {
        when(packages.maxVersion(tenant, tenderId)).thenReturn(4);
        when(storage.store(any(), any(), any(), any(), any(), any())).thenReturn(new TenderPackageStorage.Stored("key-5", 10, "x"));
        TenderPackagePlanResponse r = service.build(tenant, tenderId, true, request(List.of("COVER_LETTER"), false), userId, "Sam");
        assertThat(r.built().versionNo()).isEqualTo(5);
    }

    @Test
    @DisplayName("a password-protected document blocks the build: nothing is stored and the reason names the file")
    void blockedByEncryptedPdf() {
        byte[] locked = pdf(1, new WriterProperties().setStandardEncryption("secret".getBytes(StandardCharsets.UTF_8), "owner".getBytes(StandardCharsets.UTF_8),
                EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.ENCRYPTION_AES_128));
        TenderPackageService blocked = serviceWith(documentSource("locked.pdf", locked));

        TenderPackagePlanResponse r = blocked.build(tenant, tenderId, true, request(List.of("SUPPORTING_DOCUMENTS"), false), userId, "Sam");

        assertThat(r.built()).isNull();
        assertThat(r.canBuild()).isEqualTo(false);
        assertThat(r.issues().stream().anyMatch(i -> i.code().equals("PDF_ENCRYPTED") && "locked.pdf".equals(i.fileName()))).isEqualTo(true);
        verify(storage, never()).store(any(), any(), any(), any(), any(), any());
        verify(packages, never()).save(any(TenderPackage.class));
    }

    @Test
    @DisplayName("a tender that is already submitted cannot be built for")
    void lockedAfterSubmission() {
        when(tender.getStatus()).thenReturn("SUBMITTED");
        assertThatThrownBy(() -> service.build(tenant, tenderId, true, request(List.of("COVER_LETTER"), false), userId, "Sam"))
                .isInstanceOf(IllegalStateException.class);
        verify(storage, never()).store(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown section key and a repeated section are refused")
    void badSections() {
        assertThatThrownBy(() -> service.preview(tenant, tenderId, true, request(List.of("NOPE"), false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(tenant, tenderId, true, request(List.of("COVER_LETTER", "COVER_LETTER"), false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("if the record cannot be saved the stored bytes are removed again")
    void orphanCleanup() {
        when(storage.store(any(), any(), any(), any(), any(), any())).thenReturn(new TenderPackageStorage.Stored("key-x", 10, "x"));
        when(packages.save(any(TenderPackage.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.build(tenant, tenderId, true, request(List.of("COVER_LETTER"), false), userId, "Sam"))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("key-x");
    }

    @Test
    @DisplayName("download refuses bytes that no longer match the recorded hash")
    void downloadHashMismatch() {
        UUID packageId = UUID.randomUUID();
        TenderPackage pkg = TenderPackage.create(packageId, tenant, tenderId, 1, true, false, null, "{}", "[]", "a".repeat(64), "p.pdf",
                PackageManifest.sha256Hex("original".getBytes(StandardCharsets.UTF_8)), "application/pdf", 8, 1, "key-1", userId, "Sam");
        when(packages.findByIdForTenant(tenant, packageId)).thenReturn(Optional.of(pkg));
        when(storage.load("key-1")).thenReturn("tampered".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.download(tenant, packageId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("download returns the stored bytes when they match")
    void downloadOk() {
        UUID packageId = UUID.randomUUID();
        byte[] bytes = "original".getBytes(StandardCharsets.UTF_8);
        TenderPackage pkg = TenderPackage.create(packageId, tenant, tenderId, 1, true, false, null, "{}", "[]", "a".repeat(64), "p.pdf",
                PackageManifest.sha256Hex(bytes), "application/pdf", 8, 1, "key-1", userId, "Sam");
        when(packages.findByIdForTenant(tenant, packageId)).thenReturn(Optional.of(pkg));
        when(storage.load("key-1")).thenReturn(bytes);

        TenderPackageStorage.Loaded loaded = service.download(tenant, packageId);
        assertThat(loaded.fileName()).isEqualTo("p.pdf");
        assertThat(loaded.content().length).isEqualTo(8);
    }

    @Test
    @DisplayName("list() flags a version stale, with reasons, when the tender no longer matches what it was built from")
    void listFlagsStale() {
        TenderPackage fresh = TenderPackage.create(UUID.randomUUID(), tenant, tenderId, 2, true, false, null, "{}", "[]", "a".repeat(64), "p2.pdf",
                "b".repeat(64), "application/pdf", 8, 1, "key-2", userId, "Sam");
        fresh.recordInputs(CURRENT_INPUTS.encode());
        TenderPackage old = TenderPackage.create(UUID.randomUUID(), tenant, tenderId, 1, true, false, null, "{}", "[]", "a".repeat(64), "p1.pdf",
                "c".repeat(64), "application/pdf", 8, 1, "key-1", userId, "Sam");
        old.recordInputs(new PackageInputs("d0", "r1", "p0", "k1").encode());
        TenderPackage unknown = TenderPackage.create(UUID.randomUUID(), tenant, tenderId, 0 + 3, true, false, null, "{}", "[]", "a".repeat(64), "p3.pdf",
                "d".repeat(64), "application/pdf", 8, 1, "key-3", userId, "Sam");
        when(tenders.findByIdForTenant(tenant, tenderId)).thenReturn(Optional.of(tender));
        when(packages.findByTender(tenant, tenderId)).thenReturn(List.of(fresh, old, unknown));
        when(files.findByPackage(any(), any())).thenReturn(List.of());

        List<TenderPackageResponse> out = service.list(tenant, tenderId);

        assertThat(out.get(0).stale()).isFalse();
        assertThat(out.get(1).stale()).isTrue();
        assertThat(out.get(1).staleReasons()).containsExactly("The tender details changed", "The pricing changed");
        assertThat(out.get(2).stale()).as("built before fingerprints existed: unknown, not stale").isFalse();
    }
}
