package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A person's link between a receipt in a forecourt tank and the dispatch it came from. A receipt belongs to at most one dispatch. */
@Entity
@Table(name = "fuel_dispatch_receipt_matches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDispatchReceiptMatch {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "dispatch_id", nullable = false, updatable = false)
    private UUID dispatchId;

    @Column(name = "receipt_id", nullable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "matched_by", updatable = false)
    private UUID matchedBy;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private Instant matchedAt = Instant.now();

    public static FuelDispatchReceiptMatch of(TenantId tenantId, UUID dispatchId, UUID receiptId, UUID by) {
        FuelDispatchReceiptMatch m = new FuelDispatchReceiptMatch();
        m.tenantId = tenantId; m.dispatchId = dispatchId; m.receiptId = receiptId; m.matchedBy = by;
        return m;
    }
}
