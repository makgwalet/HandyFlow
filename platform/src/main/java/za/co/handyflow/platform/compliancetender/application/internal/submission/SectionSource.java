package za.co.handyflow.platform.compliancetender.application.internal.submission;

/**
 * Where a section's content comes from (readiness, pricing, personnel, documents, typed text...). One
 * implementation per section type. A source reads through the module's own services and facades; the
 * builder never reaches into repositories. A source that cannot produce its section returns
 * {@link SectionContent#unavailable} with the reason in words, it does not throw for an empty section.
 */
public interface SectionSource {

    SectionType type();

    SectionContent load(BuildContext context);
}
