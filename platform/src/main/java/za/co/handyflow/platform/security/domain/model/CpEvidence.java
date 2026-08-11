// security/domain/model/CpEvidence.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * CpEvidence — an evidentiary document attached to either a Principal or a
 * ProtectionDetail: ID scans, signed engagement letters, threat intelligence
 * files, medical documentation.
 *
 * WHY polymorphic (entityType/entityId) rather than two separate tables?
 * Same rationale as AuditEvent's generic entity_type/entity_id pair --
 * evidence at the Principal level (a standing threat-intel file that applies
 * across every engagement) and evidence at the ProtectionDetail level (a
 * signed engagement letter for one specific booking) share an identical
 * upload/list/soft-delete lifecycle. A typed subclass per attachment point
 * would duplicate that lifecycle for no benefit.
 *
 * WHY soft-delete (deletedAt) rather than hard delete?
 * Evidentiary material for a compliance-sensitive module -- deleting it
 * outright removes the ability to show "we did have this on file" if ever
 * challenged. A wrongly-uploaded file is hidden from normal views but the
 * record (and who removed it, and why) survives. Same posture as
 * DeclinedPrincipal and ArmouryLog being effectively append-only.
 *
 * WHY no field-level encryption on fileUrl (unlike Principal.medicalNotes)?
 * fileUrl points at wherever the actual file lives (S3 presigned URL in
 * production, "PENDING_UPLOAD" placeholder in dev -- same pattern as
 * GuardService.updatePhoto()). The file's own storage layer is responsible
 * for at-rest encryption; this row is a pointer + metadata, not the
 * sensitive content itself.
 */
@Entity
@Table(name = "security_cp_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CpEvidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(length = 1000)
    private String notes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static CpEvidence upload(TenantId tenantId, EntityType entityType, UUID entityId,
                                    Category category, String fileUrl, String fileName,
                                    String notes, UUID uploadedBy) {
        CpEvidence e   = new CpEvidence();
        e.tenantId     = tenantId;
        e.entityType   = entityType;
        e.entityId     = entityId;
        e.category     = category;
        e.fileUrl      = fileUrl;
        e.fileName     = fileName;
        e.notes        = notes;
        e.uploadedBy   = uploadedBy;
        e.createdAt    = Instant.now();
        return e;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void softDelete(UUID deletedBy, String reason) {
        if (this.deletedAt != null) {
            throw new IllegalStateException("Evidence already deleted");
        }
        this.deletedAt    = Instant.now();
        this.deletedBy    = deletedBy;
        this.deleteReason = reason;
    }

    public boolean isDeleted() { return deletedAt != null; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum EntityType {
        PRINCIPAL, PROTECTION_DETAIL
    }

    public enum Category {
        ID_DOCUMENT, ENGAGEMENT_LETTER, THREAT_INTEL, MEDICAL, OTHER
    }
}