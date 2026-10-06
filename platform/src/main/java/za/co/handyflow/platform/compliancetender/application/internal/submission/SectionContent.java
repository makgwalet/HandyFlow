package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.List;

/**
 * What a section source produced for one tender: text the renderer turns into pages, files attached as
 * they are (with their bytes, so they can be merged or kept), and the reason when the section cannot be
 * produced (for example pricing the caller may not include). A section that is unavailable says why; it is
 * never silently dropped.
 */
public record SectionContent(String text, List<Attachment> attachments, String unavailableReason) {

    /** A file and its bytes; the {@link PackageFile} is the description the validator and manifest use. */
    public record Attachment(PackageFile file, byte[] bytes) {}

    public SectionContent {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static SectionContent of(String text, List<Attachment> attachments) { return new SectionContent(text, attachments, null); }

    public static SectionContent textOnly(String text) { return new SectionContent(text, List.of(), null); }

    public static SectionContent unavailable(String reason) { return new SectionContent(null, List.of(), reason); }

    public boolean available() { return unavailableReason == null; }

    public boolean hasText() { return text != null && !text.isBlank(); }

    public List<PackageFile> files() { return attachments.stream().map(Attachment::file).toList(); }
}
