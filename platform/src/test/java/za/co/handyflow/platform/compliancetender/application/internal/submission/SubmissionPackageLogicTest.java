package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Section catalogue, profile layering, package validation and the manifest hash (ADR-005). All pure. */
class SubmissionPackageLogicTest {

    private static final long MB = 1024L * 1024;

    private static PackageFile file(String name, long size, String hash) {
        return new PackageFile("SUPPORTING_DOCUMENTS", name, size, hash, null, PdfHealth.UNKNOWN, null);
    }

    private static PackageFile pdf(String name, long size, String hash, PdfHealth health) {
        return new PackageFile("SUPPORTING_DOCUMENTS", name, size, hash, null, health, 3);
    }

    private static boolean has(List<PackageIssue> issues, String code) {
        return issues.stream().anyMatch(i -> i.code().equals(code));
    }

    // ---------- catalogue

    @Test
    @DisplayName("V1 catalogue holds the six sections in default order")
    void catalogueV1() {
        List<SectionType> types = SectionCatalogue.v1().inDefaultOrder();
        assertThat(types.size()).isEqualTo(6);
        assertThat(types.get(0).key()).isEqualTo("COVER_LETTER");
        assertThat(types.get(4).key()).isEqualTo("PRICING");
        assertThat(types.get(4).needsPricingAuthority()).isEqualTo(true);
        assertThat(types.get(5).key()).isEqualTo("SUPPORTING_DOCUMENTS");
    }

    @Test
    @DisplayName("a later section type is just another entry; an unknown key is empty, not an error")
    void catalogueExtends() {
        SectionType hse = new SectionType("HSE", "Health and safety", 45, false);
        SectionCatalogue c = new SectionCatalogue(List.of(SectionCatalogue.COVER_LETTER, hse));
        assertThat(c.find("HSE").isPresent()).isEqualTo(true);
        assertThat(c.find("NOPE").isPresent()).isEqualTo(false);
    }

