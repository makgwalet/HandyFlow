package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** History of renewals: what the expiry was and what it became, and who did it. */
@Entity
@Table(name = "fuel_compliance_renewals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelComplianceRenewal {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "previous_expires_on", nullable = false, updatable = false)
    private LocalDate previousExpiresOn;

    @Column(name = "new_expires_on", nullable = false, updatable = false)
    private LocalDate newExpiresOn;

    @Column(name = "renewed_by", updatable = false)
    private UUID renewedBy;

    @Column(name = "renewed_at", nullable = false, updatable = false)
    private Instant renewedAt;

    @Column(length = 200, updatable = false)
    private String note;

    public static FuelComplianceRenewal of(TenantId tenantId, UUID itemId, LocalDate previous, LocalDate next, UUID by, String note) {
        FuelComplianceRenewal r = new FuelComplianceRenewal();
        r.tenantId = tenantId; r.itemId = itemId; r.previousExpiresOn = previous; r.newExpiresOn = next; r.renewedBy = by;
        r.renewedAt = Instant.now(); r.note = note == null || note.isBlank() ? null : note.trim();
        return r;
    }
}
