package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.util.Set;

/**
 * The rules a submission has to meet, as a set of optional limits. A null limit means "not stated at
 * this level" (not "unlimited"): profiles are layered global, then tenant, then tender override, and a
 * non-null value at a later level replaces the earlier one ({@link #overriddenBy}). The numbers are
 * entered by people from the tender instructions; none are built in or assumed from a portal's name.
 *
 * @param allowedExtensions lower-case extensions the portal accepts, no dot; null = not restricted
 * @param zipAllowed        null = not stated, which the builder treats as "do not offer ZIP"
 * @param maxFileNameLength null = not stated
 */
public record SubmissionProfile(String name, Set<String> allowedExtensions, Long maxFileBytes, Long maxTotalBytes,
                                Integer maxFileCount, Boolean zipAllowed, Integer maxFileNameLength) {

    public SubmissionProfile {
        allowedExtensions = allowedExtensions == null ? null : Set.copyOf(allowedExtensions);
        requirePositive(maxFileBytes, "Maximum file size");
        requirePositive(maxTotalBytes, "Maximum total size");
        requirePositive(maxFileCount == null ? null : maxFileCount.longValue(), "Maximum file count");
        requirePositive(maxFileNameLength == null ? null : maxFileNameLength.longValue(), "Maximum file name length");
    }

    private static void requirePositive(Long value, String what) {
        if (value != null && value <= 0) throw new IllegalArgumentException(what + " must be more than zero when it is set.");
    }

    /** A profile that states nothing; the base of the layering. */
    public static SubmissionProfile unstated(String name) { return new SubmissionProfile(name, null, null, null, null, null, null); }

    /** Whatever the override states replaces this profile's value; what it leaves null is kept. */
    public SubmissionProfile overriddenBy(SubmissionProfile override) {
        if (override == null) return this;
        return new SubmissionProfile(
                override.name != null ? override.name : name,
                override.allowedExtensions != null ? override.allowedExtensions : allowedExtensions,
                override.maxFileBytes != null ? override.maxFileBytes : maxFileBytes,
                override.maxTotalBytes != null ? override.maxTotalBytes : maxTotalBytes,
                override.maxFileCount != null ? override.maxFileCount : maxFileCount,
                override.zipAllowed != null ? override.zipAllowed : zipAllowed,
                override.maxFileNameLength != null ? override.maxFileNameLength : maxFileNameLength);
    }

    /** The same profile without the whole-package size limit; used when the total is judged on the delivered ZIP rather than on its contents. */
    public SubmissionProfile withoutTotalLimit() {
        return new SubmissionProfile(name, allowedExtensions, maxFileBytes, null, maxFileCount, zipAllowed, maxFileNameLength);
    }

    public boolean offersZip() { return Boolean.TRUE.equals(zipAllowed); }
}