    @Test
    @DisplayName("the same key registered twice is refused")
    void catalogueDuplicate() {
        assertThatThrownBy(() -> new SectionCatalogue(List.of(SectionCatalogue.COVER_LETTER, SectionCatalogue.COVER_LETTER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- profile layering

    @Test
    @DisplayName("tender override replaces tenant, tenant replaces global; what a level leaves unstated is kept")
    void layering() {
        SubmissionProfile global = new SubmissionProfile("Default", Set.of("pdf"), 20 * MB, 100 * MB, 15, false, 100);
        SubmissionProfile tenant = new SubmissionProfile("Eskom", Set.of("pdf", "xlsx"), null, 200 * MB, null, null, null);
        SubmissionProfile tender = new SubmissionProfile(null, null, 500 * MB, null, null, null, 50);

        SubmissionProfileResolver.Effective e = SubmissionProfileResolver.resolve(global, tenant, tender, 2000 * MB, 4000 * MB);
        SubmissionProfile p = e.profile();

        assertThat(p.name()).isEqualTo("Eskom");
        assertThat(p.allowedExtensions()).isEqualTo(Set.of("pdf", "xlsx"));
        assertThat(p.maxFileBytes()).isEqualTo(500 * MB);
        assertThat(p.maxTotalBytes()).isEqualTo(200 * MB);
        assertThat(p.maxFileCount()).isEqualTo(15);
        assertThat(p.zipAllowed()).isEqualTo(false);
        assertThat(p.maxFileNameLength()).isEqualTo(50);
        assertThat(e.ceilingNotes().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("the system ceiling lowers an over-large limit and says so; an unstated size is bounded by it too")
    void ceiling() {
        SubmissionProfile tender = new SubmissionProfile("Big", null, 500 * MB, null, null, null, null);
        SubmissionProfileResolver.Effective e = SubmissionProfileResolver.resolve(null, null, tender, 100 * MB, 300 * MB);

        assertThat(e.profile().maxFileBytes()).isEqualTo(100 * MB);
        assertThat(e.profile().maxTotalBytes()).isEqualTo(300 * MB);
        assertThat(e.ceilingNotes().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("ZIP is only offered when the profile says so; unstated means no")
    void zipOffer() {
        assertThat(SubmissionProfile.unstated("x").offersZip()).isEqualTo(false);
        assertThat(new SubmissionProfile("x", null, null, null, null, true, null).offersZip()).isEqualTo(true);
    }

    @Test
    @DisplayName("a limit of zero or less is refused")
    void zeroLimit() {
        assertThatThrownBy(() -> new SubmissionProfile("x", null, 0L, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- validation

    @Test
    @DisplayName("a package inside every limit has no issues")
    void clean() {
        SubmissionProfile p = new SubmissionProfile("p", Set.of("pdf", "xlsx"), 10 * MB, 50 * MB, 5, false, 40);
        List<PackageIssue> issues = PackageValidator.validate(p, List.of(
                pdf("a.pdf", 2 * MB, "h1", PdfHealth.OK), file("b.xlsx", MB, "h2")));
        assertThat(issues.size()).isEqualTo(0);
        assertThat(PackageValidator.canBuild(issues)).isEqualTo(true);
    }

    @Test
    @DisplayName("too large overall reports the real numbers")
    void totalTooLarge() {
        SubmissionProfile p = new SubmissionProfile("p", null, null, 100 * MB, null, null, null);
        List<PackageIssue> issues = PackageValidator.validate(p, List.of(file("a.pdf", 150 * MB, "h1"), file("b.pdf", 34 * MB + 600 * 1024, "h2")));
        assertThat(has(issues, "TOTAL_TOO_LARGE")).isEqualTo(true);
        String message = issues.stream().filter(i -> i.code().equals("TOTAL_TOO_LARGE")).findFirst().orElseThrow().message();
        assertThat(message.contains("184.6 MB")).isEqualTo(true);
        assertThat(message.contains("100.0 MB")).isEqualTo(true);
        assertThat(message.contains("84.6 MB too large")).isEqualTo(true);
        assertThat(PackageValidator.canBuild(issues)).isEqualTo(false);
    }

    @Test
    @DisplayName("one file over the per-file limit, a disallowed format and too many files are each blocking")
    void fileLevelLimits() {
        SubmissionProfile p = new SubmissionProfile("p", Set.of("pdf"), 5 * MB, null, 1, null, null);
        List<PackageIssue> issues = PackageValidator.validate(p, List.of(pdf("big.pdf", 6 * MB, "h1", PdfHealth.OK), file("sheet.xlsx", MB, "h2")));
        assertThat(has(issues, "FILE_TOO_LARGE")).isEqualTo(true);
        assertThat(has(issues, "FORMAT_NOT_ALLOWED")).isEqualTo(true);
        assertThat(has(issues, "TOO_MANY_FILES")).isEqualTo(true);
    }

    @Test
    @DisplayName("an Excel file the profile allows is kept, not flagged")
    void excelAllowed() {
        SubmissionProfile p = new SubmissionProfile("p", Set.of("pdf", "xlsx"), null, null, null, null, null);
        assertThat(PackageValidator.validate(p, List.of(file("Pricing_Schedule.xlsx", MB, "h"))).size()).isEqualTo(0);
    }

    @Test
    @DisplayName("encrypted and unreadable PDFs block with a plain instruction")
    void badPdfs() {
        List<PackageIssue> issues = PackageValidator.validate(SubmissionProfile.unstated("p"), List.of(
                pdf("locked.pdf", MB, "h1", PdfHealth.ENCRYPTED), pdf("broken.pdf", MB, "h2", PdfHealth.UNREADABLE)));
        assertThat(has(issues, "PDF_ENCRYPTED")).isEqualTo(true);
        assertThat(has(issues, "PDF_UNREADABLE")).isEqualTo(true);
    }

    @Test
    @DisplayName("same name twice blocks; same content under two names warns")
    void duplicates() {
        List<PackageIssue> issues = PackageValidator.validate(SubmissionProfile.unstated("p"), List.of(
                file("a.pdf", MB, "h1"), file("A.PDF", MB, "h2"), file("copy.pdf", MB, "h1")));
        assertThat(has(issues, "DUPLICATE_NAME")).isEqualTo(true);
        assertThat(has(issues, "DUPLICATE_CONTENT")).isEqualTo(true);
        assertThat(issues.stream().filter(i -> i.code().equals("DUPLICATE_CONTENT")).findFirst().orElseThrow().blocking()).isEqualTo(false);
    }

    @Test
    @DisplayName("empty file, long name and a forbidden character are caught")
    void names() {
        SubmissionProfile p = new SubmissionProfile("p", null, null, null, null, null, 12);
        List<PackageIssue> issues = PackageValidator.validate(p, List.of(
                file("empty.pdf", 0, "h1"), file("a-very-long-file-name.pdf", MB, "h2"), file("bad:name.pdf", MB, "h3")));
        assertThat(has(issues, "EMPTY_FILE")).isEqualTo(true);
        assertThat(has(issues, "FILE_NAME_TOO_LONG")).isEqualTo(true);
        assertThat(has(issues, "FILE_NAME_CHARACTER")).isEqualTo(true);
    }

    @Test
    @DisplayName("a file with no extension is refused when formats are restricted")
    void noExtension() {
        SubmissionProfile p = new SubmissionProfile("p", Set.of("pdf"), null, null, null, null, null);
        assertThat(has(PackageValidator.validate(p, List.of(file("README", MB, "h"))), "FORMAT_NOT_ALLOWED")).isEqualTo(true);
    }

    @Test
    @DisplayName("exactly at the limit is allowed; one byte over is not (total, file, count)")
    void boundaries() {
        SubmissionProfile p = new SubmissionProfile("p", null, 10 * MB, 20 * MB, 2, null, null);
        assertThat(PackageValidator.validate(p, List.of(file("a.pdf", 10 * MB, "h1"), file("b.pdf", 10 * MB, "h2"))).size()).isEqualTo(0);
        List<PackageIssue> over = PackageValidator.validate(p, List.of(file("a.pdf", 10 * MB + 1, "h1"), file("b.pdf", 10 * MB, "h2")));
        assertThat(has(over, "TOTAL_TOO_LARGE")).isEqualTo(true);
        assertThat(has(over, "FILE_TOO_LARGE")).isEqualTo(true);
        assertThat(has(PackageValidator.validate(p, List.of(file("a.pdf", MB, "h1"), file("b.pdf", MB, "h2"), file("c.pdf", MB, "h3"))), "TOO_MANY_FILES")).isEqualTo(true);
    }

    @Test
    @DisplayName("a profile limit equal to the ceiling is untouched and silent; one byte over is lowered with a note")
    void ceilingBoundary() {
        SubmissionProfile equal = new SubmissionProfile("p", null, 100 * MB, null, null, null, null);
        assertThat(SubmissionProfileResolver.resolve(null, null, equal, 100 * MB, 300 * MB).ceilingNotes().size()).isEqualTo(0);
        SubmissionProfile over = new SubmissionProfile("p", null, 100 * MB + 1, null, null, null, null);
        SubmissionProfileResolver.Effective e = SubmissionProfileResolver.resolve(null, null, over, 100 * MB, 300 * MB);
        assertThat(e.profile().maxFileBytes()).isEqualTo(100 * MB);
        assertThat(e.ceilingNotes().size()).isEqualTo(1);
    }

    // ---------- file kinds

    @Test
    @DisplayName("file kind comes from the extension, case-insensitively")
    void kinds() {
        assertThat(FileKind.of("A.PDF")).isEqualTo(FileKind.PDF);
        assertThat(FileKind.of("scan.JPEG")).isEqualTo(FileKind.IMAGE);
        assertThat(FileKind.of("cv.docx")).isEqualTo(FileKind.WORD);
        assertThat(FileKind.of("p.xlsx")).isEqualTo(FileKind.EXCEL);
        assertThat(FileKind.of("x")).isEqualTo(FileKind.OTHER);
        assertThat(FileKind.of("x.")).isEqualTo(FileKind.OTHER);
    }

    // ---------- manifest

    @Test
    @DisplayName("same files in the same order give the same hash; a change in content, name or order changes it")
    void manifestHash() {
        PackageFile a = file("a.pdf", 10, "aa");
        PackageFile b = file("b.pdf", 20, "bb");
        String base = PackageManifest.of(List.of(a, b)).packageHash();

        assertThat(PackageManifest.of(List.of(a, b)).packageHash()).isEqualTo(base);
        assertThat(PackageManifest.of(List.of(b, a)).packageHash().equals(base)).isEqualTo(false);
        assertThat(PackageManifest.of(List.of(a, file("b.pdf", 20, "cc"))).packageHash().equals(base)).isEqualTo(false);
        assertThat(PackageManifest.of(List.of(a, file("c.pdf", 20, "bb"))).packageHash().equals(base)).isEqualTo(false);
    }

    @Test
    @DisplayName("manifest positions start at 1 and the total adds up")
    void manifestShape() {
        PackageManifest m = PackageManifest.of(List.of(file("a.pdf", 10, "aa"), file("b.pdf", 20, "bb")));
        assertThat(m.entries().get(0).position()).isEqualTo(1);
        assertThat(m.entries().get(1).position()).isEqualTo(2);
        assertThat(m.totalBytes()).isEqualTo(30L);
    }

    @Test
    @DisplayName("SHA-256 of 'abc' matches the published test vector")
    void sha() {
        assertThat(PackageManifest.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
