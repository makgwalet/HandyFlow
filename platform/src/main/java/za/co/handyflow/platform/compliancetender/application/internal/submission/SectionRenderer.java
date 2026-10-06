package za.co.handyflow.platform.compliancetender.application.internal.submission;

/**
 * Turns a section's text into PDF bytes. Kept behind an interface so the package service does not know
 * how pages are drawn, and so the section text can be tested without a PDF library.
 */
public interface SectionRenderer {

    SectionType type();

    byte[] render(String tenderTitle, SectionType section, SectionContent content);
}
