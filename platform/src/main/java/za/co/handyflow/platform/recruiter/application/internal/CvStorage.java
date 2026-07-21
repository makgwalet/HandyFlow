package za.co.handyflow.platform.recruiter.application.internal;

/**
 * Port for CV file storage. Deliberately provider-agnostic — the rest of
 * the codebase depends only on this interface, never on a specific
 * storage backend. Same pattern as SmsSender (notifications module).
 * <p>
 * WHY NOT JUST KEEP BASE64-IN-DB? Confirmed real gap from the original
 * module audit: every CV bloats the rec_applicants row, drags along on
 * every SELECT * even when nobody asked for the CV, and gets no
 * CDN/caching benefit. This interface is the seam that lets that change
 * later without touching any calling code.
 * <p>
 * TO GO LIVE WITH A REAL OBJECT STORE (S3 or equivalent) LATER:
 *   1. Add the provider's SDK dependency.
 *   2. Implement this interface, e.g. S3CvStorage.
 *   3. Annotate it @ConditionalOnProperty(name = "recruiter.cv-storage.provider", havingValue = "s3").
 *   4. Set recruiter.cv-storage.provider=s3 in application.yml.
 * No other class needs to change.
 * <p>
 * NOTE ON SWITCHING PROVIDERS: this does NOT automatically migrate files
 * already stored under the old provider. RecruiterService.getCvBytes()
 * falls back to legacy raw-base64 decoding for old rows that predate this
 * abstraction entirely, but that fallback is specific to the base64 era —
 * a future local-to-S3 switch would need its own real migration script to
 * move files, not just a config flip.
 */
public interface CvStorage {

    /**
     * Stores the given bytes and returns an opaque reference string that
     * {@link #retrieve} can resolve back to the same bytes later. This
     * reference is what gets persisted in rec_applicants.cv_url — callers
     * must not assume anything about its format beyond "pass it back to
     * retrieve() unchanged".
     */
    String store(byte[] bytes, String fileName);

    /**
     * Resolves a reference previously returned by {@link #store} back to
     * the original bytes. Throws IllegalArgumentException if the
     * reference doesn't belong to this provider — callers use that to
     * detect "not one of mine" and fall back to other resolution paths
     * (see RecruiterService.getCvBytes()'s legacy-base64 fallback).
     */
    byte[] retrieve(String reference);
}