package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.UUID;

/**
 * One file that is going into a package, described without its bytes so the validator and manifest stay
 * pure. {@code sha256} is the hex digest of the content. {@code pages} is only known for PDFs that opened.
 */
public record PackageFile(String sectionKey, String fileName, long sizeBytes, String sha256,
                          UUID evidenceId, PdfHealth pdfHealth, Integer pages) {

    public FileKind kind() { return FileKind.of(fileName); }
}
