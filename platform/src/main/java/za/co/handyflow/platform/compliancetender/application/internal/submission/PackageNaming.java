package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.Locale;

/** File names the builder gives to what it generates. Plain ASCII, no characters portals and file systems refuse. */
public final class PackageNaming {

    private PackageNaming() {}

    /** "01-cover-letter.pdf" for position 1 of the Cover letter section. */
    public static String sectionFileName(int position, SectionType section) {
        return String.format(Locale.ROOT, "%02d-%s.pdf", position, slug(section.title()));
    }

    /** "TND-0042-submission-v3.pdf"; the tender number is cleaned, never trusted as typed. */
    public static String combinedFileName(String tenderNumber, int version) {
        String base = slug(tenderNumber == null || tenderNumber.isBlank() ? "tender" : tenderNumber);
        return base + "-submission-v" + version + ".pdf";
    }

    static String slug(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "file" : s;
    }
}
