package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The client-scoped counterpart to {@code compliancetender.ComplianceDocument}
 * — same reasoning as {@code ClientComplianceRegistration}'s own Javadoc
 * for why this is a parallel entity/table, not a client dimension bolted
 * onto {@code compliancetender}'s own table. Deliberately still a thin
 * wrapper over {@code EvidenceFacade} — {@code evidenceId} is what
 * {@code EvidenceFacade.attach(...)} returned, no file bytes live here,
 * exactly the same division of responsibility
 * {@code compliancetender.ComplianceDocument} already established. The
 * document's own id is generated up front by the service, before evidence
 * is attached, for the same reason: so the attach call can be tagged with
 * this document's own id as {@code relatedEntityId}.
 */
@Entity
@Table(name = "client_compliance_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComplianceDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "registration_id")
    private UUID registrationId; // nullable — references ClientComplianceRegistration

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Set only when the uploader verified their own document; null when someone else did. */
    @Column(name = "verification_exception_reason")
    private String verificationExceptionReason;

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

    public static ClientComplianceDocument create(UUID id, TenantId tenantId, UUID clientId, UUID registrationId,
                                                   String documentType, UUID evidenceId, LocalDate issueDate,
                                                   LocalDate expiryDate, UUID createdBy) {
        if (evidenceId == null)
            throw new IllegalArgumentException("evidenceId is required — attach the file via EvidenceFacade first");
        ClientComplianceDocument d = new ClientComplianceDocument();
        if (id != null) d.id = id;
        d.tenantId = tenantId;
        d.clientId = clientId;
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

    public static final int MIN_EXCEPTION_REASON = 15;

    /**
     * Separation of duties: the person who uploaded a document does not verify it, unless they say why no one else can (recorded, at least
     * {@value #MIN_EXCEPTION_REASON} characters). A document is verified once; replace it rather than re-verify it.
     */
    public void verify(UUID verifiedByUserId, String exceptionReason) {
        if (verifiedByUserId == null) throw new IllegalArgumentException("Who is verifying this document?");
        if (isVerified()) throw new IllegalStateException("This document is already verified");
        boolean self = verifiedByUserId.equals(createdBy);
        String reason = exceptionReason == null ? "" : exceptionReason.trim();
        if (self) {
            if (reason.length() < MIN_EXCEPTION_REASON)
                throw new IllegalArgumentException("You uploaded this document, so someone else should verify it. If no one else can, give a reason of at least "
                        + MIN_EXCEPTION_REASON + " characters.");
            this.verificationExceptionReason = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        } else {
            this.verificationExceptionReason = null;
        }
        this.verifiedBy = verifiedByUserId;
        this.verifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isVerified() { return verifiedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
