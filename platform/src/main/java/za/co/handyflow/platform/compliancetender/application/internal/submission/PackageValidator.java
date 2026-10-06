package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.compliancetender.application.internal.submission.PackageIssue.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks a package's files against the effective submission profile before anything is built. Pure: no
 * repositories, no PDF library. Nothing is ever silently dropped; a file the profile does not allow is a
 * blocking issue naming the file, so the person decides.
 */
public final class PackageValidator {

    private static final String FORBIDDEN_NAME_CHARS = "\\/:*?\"<>|";

    private PackageValidator() {}

    public static List<PackageIssue> validate(SubmissionProfile profile, List<PackageFile> files) {
        List<PackageIssue> issues = new ArrayList<>();
        long total = 0;
        Map<String, PackageFile> byHash = new HashMap<>();
        Set<String> namesSeen = new HashSet<>();

        for (PackageFile file : files) {
            total += file.sizeBytes();
            checkFile(profile, file, issues);

            if (!namesSeen.add(file.fileName().toLowerCase(Locale.ROOT))) {
                issues.add(blocking("DUPLICATE_NAME", file.fileName() + " appears more than once. File names must be unique in a package.", file.fileName()));
            }
            PackageFile first = file.sha256() == null ? null : byHash.putIfAbsent(file.sha256(), file);
            if (first != null) {
                issues.add(warning("DUPLICATE_CONTENT", file.fileName() + " has the same content as " + first.fileName() + ".", file.fileName()));
            }
        }

        if (profile.maxFileCount() != null && files.size() > profile.maxFileCount()) {
            issues.add(blocking("TOO_MANY_FILES", "The package has " + files.size() + " files; the submission allows " + profile.maxFileCount() + ".", null));
        }
        if (profile.maxTotalBytes() != null && total > profile.maxTotalBytes()) {
            issues.add(blocking("TOTAL_TOO_LARGE", "The package is " + megabytes(total) + " MB; the submission allows " + megabytes(profile.maxTotalBytes())
                    + " MB. It is " + megabytes(total - profile.maxTotalBytes()) + " MB too large.", null));
        }
        return List.copyOf(issues);
    }

    /**
     * The checks that apply to what goes IN, before anything is merged. PDFs and images are merged into one
     * combined PDF, so their individual names, sizes and the number of them say nothing about what the portal
     * receives; only whether they can be used at all matters (empty, password protected, unreadable) and
     * whether the same content is in twice. Word, Excel and other originals are delivered as they are, so they
     * get the full per-file checks. Size, count and names of what is delivered are checked by
     * {@link #validate} on the output, after the merge.
     */
    public static List<PackageIssue> validateInputs(SubmissionProfile profile, List<PackageFile> files) {
        List<PackageIssue> issues = new ArrayList<>();
        Map<String, PackageFile> byHash = new HashMap<>();
        Set<String> originalNames = new HashSet<>();
        for (PackageFile file : files) {
            FileKind kind = file.kind();
            if (kind == FileKind.PDF || kind == FileKind.IMAGE) {
                if (file.sizeBytes() <= 0) issues.add(blocking("EMPTY_FILE", file.fileName() + " is empty.", file.fileName()));
                checkPdfHealth(file, issues);
            } else {
                checkFile(profile, file, issues);
                if (!originalNames.add(file.fileName().toLowerCase(Locale.ROOT))) {
                    issues.add(blocking("DUPLICATE_NAME", file.fileName() + " appears more than once. File names must be unique in a package.", file.fileName()));
                }
            }
            PackageFile first = file.sha256() == null ? null : byHash.putIfAbsent(file.sha256(), file);
            if (first != null) {
                issues.add(warning("DUPLICATE_CONTENT", file.fileName() + " has the same content as " + first.fileName() + ".", file.fileName()));
            }
        }
        return List.copyOf(issues);
    }

    public static boolean canBuild(List<PackageIssue> issues) { return issues.stream().noneMatch(PackageIssue::blocking); }

    private static void checkFile(SubmissionProfile profile, PackageFile file, List<PackageIssue> issues) {
        String name = file.fileName();
        if (file.sizeBytes() <= 0) {
            issues.add(blocking("EMPTY_FILE", name + " is empty.", name));
        }
        if (profile.maxFileBytes() != null && file.sizeBytes() > profile.maxFileBytes()) {
            issues.add(blocking("FILE_TOO_LARGE", name + " is " + megabytes(file.sizeBytes()) + " MB; one file may be at most " + megabytes(profile.maxFileBytes()) + " MB.", name));
        }
        String extension = FileKind.extensionOf(name);
        if (profile.allowedExtensions() != null && !profile.allowedExtensions().contains(extension)) {
            issues.add(blocking("FORMAT_NOT_ALLOWED", name + " is a ." + (extension.isEmpty() ? "(no extension)" : extension)
                    + " file; the submission accepts " + String.join(", ", new java.util.TreeSet<>(profile.allowedExtensions())) + ".", name));
        }
        if (profile.maxFileNameLength() != null && name.length() > profile.maxFileNameLength()) {
            issues.add(blocking("FILE_NAME_TOO_LONG", name + " has " + name.length() + " characters; at most " + profile.maxFileNameLength() + " are allowed.", name));
        }
        for (char c : FORBIDDEN_NAME_CHARS.toCharArray()) {
            if (name.indexOf(c) >= 0) {
                issues.add(blocking("FILE_NAME_CHARACTER", name + " contains \"" + c + "\", which portals and file systems often refuse.", name));
                break;
            }
        }
        checkPdfHealth(file, issues);
    }

    private static void checkPdfHealth(PackageFile file, List<PackageIssue> issues) {
        String name = file.fileName();
        if (file.pdfHealth() == PdfHealth.ENCRYPTED) {
            issues.add(blocking("PDF_ENCRYPTED", name + " is password protected and cannot be merged. Upload an unprotected copy.", name));
        } else if (file.pdfHealth() == PdfHealth.UNREADABLE) {
            issues.add(blocking("PDF_UNREADABLE", name + " could not be opened and may be damaged. Upload it again or a repaired copy.", name));
        }
    }

    private static PackageIssue blocking(String code, String message, String fileName) { return new PackageIssue(Severity.BLOCKING, code, message, fileName); }

    private static PackageIssue warning(String code, String message, String fileName) { return new PackageIssue(Severity.WARNING, code, message, fileName); }

    static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }
}
