package za.co.handyflow.platform.compliancetender.dto;

import java.util.List;

/**
 * The outcome of a preview or a build: what each section produced, every issue found, and the built package when
 * there was one. {@code built} is null for a preview or when a blocking issue stopped the build.
 */
public record TenderPackagePlanResponse(
        boolean canBuild, boolean submissionReady, List<SectionStatus> sections, List<Issue> issues,
        List<String> limitNotes, TenderPackageResponse built
) {
    public record SectionStatus(String key, String title, boolean available, String unavailableReason, int fileCount) {}

    /** severity is BLOCKING (the build stops) or WARNING (shown, does not stop it). */
    public record Issue(String severity, String code, String message, String fileName) {}
}
