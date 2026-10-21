package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One deal with a company. A company can have many, each with its own stage, amount and close date. */
@Entity
@Table(name = "crm_opportunities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Opportunity {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private OpportunityStage stage;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lost_reason", length = 30)
    private LostReason lostReason;

    @Column(name = "lost_note", length = 500)
    private String lostNote;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Opportunity create(TenantId tenantId, UUID customerId, String name, BigDecimal amount, LocalDate closeDate,
                                     UUID ownerId, String notes, UUID by) {
        var o = new Opportunity();
        o.tenantId = tenantId;
        o.customerId = customerId;
        o.name = name;
        o.stage = OpportunityStage.PROSPECTING;
        o.amount = amount;
        o.expectedCloseDate = closeDate;
        o.ownerId = ownerId;
        o.notes = notes;
        o.createdBy = by;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public void changeDetails(String name, BigDecimal amount, LocalDate closeDate, UUID ownerId, String notes) {
        this.name = name;
        this.amount = amount;
        this.expectedCloseDate = closeDate;
        this.ownerId = ownerId;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    /** The caller has already checked the move with OpportunityRules. */
    public void moveTo(OpportunityStage to, LostReason reason, String note) {
        this.stage = to;
        this.lostReason = to == OpportunityStage.LOST ? reason : null;
        this.lostNote = to == OpportunityStage.LOST ? note : null;
        this.closedAt = to.isOpen() ? null : Instant.now();
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }
}
