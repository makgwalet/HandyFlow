package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A trade licence or ticket a technician holds (wireman's licence, gas installer, plumbing, working at heights...).
 * Everything except OTHER needs an expiry date; a revoked licence no longer counts. A scan or photo of the licence
 * is attached as evidence against this record.
 */
@Entity
@Table(name = "facility_technician_licences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityTechnicianLicence {

    public static final List<String> TYPES = List.of("ELECTRICAL_WIREMAN", "GAS_INSTALLER", "PLUMBING", "HVAC_REFRIGERATION",
            "FIRE_EQUIPMENT", "WORKING_AT_HEIGHTS", "FIRST_AID", "DRIVERS_LICENCE", "OTHER");
    private static final Set<String> EXPIRY_OPTIONAL = Set.of("OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "licence_type", nullable = false, length = 40)
    private String licenceType;

    @Column(length = 120)
    private String reference;

    @Column(name = "issued_by", length = 200)
    private String issuedBy;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

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

    public static FacilityTechnicianLicence create(TenantId tenantId, UUID technicianId, String licenceType, String reference,
                                                   String issuedBy, LocalDate issueDate, LocalDate expiryDate) {
        String type = licenceType == null ? "" : licenceType.trim().toUpperCase();
        if (!TYPES.contains(type)) throw new IllegalArgumentException("licenceType must be one of " + TYPES);
        if (expiryDate == null && !EXPIRY_OPTIONAL.contains(type))
            throw new IllegalArgumentException("An expiry date is required for " + type);
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate))
            throw new IllegalArgumentException("expiryDate cannot be before issueDate");
        FacilityTechnicianLicence l = new FacilityTechnicianLicence();
        l.tenantId = tenantId;
        l.technicianId = technicianId;
        l.licenceType = type;
        l.reference = blankToNull(reference);
        l.issuedBy = blankToNull(issuedBy);
        l.issueDate = issueDate;
        l.expiryDate = expiryDate;
        l.createdAt = Instant.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public void revoke(String reason) {
        if ("REVOKED".equals(status)) throw new IllegalStateException("This licence is already revoked");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to revoke a licence");
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
