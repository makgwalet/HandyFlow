package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.Locale;

/**
 * What a file in a submission package is, decided from its name. The package builder treats the kinds
 * differently (ADR-005): PDFs are merged, images are placed into a PDF, Word/Excel originals are kept
 * as they are (no Office conversion in V1), anything else is kept as an original.
 */
public enum FileKind {
    PDF("pdf"), IMAGE("jpg", "jpeg", "png"), WORD("doc", "docx"), EXCEL("xls", "xlsx"), ZIP("zip"), OTHER;

    private final String[] extensions;

    FileKind(String... extensions) { this.extensions = extensions; }

    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static FileKind of(String fileName) {
        String ext = extensionOf(fileName);
        for (FileKind kind : values()) {
            for (String candidate : kind.extensions) {
                if (candidate.equals(ext)) return kind;
            }
        }
        return OTHER;
    }
}
