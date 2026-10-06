package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.List;

/**
 * What a section source produced for one tender: text the renderer turns into pages, files that are
 * attached as they are, and the reason when the section cannot be produced (for example pricing the
 * caller may not include). A section that is unavailable says why; it is never silently dropped.
 */
public record SectionContent(String text, List<PackageFile> files, String unavailableReason) {

    public SectionContent {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static SectionContent of(String text, List<PackageFile> files) { return new SectionContent(text, files, null); }

    public static SectionContent unavailable(String reason) { return new SectionContent(null, List.of(), reason); }

    public boolean available() { return unavailableReason == null; }
}
