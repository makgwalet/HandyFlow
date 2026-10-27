package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A compliance document a vendor must keep current: public liability insurance, COIDA letter of good standing,
 * tax clearance, a trade licence, a B-BBEE certificate, or anything else. Everything except OTHER needs an
 * expiry date; a revoked document no longer counts.
 */
@Entity
@Table(name = "facility_vendor_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityVendorDocument {

    public static final List<String> TYPES = List.of("PUBLIC_LIABILITY_INSURANCE", "COIDA_LETTER", "TAX_CLEARANCE",
            "TRADE_LICENCE", "BEE_CERTIFICATE", "OTHER");
    private static final Set<String> EXPIRY_OPTIONAL = Set.of("OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "doc_type", nullable = false, length = 40)
    private String docType;

    @Column(length = 120)
    private String reference;

    @Column(name = "issued_by", length = 200)
    private String issuedBy;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Insurance cover limit, when relevant. */
    @Column(name = "cover_amount", precision = 15, scale = 2)
    private BigDecimal coverAmount;

    @Column(name = "document_ref", length = 500)
    private String documentRef;

    /** VALID or REVOKED. */
    @Column(nullable = false, length = 20)
    private String status = "VALID";
    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FacilityVendorDocument create(TenantId tenantId, UUID vendorId, String docType, String reference,
                                                String issuedBy, LocalDate issueDate, LocalDate expiryDate,
                                                BigDecimal coverAmount, String documentRef) {
        String type = docType == null ? "" : docType.trim().toUpperCase();
        if (!TYPES.contains(type)) throw new IllegalArgumentException("docType must be one of " + TYPES);
        if (expiryDate == null && !EXPIRY_OPTIONAL.contains(type))
            throw new IllegalArgumentException("An expiry date is required for " + type);
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate))
            throw new IllegalArgumentException("expiryDate cannot be before issueDate");
        if (coverAmount != null && coverAmount.signum() < 0)
            throw new IllegalArgumentException("coverAmount must not be negative");
        FacilityVendorDocument d = new FacilityVendorDocument();
        d.tenantId = tenantId;
        d.vendorId = vendorId;
        d.docType = type;
        d.reference = blankToNull(reference);
        d.issuedBy = blankToNull(issuedBy);
        d.issueDate = issueDate;
        d.expiryDate = expiryDate;
        d.coverAmount = coverAmount;
        d.documentRef = blankToNull(documentRef);
        d.createdAt = Instant.now();
        d.updatedAt = d.createdAt;
        return d;
    }

    public void revoke(String reason) {
        if ("REVOKED".equals(status)) throw new IllegalStateException("This document is already revoked");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to revoke a document");
        this.status = "REVOKED";
        this.revokedReason = reason.trim();
        this.revokedAt = Instant.now();
        this.updatedAt = this.revokedAt;
    }

    /** In force on {@code today}: not revoked, and either no expiry (OTHER) or expiring on/after today. */
    public boolean isInForce(LocalDate today) {
        return "VALID".equals(status) && (expiryDate == null || !expiryDate.isBefore(today));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
