package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One cost allocation of a change order: adjusts an existing budget line (budgetLineId set,
 * amount may be negative) or creates a new one on approval (budgetLineId null, amount positive).
 */
@Entity
@Table(name = "change_order_cost_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeOrderCostLine {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)        private UUID tenantId;
    @Column(name = "change_order_id", nullable = false)  private UUID changeOrderId;
    @Column(name = "budget_line_id")                      private UUID budgetLineId;
    @Column(name = "category", length = 50)               private String category;
    @Column(name = "description", length = 300)           private String description;
    @Column(name = "amount", nullable = false)            private BigDecimal amount;
    @Column(name = "created_at", nullable = false)        private Instant createdAt;

    public static ChangeOrderCostLine create(UUID tenantId, UUID changeOrderId, UUID budgetLineId,
                                             String category, String description, BigDecimal amount) {
        ChangeOrderCostLine l = new ChangeOrderCostLine();
        l.id            = UUID.randomUUID();
        l.tenantId      = tenantId;
        l.changeOrderId = changeOrderId;
        l.budgetLineId  = budgetLineId;
        l.category      = category;
        l.description   = description;
        l.amount        = amount;
        l.createdAt     = Instant.now();
        return l;
    }
}