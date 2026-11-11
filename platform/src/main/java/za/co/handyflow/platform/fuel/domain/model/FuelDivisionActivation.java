package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A tenant's use of one division. Rows are switched on and off, never deleted, so the history of activation stays. */
@Entity
@Table(name = "fuel_division_activations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDivisionActivation {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private FuelDivision division;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "activated_at", nullable = false) private Instant activatedAt = Instant.now();
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "deactivated_at") private Instant deactivatedAt;
    @Column(name = "deactivated_by") private UUID deactivatedBy;
    @Column(name = "deactivation_reason", length = 300) private String deactivationReason;
    @Version private Long version;

    public static FuelDivisionActivation of(TenantId tenantId, FuelDivision division, UUID by) {
        FuelDivisionActivation a = new FuelDivisionActivation();
        a.tenantId = tenantId; a.division = division; a.activatedBy = by;
        return a;
    }

    public void activate(UUID by) {
        this.active = true; this.activatedAt = Instant.now(); this.activatedBy = by;
        this.deactivatedAt = null; this.deactivatedBy = null; this.deactivationReason = null;
    }

    public void deactivate(UUID by, String reason) {
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason for deactivating this division (at least 5 characters).");
        this.active = false; this.deactivatedAt = Instant.now(); this.deactivatedBy = by; this.deactivationReason = reason.trim();
    }
}
