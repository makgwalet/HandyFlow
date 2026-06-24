package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;
import za.co.handyflow.platform.projects.domain.repository.ProjectBudgetLineRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.projects.dto.CreateBudgetLineRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final ProjectBudgetLineRepository budgetRepo;
    private final ProjectRepository           projectRepo;

    @Transactional(readOnly = true)
    public List<ProjectBudgetLine> getBudgetLines(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return budgetRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectBudgetLine createBudgetLine(TenantId tenantId, UUID projectId,
                                              CreateBudgetLineRequest req) {
        verifyProject(tenantId, projectId);
        int nextOrder = budgetRepo.findMaxSortOrder(projectId) + 1;
        ProjectBudgetLine line = ProjectBudgetLine.create(
                tenantId.getValue(), projectId, req.phaseId(),
                req.category(), req.description(), req.budgetedAmount(),
                req.isProvisional(), req.isPrimeCost(), nextOrder);
        line = budgetRepo.save(line);

        // Roll up budget total on project
        refreshProjectBudget(tenantId, projectId);
        return line;
    }

    @Transactional
    public ProjectBudgetLine updateBudgetLine(TenantId tenantId, UUID lineId,
                                              BigDecimal budgetedAmount, String description) {
        ProjectBudgetLine line = budgetRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> notFound("Budget line"));
        if (budgetedAmount != null) line.setBudgetedAmount(budgetedAmount);
        if (description   != null) line.setDescription(description);
        line = budgetRepo.save(line);
        refreshProjectBudget(tenantId, line.getProjectId());
        return line;
    }

    @Transactional
    public void deleteBudgetLine(TenantId tenantId, UUID lineId) {
        ProjectBudgetLine line = budgetRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> notFound("Budget line"));
        UUID projectId = line.getProjectId();
        budgetRepo.delete(line);
        refreshProjectBudget(tenantId, projectId);
    }

    /**
     * Earned Value Management — SPI, CPI, EAC, ETC.
     * planPct: how far through the schedule we are (0–100), passed from the controller
     *          based on (today - startDate) / (endDate - startDate)
     * earnedPct: weighted completion % of all tasks (passed from ProjectService)
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getEvm(TenantId tenantId, UUID projectId,
                                        BigDecimal planPct, BigDecimal earnedPct) {
        Project p = verifyProject(tenantId, projectId);

        BigDecimal bac    = budgetRepo.sumBudgetedByProject(projectId);   // Budget At Completion
        BigDecimal actual = budgetRepo.sumActualByProject(projectId);
        BigDecimal committed = budgetRepo.sumCommittedByProject(projectId);
        BigDecimal variance  = bac.subtract(actual).subtract(committed);

        // EVM core calculations
        // PV = BAC × planPct%
        BigDecimal pv = safeMultiply(bac, planPct);
        // EV = BAC × earnedPct%
        BigDecimal ev = safeMultiply(bac, earnedPct);
        // SPI = EV / PV  (>1 = ahead of schedule, <1 = behind)
        BigDecimal spi = pv.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(pv, 4, RoundingMode.HALF_UP);
        // CPI = EV / AC  (>1 = under budget, <1 = over)
        BigDecimal cpi = actual.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(actual, 4, RoundingMode.HALF_UP);
        // EAC = BAC / CPI  (estimated final cost)
        BigDecimal eac = cpi.compareTo(BigDecimal.ZERO) == 0
                ? bac
                : bac.divide(cpi, 2, RoundingMode.HALF_UP);
        // ETC = EAC - AC  (remaining cost to complete)
        BigDecimal etc = eac.subtract(actual);

        // Completion % of budget spent
        BigDecimal completionPct = bac.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : actual.add(committed).multiply(BigDecimal.valueOf(100))
                .divide(bac, 2, RoundingMode.HALF_UP);

        return new BudgetSummaryResponse(bac, committed, actual, variance,
                completionPct, pv, ev, actual, spi, cpi, eac, etc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshProjectBudget(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId).ifPresent(p -> {
            BigDecimal total     = budgetRepo.sumBudgetedByProject(projectId);
            BigDecimal actual    = budgetRepo.sumActualByProject(projectId);
            BigDecimal committed = budgetRepo.sumCommittedByProject(projectId);
            p.setBudgetTotal(total);
            p.setBudgetSpent(actual);
            p.setBudgetCommitted(committed);
            p.updateHealth();
            projectRepo.save(p);
        });
    }

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private BigDecimal safeMultiply(BigDecimal base, BigDecimal pct) {
        if (pct == null || base == null) return BigDecimal.ZERO;
        return base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
