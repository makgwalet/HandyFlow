// security/domain/model/GuardDocument.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * GuardDocument — a compliance document attached to a guard's file
 * (Guard File): ID copy, PSIRA certificate, proof of address, bank
 * confirmation, training/firearm/medical certificates, fingerprint form,
 * employment contract, POPIA consent, etc.
 *
 * Deliberately mirrors CpEvidence (V211, Close Protection module) exactly
 * rather than inventing a new pattern -- same category-enum shape,
 * soft-delete-only lifecycle, dev-mode base64 handling. See CpEvidence's
 * own javadoc for the fuller rationale on each of those choices; it
 * applies identically here.
 */
@Entity
@Table(name = "security_guard_documents")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
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

    public static GuardDocument upload(TenantId tenantId, UUID guardId, Category category,
                                       String fileUrl, String fileName, String notes,
                                       UUID uploadedBy) {
        GuardDocument d = new GuardDocument();
        d.tenantId      = tenantId;
        d.guardId       = guardId;
        d.category      = category;
        d.fileUrl       = fileUrl;
        d.fileName      = fileName;
        d.notes         = notes;
        d.uploadedBy    = uploadedBy;
        d.createdAt     = Instant.now();
        return d;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void softDelete(UUID deletedBy, String reason) {
        if (this.deletedAt != null) {
            throw new IllegalStateException("Document already deleted");
        }
        this.deletedAt    = Instant.now();
        this.deletedBy    = deletedBy;
        this.deleteReason = reason;
    }

    public boolean isDeleted() { return deletedAt != null; }

    // ── Enum ───────────────────────────────────────────────────────────────────
    //
    // Scoped to the PSIRA/POPIA/SARS documents an SA security company
    // actually needs on file per your own research -- not every category
    // listed there (uniform issue forms, leave forms, etc. are operational
    // records, not identity/compliance DOCUMENTS, and don't belong in this
    // upload list). FINGERPRINT_FORM specifically replaces the fake
    // biometric-scan simulation removed from the onboarding form -- see
    // the conversation this migration came from for why.

    public enum Category {
        ID_COPY,
        PROOF_OF_ADDRESS,
        PSIRA_CERTIFICATE,
        BANK_CONFIRMATION,
        MATRIC_CERTIFICATE,
        TRAINING_CERTIFICATE,
        FIREARM_COMPETENCY,
        FIREARM_LICENSE,
        DRIVERS_LICENSE,
        MEDICAL_CERTIFICATE,
        POLICE_CLEARANCE,
        FINGERPRINT_FORM,
        EMPLOYMENT_CONTRACT,
        POPIA_CONSENT,
        PASSPORT_PHOTO,
        OTHER
    }
}