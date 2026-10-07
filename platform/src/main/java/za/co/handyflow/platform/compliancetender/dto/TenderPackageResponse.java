package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A built package version. Immutable once built. */
public record TenderPackageResponse(
        UUID id, UUID tenderId, int versionNo, boolean submissionReady, boolean includesPricing, String profileName,
        String packageHash, String fileName, long sizeBytes, Integer pageCount, Instant createdAt, String createdByName,
        List<FileEntry> files,
        /** True when the tender has changed since this version was built; the reasons are in plain words. */
        boolean stale, List<String> staleReasons
) {
    /** source is GENERATED (a section this system drew), ATTACHED (a document merged in) or ORIGINAL (kept as uploaded). */
    public record FileEntry(int position, String sectionKey, String fileName, String source, long sizeBytes, String sha256, Integer pages) {}
}
