package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An external contractor/supplier used for work the internal technician
 * pool can't or shouldn't do itself (specialist HVAC servicing, elevator
 * certification, electrical compliance testing, etc.).
 */
@Entity
@Table(name = "facility_vendors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityVendor {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "service_type", nullable = false)
    private String serviceType; // ELECTRICAL, HVAC, PLUMBING, FIRE, ELEVATOR, GENERAL, OTHER

    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_phone")
    private String contactPhone;
    @Column(name = "contact_email")
    private String contactEmail;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * PENDING (just added, not yet cleared to receive work), APPROVED, or SUSPENDED. Vendors that existed before
     * onboarding was introduced were set to APPROVED by the migration.
     */
    @Column(name = "onboarding_status", nullable = false, length = 20)
    private String onboardingStatus = "PENDING";
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FacilityVendor create(TenantId tenantId, String companyName, String serviceType,
                                         String contactName, String contactPhone, String contactEmail,
                                         String notes) {
        FacilityVendor v = new FacilityVendor();
        v.tenantId = tenantId;
        v.companyName = companyName;
        v.serviceType = serviceType != null ? serviceType.toUpperCase() : "GENERAL";
        v.contactName = contactName;
        v.contactPhone = contactPhone;
        v.contactEmail = contactEmail;
        v.notes = notes;
        v.createdAt = Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }

    public void update(String companyName, String serviceType, String contactName,
                        String contactPhone, String contactEmail, String notes) {
        if (companyName != null) this.companyName = companyName;
        if (serviceType != null) this.serviceType = serviceType.toUpperCase();
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void recordCreator(UUID userId) { this.createdByUserId = userId; }

    /** Clear the vendor to receive work. The caller has already checked its documents. */
    public void approve(UUID approverId) {
        if ("APPROVED".equals(onboardingStatus)) throw new IllegalStateException("This vendor is already approved");
        this.onboardingStatus = "APPROVED";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        this.suspendedReason = null;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if ("SUSPENDED".equals(onboardingStatus)) throw new IllegalStateException("This vendor is already suspended");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to suspend a vendor");
        this.onboardingStatus = "SUSPENDED";
        this.suspendedReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    public boolean isApproved() { return "APPROVED".equals(onboardingStatus); }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
