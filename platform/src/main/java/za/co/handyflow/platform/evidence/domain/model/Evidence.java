package za.co.handyflow.platform.evidence.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A piece of evidence — any file attached to any other module's entity,
 * via the shared EvidenceFacade. Stage 0 of the Financial Control &
 * Assurance adoption plan.
 * <p>
 * Deliberately generic rather than one table per module (the pattern
 * AccFicaDocument/TaskAttachment/RecAgencyCandidate's CV fields each
 * independently reinvented) — sourceModule + relatedEntityType +
 * relatedEntityId identify what this is attached to, without a foreign
 * key into any specific module's schema, since evidence must be
 * attachable to entities in modules this one has no dependency on.
 * <p>
 * Stores only fileHash + storageKey, never file bytes directly — actual
 * content lives behind FileStorageService, exactly like TaskAttachment
 * and RecAgencyCandidate's CV field, NOT like AccFicaDocument's
 * file_content_base64 column. See EvidenceService for why that
 * distinction matters.
 */
@Entity
@Table(name = "evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;

    @Column(name = "file_name", nullable = false, length = 300) private String fileName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size_bytes", nullable = false) private long fileSizeBytes;
    @Column(name = "storage_key", nullable = false, length = 500) private String storageKey;

    @Column(name = "evidence_type", nullable = false, length = 50) private String evidenceType;
    @Column(name = "source_module", nullable = false, length = 50) private String sourceModule;
    @Column(name = "related_entity_type", nullable = false, length = 100) private String relatedEntityType;
    @Column(name = "related_entity_id", nullable = false) private UUID relatedEntityId;

    @Column(name = "period_id") private UUID periodId;

    @Column(name = "file_hash", nullable = false, length = 64) private String fileHash;
    @Column(name = "version", nullable = false) private int version = 1;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";

    @Column(name = "uploaded_by", nullable = false) private UUID uploadedBy;
    @Column(name = "uploaded_by_name") private String uploadedByName;

    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public static Evidence attach(UUID tenantId, String fileName, String contentType, long fileSizeBytes,
                                  String storageKey, String evidenceType, String sourceModule,
                                  String relatedEntityType, UUID relatedEntityId, UUID periodId,
                                  String fileHash, UUID uploadedBy, String uploadedByName) {
        Evidence e = new Evidence();
        e.tenantId = tenantId;
        e.fileName = fileName;
        e.contentType = contentType;
        e.fileSizeBytes = fileSizeBytes;
        e.storageKey = storageKey;
        e.evidenceType = evidenceType;
        e.sourceModule = sourceModule;
        e.relatedEntityType = relatedEntityType;
        e.relatedEntityId = relatedEntityId;
        e.periodId = periodId;
        e.fileHash = fileHash;
        e.uploadedBy = uploadedBy;
        e.uploadedByName = uploadedByName;
        return e;
    }

    /** Soft-remove — never hard-deletes a row that may already be cited as evidence elsewhere. */
    public void detach() {
        this.status = "DETACHED";
        this.updatedAt = Instant.now();
    }

    public void markReviewed(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}