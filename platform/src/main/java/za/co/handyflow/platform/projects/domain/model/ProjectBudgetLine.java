package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_budget_lines")
@Getter
@NoArgsConstructor
public class ProjectBudgetLine {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID       tenantId;
    @Column(name = "project_id", nullable = false) UUID       projectId;
    @Column(name = "phase_id")                      UUID       phaseId;
    // category: LABOUR | MATERIALS | SUBCONTRACT | EQUIPMENT | OVERHEAD | CONTINGENCY
    @Column(nullable = false, length = 50)          String     category;
    @Column(nullable = false, length = 300)         String     description;
    @Column(name = "budgeted_amount",  nullable = false) BigDecimal budgetedAmount;
    @Column(name = "committed_amount", nullable = false) BigDecimal committedAmount = BigDecimal.ZERO;
    @Column(name = "actual_amount",    nullable = false) BigDecimal actualAmount    = BigDecimal.ZERO;
    @Column(name = "is_provisional",   nullable = false) boolean   isProvisional   = false;
    @Column(name = "is_prime_cost",    nullable = false) boolean   isPrimeCost     = false;
    @Column(name = "sort_order",       nullable = false) int       sortOrder       = 0;
    @Column(name = "created_at",       nullable = false) Instant   createdAt;

    public static ProjectBudgetLine create(UUID tenantId, UUID projectId, UUID phaseId,
                                           String category, String description,
                                           BigDecimal budgetedAmount, boolean isProvisional,
                                           boolean isPrimeCost, int sortOrder) {
        ProjectBudgetLine b = new ProjectBudgetLine();
        b.id             = UUID.randomUUID();
        b.tenantId       = tenantId;
        b.projectId      = projectId;
        b.phaseId        = phaseId;
        b.category       = category;
        b.description    = description;
        b.budgetedAmount = budgetedAmount != null ? budgetedAmount : BigDecimal.ZERO;
        b.isProvisional  = isProvisional;
        b.isPrimeCost    = isPrimeCost;
        b.sortOrder      = sortOrder;
        b.createdAt      = Instant.now();
        return b;
    }

    /** Called when a PO is approved against this budget line */
    public void commitCost(BigDecimal amount) {
        this.committedAmount = this.committedAmount.add(amount);
    }

    /** Called when an expense or invoice is posted against this line */
    public void recordActual(BigDecimal amount) {
        this.actualAmount = this.actualAmount.add(amount);
    }

    public BigDecimal getVariance() {
        return budgetedAmount.subtract(actualAmount).subtract(committedAmount);
    }

    public void setBudgetedAmount(BigDecimal v) { this.budgetedAmount = v; }
    public void setDescription(String v)        { this.description = v; }
    public void setSortOrder(int v)             { this.sortOrder = v; }
}
