package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of a project budget.
 *
 * CHUNK 4 CHANGES
 * ───────────────
 * - committedAmount / actualAmount are now a CACHE of the cost ledger
 *   (project_cost_entries). They are written only by applyLedgerTotals(), which
 *   ProjectCostService calls after every ledger change. The old commitCost() and
 *   recordActual() methods are REMOVED: they changed the numbers with no record of
 *   why, and would have been overwritten by the next recalculation anyway.
 * - originalAmount: the approved baseline budget for this line, captured when the
 *   project baseline locks. NULL means the line was added AFTER the baseline (a
 *   variation), not part of the original approved scope.
 * - forecastToComplete: an optional manual estimate of ADDITIONAL cost still to come
 *   beyond actual + committed. NULL means "use whatever budget remains".
 */
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
    @Column(name = "original_amount")      BigDecimal originalAmount;
    @Column(name = "forecast_to_complete") BigDecimal forecastToComplete;
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

    /** Overwrites the cached totals from the ledger. The ONLY way committed/actual change. */
    public void applyLedgerTotals(BigDecimal committed, BigDecimal actual) {
        this.committedAmount = committed != null ? committed : BigDecimal.ZERO;
        this.actualAmount    = actual    != null ? actual    : BigDecimal.ZERO;
    }

    /** Captures the current budget as the approved original (only if none captured yet). */
    public boolean captureOriginal() {
        if (originalAmount != null) return false;
        this.originalAmount = budgetedAmount;
        return true;
    }

    public BigDecimal getVariance() {
        return budgetedAmount.subtract(actualAmount).subtract(committedAmount);
    }

    public void setBudgetedAmount(BigDecimal v)        { this.budgetedAmount = v; }
    public void setDescription(String v)               { this.description = v; }
    public void setSortOrder(int v)                    { this.sortOrder = v; }
    public void setForecastToComplete(BigDecimal v)    { this.forecastToComplete = v; }
}