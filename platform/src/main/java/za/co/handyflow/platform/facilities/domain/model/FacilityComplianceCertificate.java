package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A regulatory compliance certificate for a site or asset — electrical
 * Certificate of Compliance (COC), fire equipment service certificate,
 * elevator/lift certificate, gas compliance certificate, etc. A real SA
 * SME compliance need with genuine legal consequences if it lapses
 * (insurance claims can be repudiated over an expired electrical COC,
 * for example) — the reason this gets its own tracked entity rather than
 * being folded into {@link FacilityAsset} as a couple of date fields.
 */
@Entity
@Table(name = "facility_compliance_certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityComplianceCertificate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "asset_id")
    private UUID assetId; // nullable — some certificates (e.g. electrical COC) apply to the whole site

    @Column(name = "certificate_type", nullable = false)
    private String certificateType; // ELECTRICAL_COC, FIRE_EQUIPMENT, ELEVATOR, GAS, OTHER

    @Column(name = "certificate_number")
    private String certificateNumber;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "document_ref")
    private String documentRef;

    @Column(nullable = false)
    private String status = "VALID"; // VALID, EXPIRED, REVOKED

    @Column(name = "revoked_reason")
    private String revokedReason;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FacilityComplianceCertificate create(TenantId tenantId, UUID siteId, UUID assetId,
                                                        String certificateType, String certificateNumber,
                                                        String issuedBy, LocalDate issueDate,
                                                        LocalDate expiryDate, String documentRef) {
        if (expiryDate.isBefore(issueDate))
            throw new IllegalArgumentException("expiryDate cannot be before issueDate");
        FacilityComplianceCertificate c = new FacilityComplianceCertificate();
        c.tenantId = tenantId;
        c.siteId = siteId;
        c.assetId = assetId;
        c.certificateType = certificateType != null ? certificateType.toUpperCase() : "OTHER";
        c.certificateNumber = certificateNumber;
        c.issuedBy = issuedBy;
        c.issueDate = issueDate;
        c.expiryDate = expiryDate;
        c.documentRef = documentRef;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void revoke(String reason) {
        if ("REVOKED".equals(status))
            throw new IllegalStateException("Certificate is already revoked");
        this.status = "REVOKED";
        this.revokedReason = reason;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Called by the daily notification sweep once a VALID certificate's expiry date has passed. */
    public void markExpired() {
        if ("VALID".equals(status)) {
            this.status = "EXPIRED";
            this.updatedAt = Instant.now();
        }
    }

    public boolean isExpired() { return "EXPIRED".equals(status) || (!"REVOKED".equals(status) && expiryDate.isBefore(LocalDate.now())); }

    public boolean isExpiringWithin(int days) {
        if (!"VALID".equals(status)) return false;
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return !expiryDate.isBefore(LocalDate.now()) && !expiryDate.isAfter(cutoff);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
