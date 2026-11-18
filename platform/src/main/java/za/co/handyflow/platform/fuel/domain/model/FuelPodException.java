package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An authorised reason a delivery was completed without a named receiver. APPROVED waits for completion; USED was applied. */
@Entity
@Table(name = "fuel_pod_exceptions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPodException {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "exception_type", nullable = false, updatable = false, length = 30)
    private String exceptionType;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Approved ahead of completion by someone holding the approve permission. */
    public static FuelPodException approved(TenantId t, UUID deliveryId, String type, String reason, UUID approver, Instant at) {
        FuelPodException e = new FuelPodException();
        e.tenantId = t; e.deliveryId = deliveryId; e.exceptionType = type; e.reason = reason; e.status = "APPROVED";
        e.requestedBy = approver; e.approvedBy = approver; e.approvedAt = at; e.createdAt = at;
        return e;
    }

    /** Applied at completion on the strength of the site's own designation. */
    public static FuelPodException usedBySiteRule(TenantId t, UUID deliveryId, String type, String reason, UUID by, int evidence, Instant at) {
        FuelPodException e = new FuelPodException();
        e.tenantId = t; e.deliveryId = deliveryId; e.exceptionType = type; e.reason = reason; e.status = "USED";
        e.requestedBy = by; e.usedAt = at; e.evidenceCount = evidence; e.createdAt = at;
        return e;
    }

    public void markUsed(int evidence, Instant at) { this.status = "USED"; this.usedAt = at; this.evidenceCount = evidence; }
}
