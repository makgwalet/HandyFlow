package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.List;

/**
 * What a build would contain and whether it may go ahead, decided before any PDF is made.
 *
 * @param canBuild        no blocking issue: a draft package can be built
 * @param submissionReady a complete package: buildable, every chosen section produced, and pricing present when the tender requires it.
 *                        A READ user's draft without pricing can be built but is never submission-ready.
 */
public record PackagePlan(List<PlannedSection> sections, List<PackageFile> files, List<PackageIssue> issues,
                          boolean canBuild, boolean submissionReady) {

    public record PlannedSection(SectionType type, SectionContent content) {}
}
