package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An authorised exception to a HARD_STOP budget — valid only up to the amount the person authorised. */
@Entity
@Table(name = "sc_budget_overrides")
@Getter
@NoArgsConstructor
public class ScBudgetOverride {

    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String REQUISITION = "REQUISITION";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "entity_type", nullable = false, length = 20) String entityType;
    @Column(name = "entity_id", nullable = false) UUID entityId;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal amount;
    @Column(nullable = false, length = 500) String reason;
    @Column(name = "overridden_by") UUID overriddenBy;
    @Column(name = "overridden_by_name", length = 255) String overriddenByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScBudgetOverride create(UUID tenantId, String entityType, UUID entityId, BigDecimal amount,
                                          String reason, UUID by, String byName) {
        ScBudgetOverride o = new ScBudgetOverride();
        o.id = UUID.randomUUID();
        o.tenantId = tenantId;
        o.entityType = entityType;
        o.entityId = entityId;
        o.amount = amount;
        o.reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        o.overriddenBy = by;
        o.overriddenByName = byName;
        o.createdAt = Instant.now();
        return o;
    }
}
