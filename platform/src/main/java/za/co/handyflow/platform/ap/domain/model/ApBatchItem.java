package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ap_batch_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApBatchItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    public static ApBatchItem of(UUID batchId, UUID billId, BigDecimal amount) {
        ApBatchItem item = new ApBatchItem();
        item.batchId = batchId;
        item.billId  = billId;
        item.amount  = amount;
        return item;
    }
}
