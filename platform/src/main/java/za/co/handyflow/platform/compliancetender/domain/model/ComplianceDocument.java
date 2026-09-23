package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deliberately a thin wrapper referencing
 * evidence.application.EvidenceFacade — confirmed already the reusable,
 * source-module-tagged document-vault engine (already proven with three
 * independent consumers: Expenses, Recruitment Agency, Payroll Bureau).
 * {@code evidenceId} is what {@code EvidenceFacade.attach(...)} returned;
 * no file bytes or storage path live on this entity — that stays
 * evidence's responsibility. This entity only carries what's specific to
 * a COMPLIANCE document: which registration it supports (if any), its own
 * issue/expiry dates (which may differ from the registration's own — a
 * supporting ID document expires on its own schedule), and verification
 * status.
 */
@Entity
@Table(name = "compliance_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "registration_id")
    private UUID registrationId; // nullable — some documents aren't tied to one specific registration

    @Column(name = "document_type", nullable = false)
    private String documentType; // e.g. "Director ID", "Tax Clearance Certificate", "cidb Certificate"

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId; // references evidence.domain.model.Evidence — not a DB FK, evidence lives in another module's table

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static ComplianceDocument create(TenantId tenantId, UUID registrationId, String documentType,
                                             UUID evidenceId, LocalDate issueDate, LocalDate expiryDate,
                                             UUID createdBy) {
        if (evidenceId == null)
            throw new IllegalArgumentException("evidenceId is required — attach the file via EvidenceFacade first");
        ComplianceDocument d = new ComplianceDocument();
        d.tenantId = tenantId;
        d.registrationId = registrationId;
        d.documentType = documentType;
        d.evidenceId = evidenceId;
        d.issueDate = issueDate;
        d.expiryDate = expiryDate;
        d.createdAt = Instant.now();
        d.createdBy = createdBy;
        d.updatedAt = Instant.now();
        d.updatedBy = createdBy;
        return d;
    }

    /**
     * Extracted data (OCR/AI) must be reviewable before being treated as
     * authoritative — the source material's own explicit rule for document
     * intelligence. This method is the human-review step: nobody's data is
     * "verified" just because a document was uploaded.
     */
    public void verify(UUID verifiedByUserId) {
        this.verifiedBy = verifiedByUserId;
        this.verifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isVerified() { return verifiedAt != null; }

    public boolean isExpired() { return expiryDate != null && expiryDate.isBefore(LocalDate.now()); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
