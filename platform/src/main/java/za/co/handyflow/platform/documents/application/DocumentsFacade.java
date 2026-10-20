package za.co.handyflow.platform.documents.application;

import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Document Service as other modules use it. Every call is limited to the tenant given. The calling module decides who may see or change its
 * own owners' documents (and that the owner exists); this service enforces tenant isolation, file checks, the malware-scan hook, versioning,
 * archiving and the audit record.
 */
public interface DocumentsFacade {

    record FileUpload(String fileName, String contentType, byte[] content) {}

    record VersionInfo(UUID id, int versionNo, String fileName, String contentType, long sizeBytes, String sha256, String scanStatus,
                       String note, UUID uploadedBy, Instant uploadedAt) {}

    record DocumentInfo(UUID id, String ownerType, UUID ownerId, String category, String title, String description, LocalDate expiresOn,
                        String status, String archivedReason, int currentVersion, VersionInfo current, Instant createdAt, UUID createdBy) {}

    record Download(byte[] content, String fileName, String contentType) {}

    /** Stores a first version. Refused for an invalid or infected file. */
    DocumentInfo upload(TenantId tenantId, String ownerType, UUID ownerId, String category, String title, String description,
                        LocalDate expiresOn, FileUpload file, UUID actor);

    /** Adds a version. A non-null {@code renewedExpiry} replaces the expiry date (a renewal). */
    DocumentInfo addVersion(TenantId tenantId, UUID documentId, FileUpload file, String note, LocalDate renewedExpiry, UUID actor);

    DocumentInfo updateDetails(TenantId tenantId, UUID documentId, String title, String description, LocalDate expiresOn, UUID actor);

    /** Takes the document out of use. Its versions and files are kept. */
    DocumentInfo archive(TenantId tenantId, UUID documentId, String reason, UUID actor);

    Optional<DocumentInfo> find(TenantId tenantId, UUID documentId);

    List<DocumentInfo> list(TenantId tenantId, String ownerType, UUID ownerId, boolean includeArchived);

    List<VersionInfo> versions(TenantId tenantId, UUID documentId);

    /** The latest version, or the numbered one. The download is audited. */
    Download download(TenantId tenantId, UUID documentId, Integer versionNo, UUID actor);

    /** Active documents of these owner types that have expired or expire on or before {@code until}. */
    List<DocumentInfo> expiring(TenantId tenantId, Collection<String> ownerTypes, LocalDate until);

    /** Tenants with at least one such document. */
    List<TenantId> tenantsWithExpiring(Collection<String> ownerTypes, LocalDate until);

    long countActive(TenantId tenantId, String ownerType, UUID ownerId, String category);

    /** Convenience for callers that want only a few ids. */
    default Set<UUID> ids(Collection<DocumentInfo> docs) { return docs.stream().map(DocumentInfo::id).collect(java.util.stream.Collectors.toSet()); }
}
