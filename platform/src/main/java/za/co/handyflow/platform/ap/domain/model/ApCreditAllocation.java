package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Part of a supplier credit note applied to a bill (finance patch F10). */
@Entity
@Table(name = "ap_credit_allocations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApCreditAllocation {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "credit_note_id", nullable = false) private UUID creditNoteId;
    @Column(name = "bill_id", nullable = false) private UUID billId;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "allocated_by") private UUID allocatedBy;
    @Column(name = "allocated_at") private Instant allocatedAt = Instant.now();

    public static ApCreditAllocation of(TenantId tenantId, UUID creditNoteId, UUID billId, BigDecimal amount, UUID by) {
        ApCreditAllocation a = new ApCreditAllocation();
        a.tenantId = tenantId;
        a.creditNoteId = creditNoteId;
        a.billId = billId;
        a.amount = amount;
        a.allocatedBy = by;
        return a;
    }
}
