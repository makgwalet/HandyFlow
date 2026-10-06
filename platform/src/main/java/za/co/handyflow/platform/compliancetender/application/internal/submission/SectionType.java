package za.co.handyflow.platform.compliancetender.application.internal.submission;

/**
 * A kind of section a package can hold. A record keyed by a string, not an enum, so a section type that
 * is added later (Experience, Equipment, Methodology, HSE...) is a new catalogue entry plus a source and
 * renderer, and a package stored with a key this build does not know is shown as unavailable instead of
 * failing to load.
 *
 * @param needsPricingAuthority true when including the section needs COMPLIANCE_MANAGE/ADMIN (ADR-004)
 */
public record SectionType(String key, String title, int defaultOrder, boolean needsPricingAuthority) {

    public SectionType {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A section type needs a key.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A section type needs a title.");
    }
}
