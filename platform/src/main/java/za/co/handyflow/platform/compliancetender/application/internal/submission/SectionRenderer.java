package za.co.handyflow.platform.compliancetender.application.internal.submission;

/**
 * Turns a section's text into PDF bytes. Behind an interface so the package service does not know how
 * pages are drawn and can be tested without a PDF library.
 */
public interface SectionRenderer {

    byte[] render(String tenderTitle, SectionType section, String text);
}
