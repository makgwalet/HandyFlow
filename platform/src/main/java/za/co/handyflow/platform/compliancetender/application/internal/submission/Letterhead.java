package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.List;

/**
 * Who is writing and about which tender: what the generated pages put at the top and bottom. The logo is
 * optional; without one the page shows the company name alone.
 */
public record Letterhead(String companyName, List<String> contactLines, byte[] logo, String tenderNumber, String tenderName,
                         String authority, String authorityReference, String closingDate) {

    public Letterhead {
        contactLines = contactLines == null ? List.of() : List.copyOf(contactLines);
    }

    /** A letterhead with no company details, for tests and for a tenant that has none recorded. */
    public static Letterhead plain(String tenderName) {
        return new Letterhead(null, List.of(), null, null, tenderName, null, null, null);
    }

    public boolean hasCompany() { return companyName != null && !companyName.isBlank(); }
}
