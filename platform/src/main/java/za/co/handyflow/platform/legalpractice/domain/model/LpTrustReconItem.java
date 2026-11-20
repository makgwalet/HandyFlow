package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** A reason the bank statement and the ledger differ: a deposit the bank has not yet credited, or a payment it has not yet debited. */
@Entity
@Table(name = "lp_trust_recon_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTrustReconItem {

    public static final Set<String> KINDS = Set.of("OUTSTANDING_DEPOSIT", "OUTSTANDING_PAYMENT");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "reconciliation_id", nullable = false)
    private UUID reconciliationId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "item_date", nullable = false)
    private LocalDate itemDate;

    @Column(nullable = false)
    private String description;

    @Column(length = 100)
    private String reference;

    /** The ledger row this item explains, when there is one. */
    @Column(name = "trust_transaction_id")
    private UUID trustTransactionId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LpTrustReconItem create(TenantId tenantId, UUID reconciliationId, String kind, BigDecimal amount, LocalDate itemDate,
                                          String description, String reference, UUID trustTransactionId, UUID by) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("The kind must be OUTSTANDING_DEPOSIT or OUTSTANDING_PAYMENT");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("The amount must be more than nil");
        if (description == null || description.trim().length() < 3) throw new IllegalArgumentException("Describe the item (at least 3 characters)");
        LpTrustReconItem i = new LpTrustReconItem();
        i.tenantId = tenantId;
        i.reconciliationId = reconciliationId;
        i.kind = kind;
        i.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        i.itemDate = itemDate;
        i.description = description.trim();
        i.reference = reference == null || reference.isBlank() ? null : reference.trim();
        i.trustTransactionId = trustTransactionId;
        i.createdBy = by;
        i.createdAt = Instant.now();
        return i;
    }
}
