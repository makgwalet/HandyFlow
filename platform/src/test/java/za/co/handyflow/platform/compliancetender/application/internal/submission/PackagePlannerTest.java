package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.application.internal.submission.PackagePlan.PlannedSection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Decision 2 and 6 of ADR-005: drafts without pricing, submission-ready only when complete, nothing dropped silently. */
class PackagePlannerTest {

    private static final SubmissionProfile ANY = SubmissionProfile.unstated("any");

    private static PackageFile file(String name, String hash) {
        return new PackageFile("SUPPORTING_DOCUMENTS", name, 1000, hash, null, PdfHealth.UNKNOWN, null);
    }

    private static PlannedSection produced(SectionType type, PackageFile... files) {
        return new PlannedSection(type, SectionContent.of("text", List.of(files)));
    }

    private static PlannedSection missing(SectionType type, String why) {
        return new PlannedSection(type, SectionContent.unavailable(why));
    }

    private static boolean has(PackagePlan plan, String code) {
        return plan.issues().stream().anyMatch(i -> i.code().equals(code));
    }

    @Test
    @DisplayName("everything produced and no pricing required: buildable and ready")
    void complete() {
        PackagePlan plan = PackagePlanner.plan(List.of(
                produced(SectionCatalogue.COVER_LETTER), produced(SectionCatalogue.SUPPORTING_DOCUMENTS, file("a.pdf", "h1"))), ANY, false);
        assertThat(plan.canBuild()).isEqualTo(true);
        assertThat(plan.submissionReady()).isEqualTo(true);
        assertThat(plan.files().size()).isEqualTo(1);
        assertThat(plan.issues().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("a READ user's draft: pricing unavailable, tender requires it -> can build, never ready, both reasons shown")
    void draftWithoutPricing() {
        PackagePlan plan = PackagePlanner.plan(List.of(
                produced(SectionCatalogue.COVER_LETTER),
                missing(SectionCatalogue.PRICING, "you need manage permission to include pricing")), ANY, true);
        assertThat(plan.canBuild()).isEqualTo(true);
        assertThat(plan.submissionReady()).isEqualTo(false);
        assertThat(has(plan, "SECTION_UNAVAILABLE")).isEqualTo(true);
        assertThat(has(plan, "PRICING_REQUIRED")).isEqualTo(true);
    }

    @Test
    @DisplayName("pricing not chosen at all but required: still not ready")
    void pricingNotChosen() {
        PackagePlan plan = PackagePlanner.plan(List.of(produced(SectionCatalogue.COVER_LETTER)), ANY, true);
        assertThat(plan.submissionReady()).isEqualTo(false);
        assertThat(has(plan, "PRICING_REQUIRED")).isEqualTo(true);
    }

    @Test
    @DisplayName("a MANAGE user with pricing in and a tender that requires it: ready")
    void pricingIncluded() {
        PackagePlan plan = PackagePlanner.plan(List.of(produced(SectionCatalogue.COVER_LETTER), produced(SectionCatalogue.PRICING)), ANY, true);
        assertThat(plan.submissionReady()).isEqualTo(true);
        assertThat(has(plan, "PRICING_REQUIRED")).isEqualTo(false);
    }

    @Test
    @DisplayName("pricing missing but the tender does not require it: buildable, and ready only if the person did not choose it")
    void pricingOptional() {
        PackagePlan notChosen = PackagePlanner.plan(List.of(produced(SectionCatalogue.COVER_LETTER)), ANY, false);
        assertThat(notChosen.submissionReady()).isEqualTo(true);
        PackagePlan chosenButMissing = PackagePlanner.plan(List.of(missing(SectionCatalogue.PRICING, "no lines")), ANY, false);
        assertThat(chosenButMissing.submissionReady()).isEqualTo(false);
    }

    @Test
    @DisplayName("any other chosen section that could not be produced makes the package not ready, and says which")
    void otherSectionMissing() {
        PackagePlan plan = PackagePlanner.plan(List.of(missing(SectionCatalogue.KEY_PERSONNEL, "no personnel added")), ANY, false);
        assertThat(plan.canBuild()).isEqualTo(true);
        assertThat(plan.submissionReady()).isEqualTo(false);
        assertThat(plan.issues().get(0).message().contains("Key personnel")).isEqualTo(true);
        assertThat(plan.issues().get(0).message().contains("no personnel added")).isEqualTo(true);
    }

    @Test
    @DisplayName("a blocking profile violation stops the build and readiness")
    void blocked() {
        SubmissionProfile pdfOnly = new SubmissionProfile("p", java.util.Set.of("pdf"), null, null, null, null, null);
        PackagePlan plan = PackagePlanner.plan(List.of(produced(SectionCatalogue.SUPPORTING_DOCUMENTS, file("sheet.xlsx", "h"))), pdfOnly, false);
        assertThat(plan.canBuild()).isEqualTo(false);
        assertThat(plan.submissionReady()).isEqualTo(false);
        assertThat(has(plan, "FORMAT_NOT_ALLOWED")).isEqualTo(true);
    }

    @Test
    @DisplayName("files keep the order of the chosen sections")
    void order() {
        PackagePlan plan = PackagePlanner.plan(List.of(
                produced(SectionCatalogue.COMPLIANCE, file("c.pdf", "h3")), produced(SectionCatalogue.SUPPORTING_DOCUMENTS, file("a.pdf", "h1"), file("b.pdf", "h2"))), ANY, false);
        assertThat(plan.files().get(0).fileName()).isEqualTo("c.pdf");
        assertThat(plan.files().get(2).fileName()).isEqualTo("b.pdf");
    }
}
