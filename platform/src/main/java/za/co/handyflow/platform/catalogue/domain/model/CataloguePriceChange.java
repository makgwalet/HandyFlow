package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One change of an item's price on one price list: the history line and, while open, the workflow item. */
@Getter
@Entity
@Table(name = "catalogue_price_changes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CataloguePriceChange {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "price_list_id", nullable = false, updatable = false)
    private UUID priceListId;

    @Column(name = "old_price", precision = 15, scale = 2, updatable = false)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal newPrice;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(length = 255, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PriceChangeStatus status;

    @Column(name = "proposed_by", updatable = false)
    private UUID proposedBy;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Version
    private Long version;

    public static CataloguePriceChange propose(TenantId tenantId, UUID itemId, UUID priceListId, BigDecimal oldPrice,
                                               BigDecimal newPrice, LocalDate effectiveFrom, String reason, UUID proposedBy) {
        CataloguePriceChange c = new CataloguePriceChange();
        c.tenantId = tenantId;
        c.itemId = itemId;
        c.priceListId = priceListId;
        c.oldPrice = oldPrice;
        c.newPrice = newPrice;
        c.effectiveFrom = effectiveFrom;
        c.reason = reason;
        c.proposedBy = proposedBy;
        c.proposedAt = Instant.now();
        c.status = PriceChangeStatus.PENDING;
        return c;
    }

    /** Decided as approved (or needing no approval): in force now if due, otherwise scheduled. */
    public void approve(UUID by, String note, LocalDate today) {
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
        if (effectiveFrom.isAfter(today)) this.status = PriceChangeStatus.SCHEDULED;
        else markApplied();
    }

    public void reject(UUID by, String note) {
        this.status = PriceChangeStatus.REJECTED;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }

    public void cancel(UUID by, String note) {
        this.status = PriceChangeStatus.CANCELLED;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }

    /** A scheduled change whose date has come. */
    public void markApplied() {
        this.status = PriceChangeStatus.APPLIED;
        this.appliedAt = Instant.now();
    }
}
