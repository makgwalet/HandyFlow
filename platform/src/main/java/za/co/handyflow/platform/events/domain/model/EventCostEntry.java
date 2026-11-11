package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An invoice received (INVOICE — the cost is incurred) or a payment made
 * (PAYMENT — cash out, optionally a deposit). Never deleted: a mistaken entry
 * is VOIDED with a reason so the history stays intact.
 * <p>
 * This is the event's own tracking record, not the accounting ledger. When
 * sourceModule + sourceRef are set it points at the authoritative record
 * (an AP bill, an expense claim, a bank payment); when they are not, the
 * entry is "unreconciled" and reported as such.
 */
@Entity
@Table(name = "event_cost_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCostEntry {

    public static final String INVOICE = "INVOICE";
    public static final String PAYMENT = "PAYMENT";
    public static final String ACTIVE = "ACTIVE";
    public static final String VOIDED = "VOIDED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "event_id", nullable = false)        private UUID eventId;
    @Column(name = "budget_line_id", nullable = false)  private UUID budgetLineId;
    @Column(name = "commitment_id")                     private UUID commitmentId;
    @Column(name = "kind", nullable = false)            private String kind;
    @Column(name = "is_deposit", nullable = false)      private boolean deposit;
    @Column(name = "amount", nullable = false)          private BigDecimal amount;
    @Column(name = "entry_date", nullable = false)      private LocalDate entryDate;
    @Column(name = "reference")                         private String reference;
    @Column(name = "description")                       private String description;
    @Column(name = "source_module")                     private String sourceModule;
    @Column(name = "source_ref")                        private String sourceRef;
    @Column(name = "status", nullable = false)          private String status = ACTIVE;
    @Column(name = "void_reason")                       private String voidReason;
    @Column(name = "voided_by_name")                    private String voidedByName;
    @Column(name = "voided_at")                         private Instant voidedAt;
    @Column(name = "recorded_by")                       private UUID recordedBy;
    @Column(name = "recorded_by_name")                  private String recordedByName;
    @Column(name = "created_at", nullable = false)      private Instant createdAt;

    public static EventCostEntry create(TenantId tenantId, UUID eventId, UUID budgetLineId, UUID commitmentId,
                                        String kind, boolean deposit, BigDecimal amount, LocalDate entryDate,
                                        String reference, String description, String sourceModule,
                                        String sourceRef, UUID recordedBy, String recordedByName) {
        if (!INVOICE.equals(kind) && !PAYMENT.equals(kind))
            throw new IllegalArgumentException("Kind must be INVOICE or PAYMENT");
        if (deposit && !PAYMENT.equals(kind))
            throw new IllegalArgumentException("Only a PAYMENT can be marked as a deposit");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");
        if (entryDate == null)
            throw new IllegalArgumentException("Entry date is required");
        if ((sourceModule == null) != (sourceRef == null))
            throw new IllegalArgumentException("sourceModule and sourceRef must be given together");

        EventCostEntry e = new EventCostEntry();
        e.id             = UUID.randomUUID();
        e.tenantId       = tenantId.getValue();
        e.eventId        = eventId;
        e.budgetLineId   = budgetLineId;
        e.commitmentId   = commitmentId;
        e.kind           = kind;
        e.deposit        = deposit;
        e.amount         = amount.setScale(2, RoundingMode.HALF_UP);
        e.entryDate      = entryDate;
        e.reference      = reference;
        e.description    = description;
        e.sourceModule   = sourceModule;
        e.sourceRef      = sourceRef;
        e.status         = ACTIVE;
        e.recordedBy     = recordedBy;
        e.recordedByName = recordedByName;
        e.createdAt      = Instant.now();
        return e;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    /** True when the entry points at the authoritative accounting record. */
    public boolean isReconciled() {
        return sourceRef != null;
    }

    public void voidEntry(String reason, String byName) {
        if (!isActive())
            throw new IllegalStateException("This entry has already been voided");
        this.status       = VOIDED;
        this.voidReason   = reason;
        this.voidedByName = byName;
        this.voidedAt     = Instant.now();
    }
}