package za.co.handyflow.platform.secureaccess.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Implemented by every module that shares documents through the engine (one bean per module code).
 * The engine owns access; the module owns the business record and the document, and decides, on every
 * request, whether the document may still leave the system.
 *
 * Every method is called with the tenant of the share. Implementations must filter by that tenant.
 * The engine fails CLOSED: if a provider throws or returns null, nothing is released.
 */
public interface SecureShareProvider {

    /** Upper-case code stored on shares and items, for example PROJECTS, CONTRACTING, FM. */
    String moduleCode();

    /** What recipients see at the top of the page and in the email. Null if the business record no longer exists. */
    ShareHeader describe(UUID tenantId, ShareRef share);

    /**
     * Asked on EVERY view and EVERY download of this item. Check here that the record still exists in the
     * tenant, is the issued revision, is not withdrawn or voided, and that the relationship that justified
     * the share (project, client, contract) is still valid.
     */
    Decision authorise(UUID tenantId, ShareRef share, DocumentRef doc, Who who);

    /**
     * The bytes of exactly the revision that was issued. Must verify that the file still matches
     * {@code issuedSha256} (when not null) and refuse otherwise. {@code actor} is for the module's own audit.
     */
    ReleasedFile release(UUID tenantId, ShareRef share, DocumentRef doc, String issuedSha256, String actor);

    /** Optional notifications back to the owning module (for example, every recipient has acknowledged). */
    default void onEvent(UUID tenantId, ShareRef share, ShareEvent event) {}

    // ── Types ────────────────────────────────────────────────────────────────

    record ShareRef(UUID shareId, String moduleCode, String sourceType, UUID sourceId, UUID contextId) {}

    /** A generic document reference: module code, source record type and source record id, plus the issued revision. */
    record DocumentRef(String moduleCode, String sourceType, UUID sourceId, String revision) {}

    record Who(UUID recipientId, UUID collaboratorId, String name, String email) {}

    record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, null); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }

    record ReleasedFile(String fileName, String contentType, byte[] content) {}

    /** reference is the module's own number (for example TR-003). contextName is e.g. the project name. */
    record ShareHeader(String reference, String subject, String contextName, String purposeLabel, String notes,
                       String sentBy, Instant sentAt) {}

    record ShareEvent(String type, UUID recipientId, String recipientName, boolean everyRecipientAcknowledged,
                      int activeRecipients) {
        public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    }
}
