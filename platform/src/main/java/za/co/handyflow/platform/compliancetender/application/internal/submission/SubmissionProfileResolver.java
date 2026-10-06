package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.ArrayList;
import java.util.List;

/**
 * Layers the three levels (global, tenant, tender override) and then applies the system ceiling, which is
 * an operational limit (memory) and not a business rule (ADR-005 decision 5). The ceiling can only lower a
 * size; when it does, the result says so, so a build that is refused for the ceiling can show the real
 * numbers instead of blaming the tender.
 */
public final class SubmissionProfileResolver {

    private SubmissionProfileResolver() {}

    /** @param ceilingNotes one line per limit the system ceiling lowered; empty when it changed nothing */
    public record Effective(SubmissionProfile profile, List<String> ceilingNotes) {}

    public static Effective resolve(SubmissionProfile global, SubmissionProfile tenant, SubmissionProfile tenderOverride,
                                    long systemMaxFileBytes, long systemMaxTotalBytes) {
        SubmissionProfile layered = (global == null ? SubmissionProfile.unstated(null) : global)
                .overriddenBy(tenant)
                .overriddenBy(tenderOverride);

        List<String> notes = new ArrayList<>();
        Long fileBytes = capped(layered.maxFileBytes(), systemMaxFileBytes, "Maximum file size", notes);
        Long totalBytes = capped(layered.maxTotalBytes(), systemMaxTotalBytes, "Maximum total size", notes);

        SubmissionProfile effective = new SubmissionProfile(layered.name(), layered.allowedExtensions(), fileBytes, totalBytes,
                layered.maxFileCount(), layered.zipAllowed(), layered.maxFileNameLength());
        return new Effective(effective, List.copyOf(notes));
    }

    /** A limit nobody stated is bounded by the ceiling too, otherwise "no limit" would mean no protection. */
    private static Long capped(Long stated, long ceiling, String what, List<String> notes) {
        if (stated == null) return ceiling;
        if (stated > ceiling) {
            notes.add(what + " is " + stated + " bytes in the profile but this system allows at most " + ceiling + " bytes.");
            return ceiling;
        }
        return stated;
    }
}
