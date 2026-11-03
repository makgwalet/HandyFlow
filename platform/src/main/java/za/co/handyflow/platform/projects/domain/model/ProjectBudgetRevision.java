package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.BudgetRevisionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only history of every change to a budget line's budgeted amount. */
@Entity
@Table(name = "project_budget_revisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectBudgetRevision {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "budget_line_id", nullable = false)
    private UUID budgetLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type", nullable = false)
    private BudgetRevisionType revisionType;

    @Column(name = "old_amount", nullable = false)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false)
    private BigDecimal newAmount;

    @Column(name = "reason")
    private String reason;

    @Column(name = "transfer_id")
    private UUID transferId;

    /** CHUNK 5: the change order that caused this revision, when there is one. */
    @Column(name = "change_order_id")
    private UUID changeOrderId;

    @Column(name = "revised_by")
    private UUID revisedBy;

    @Column(name = "revised_by_name")
    private String revisedByName;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    public static ProjectBudgetRevision record(UUID tenantId, UUID projectId, UUID budgetLineId,
                                               BudgetRevisionType type, BigDecimal oldAmount, BigDecimal newAmount,
                                               String reason, UUID transferId, UUID by, String byName) {
        ProjectBudgetRevision r = new ProjectBudgetRevision();
        r.id            = UUID.randomUUID();
        r.tenantId      = tenantId;
        r.projectId     = projectId;
        r.budgetLineId  = budgetLineId;
        r.revisionType  = type;
        r.oldAmount     = oldAmount;
        r.newAmount     = newAmount;
        r.reason        = reason;
        r.transferId    = transferId;
        r.revisedBy     = by;
        r.revisedByName = byName;
        r.revisedAt     = Instant.now();
        return r;
    }

    /** CHUNK 5: a revision caused by an approved change order. */
    public static ProjectBudgetRevision recordForChangeOrder(UUID tenantId, UUID projectId, UUID budgetLineId,
                                                             BudgetRevisionType type, BigDecimal oldAmount,
                                                             BigDecimal newAmount, String reason,
                                                             UUID changeOrderId, UUID by, String byName) {
        ProjectBudgetRevision r = record(tenantId, projectId, budgetLineId, type, oldAmount, newAmount,
                reason, null, by, byName);
        r.changeOrderId = changeOrderId;
        return r;
    }
}