package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.compliancetender.application.internal.submission.PackageIssue.Severity;
import za.co.handyflow.platform.compliancetender.application.internal.submission.PackagePlan.PlannedSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure planning step of the package builder (ADR-005 decisions 1, 2 and 6): takes the sections the person
 * chose, in the order they chose, with what each produced, and says what the package would hold and
 * whether it can be built or submitted. No repositories and no PDF library.
 * <p>
 * Only input checks run here ({@link PackageValidator#validateInputs}); size, count and names of what is
 * delivered are checked on the output after the merge.
 * <p>
 * A section that could not be produced (pricing the caller may not include, no pricing lines yet) is
 * reported, never silently left out. A draft may be built without it; a package is only
 * {@code submissionReady} when nothing chosen is missing and, if the tender requires pricing, pricing is in.
 */
public final class PackagePlanner {

    private PackagePlanner() {}

    public static PackagePlan plan(List<PlannedSection> chosen, SubmissionProfile profile, boolean tenderRequiresPricing) {
        List<PackageFile> files = new ArrayList<>();
        List<PackageIssue> issues = new ArrayList<>();
        boolean allProduced = true;
        boolean pricingIncluded = false;

        for (PlannedSection section : chosen) {
            SectionContent content = section.content();
            if (content.available()) {
                files.addAll(content.files());
                if (section.type().needsPricingAuthority()) pricingIncluded = true;
            } else {
                allProduced = false;
                issues.add(new PackageIssue(Severity.WARNING, "SECTION_UNAVAILABLE",
                        section.type().title() + " is not in this package: " + content.unavailableReason(), null));
            }
        }

        if (tenderRequiresPricing && !pricingIncluded) {
            issues.add(new PackageIssue(Severity.WARNING, "PRICING_REQUIRED",
                    "This tender requires pricing, so the package is incomplete and cannot be marked ready to submit without it.", null));
        }

        issues.addAll(PackageValidator.validateInputs(profile, files));
        boolean canBuild = PackageValidator.canBuild(issues);
        boolean ready = canBuild && allProduced && (!tenderRequiresPricing || pricingIncluded);
        return new PackagePlan(List.copyOf(chosen), List.copyOf(files), List.copyOf(issues), canBuild, ready);
    }
}
