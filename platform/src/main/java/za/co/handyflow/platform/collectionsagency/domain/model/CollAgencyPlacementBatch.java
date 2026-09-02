package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One "handover" event — a creditor client places a batch of debtor
 * accounts with the agency in one go (typically a bulk import of debtor
 * details, outstanding amounts, and supporting documents). This is the
 * core operational loop the user's own domain analysis called out:
 * placement -> acknowledgment -> collection -> reporting. This entity is
 * the discrete record of the placement/acknowledgment step, distinct
 * from the individual CollAgencyDebtorAccount rows it creates — a client
 * uploading a spreadsheet of 50 accounts is one batch, not 50
 * independent placement events.
 * <p>
 * totalAccounts/totalPlacedValue are snapshots captured at creation
 * (from what was actually placed), not live-recomputed from the debtor
 * accounts — a batch is a historical record of what was handed over, and
 * should still show the original numbers even if a debtor account is
 * later returned to the client or written off.
 */
@Entity
@Table(name = "collagency_placement_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyPlacementBatch {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "batch_reference")
    private String batchReference; // client's own reference for this handover, if they supplied one

    @Column(name = "placed_date", nullable = false)
    private LocalDate placedDate;

    @Column(name = "total_accounts", nullable = false)
    private int totalAccounts;

    @Column(name = "total_placed_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPlacedValue;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CollAgencyPlacementBatch create(UUID tenantId, UUID clientId, String batchReference,
                                                   LocalDate placedDate, int totalAccounts,
                                                   BigDecimal totalPlacedValue, String notes) {
        CollAgencyPlacementBatch b = new CollAgencyPlacementBatch();
        b.tenantId = tenantId;
        b.clientId = clientId;
        b.batchReference = batchReference;
        b.placedDate = placedDate != null ? placedDate : LocalDate.now();
        b.totalAccounts = totalAccounts;
        b.totalPlacedValue = totalPlacedValue;
        b.notes = notes;
        b.createdAt = Instant.now();
        return b;
    }

    /** Confirms the agency has received and begun processing this batch. */
    public void acknowledge(UUID acknowledgedBy) {
        if (this.acknowledgedAt != null) {
            throw new IllegalStateException("This batch has already been acknowledged");
        }
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = acknowledgedBy;
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }
}
