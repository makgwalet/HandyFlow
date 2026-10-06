package za.co.handyflow.platform.compliancetender.application.internal.submission;

/**
 * A problem found before a package is built. BLOCKING issues stop the build, because the portal would
 * refuse the submission; WARNING issues are shown and do not stop it.
 */
public record PackageIssue(Severity severity, String code, String message, String fileName) {

    public enum Severity { BLOCKING, WARNING }

    public boolean blocking() { return severity == Severity.BLOCKING; }
}
