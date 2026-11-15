package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A driver's standing at one delivery site: approved (inducted), restricted to stated conditions, or suspended. The induction and training dates
 * are the site's own; the system only compares them with today and never decides what a site should require.
 */
@Entity
@Table(name = "fuel_site_driver_approvals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSiteDriverApproval {

    public static final String APPROVED = "APPROVED", RESTRICTED = "RESTRICTED", SUSPENDED = "SUSPENDED";
    public static final Set<String> STATUSES = Set.of(APPROVED, RESTRICTED, SUSPENDED);

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 12)
    private String status = APPROVED;

    @Column(name = "induction_date")
    private LocalDate inductionDate;

    @Column(name = "induction_expiry")
    private LocalDate inductionExpiry;

    @Column(name = "training_ref", length = 120)
    private String trainingRef;

    @Column(name = "training_expiry")
    private LocalDate trainingExpiry;

    /** The site's access conditions for an approved driver; the restriction for a restricted one; the reason for a suspended one. */
    @Column(length = 500)
    private String conditions;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelSiteDriverApproval forDriver(TenantId tenantId, UUID siteId, UUID employeeId) {
        FuelSiteDriverApproval a = new FuelSiteDriverApproval();
        a.tenantId = tenantId; a.siteId = siteId; a.employeeId = employeeId;
        return a;
    }

    public void update(String status, LocalDate inductionDate, LocalDate inductionExpiry, String trainingRef, LocalDate trainingExpiry, String conditions, UUID by) {
        if (status == null || !STATUSES.contains(status)) throw new IllegalArgumentException("The status must be APPROVED, RESTRICTED or SUSPENDED.");
        boolean inducted = !SUSPENDED.equals(status);
        if (inducted && inductionDate == null) throw new IllegalArgumentException("Give the date the driver was inducted at this site.");
        if (inductionDate != null && inductionExpiry != null && inductionExpiry.isBefore(inductionDate)) throw new IllegalArgumentException("The induction cannot expire before it was done.");
        String cond = conditions == null || conditions.isBlank() ? null : (conditions.trim().length() > 500 ? conditions.trim().substring(0, 500) : conditions.trim());
        if (RESTRICTED.equals(status) && cond == null) throw new IllegalArgumentException("Say what the driver is restricted to.");
        if (SUSPENDED.equals(status) && cond == null) throw new IllegalArgumentException("Say why the driver is suspended at this site.");
        this.status = status; this.inductionDate = inductionDate; this.inductionExpiry = inductionExpiry;
        this.trainingRef = trainingRef == null || trainingRef.isBlank() ? null : (trainingRef.trim().length() > 120 ? trainingRef.trim().substring(0, 120) : trainingRef.trim());
        this.trainingExpiry = trainingExpiry; this.conditions = cond; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}
