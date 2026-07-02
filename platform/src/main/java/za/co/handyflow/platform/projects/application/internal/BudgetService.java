package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Budget line management and EVM (Earned Value Management) calculations.
 *
 * CHANGES FROM ORIGINAL
 * ──────────────────────
 * 1. getEvm() now auto-computes planPct from actual project schedule dates when
 *    the caller passes planPct = 0 (the new default).
 *    Previously the frontend hardcoded planPct=50, making SPI meaningless.
 *
 * 2. Added @Slf4j — service had no logger, so all errors were silent.
 *
 * 3. refreshProjectBudget() is now private and called internally — no change in
 *    behaviour, just making the encapsulation explicit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final ProjectBudgetLineRepository budgetRepo;
    private final ProjectRepository           projectRepo;
    private final ProjectService              projectService;   // for computePlanPct()
    private final SequenceService             sequenceService;

    // ── Budget lines ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectBudgetLine> getBudgetLines(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return budgetRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectBudgetLine createBudgetLine(TenantId tenantId, UUID projectId,
                                              CreateBudgetLineRequest req) {
        verifyProject(tenantId, projectId);
        // FIX: Use SequenceService for sort order (atomic, race-condition-free)
        int nextOrder = sequenceService.nextSortOrder(tenantId.getValue(), projectId, "BUDGET");
        ProjectBudgetLine line = ProjectBudgetLine.create(
                tenantId.getValue(), projectId, req.phaseId(),
                req.category(), req.description(), req.budgetedAmount(),
                req.isProvisional(), req.isPrimeCost(), nextOrder);
        line = budgetRepo.save(line);
        refreshProjectBudget(tenantId, projectId);
        return line;
    }

    @Transactional
    public ProjectBudgetLine updateBudgetLine(TenantId tenantId, UUID lineId,
                                              BigDecimal budgetedAmount, String description) {
        ProjectBudgetLine line = budgetRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> notFound("Budget line"));
        if (budgetedAmount != null) line.setBudgetedAmount(budgetedAmount);
        if (description    != null) line.setDescription(description);
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

    // ── EVM ───────────────────────────────────────────────────────────────────

    /**
     * Earned Value Management — SPI, CPI, EAC, ETC.
     *
     * planPct:   how far through the schedule we are (0–100).
     *            Pass 0 (or null) to auto-compute from project dates.
     *            Auto-computation: (today − startDate) / (endDate − startDate) × 100.
     *
     * earnedPct: weighted completion % of all tasks (0–100).
     *            Typically the task completion % passed from the controller.
     *
     * FIX: When planPct == 0 (default), the SPI was always BAC × 0 = 0 which
     *       divided to SPI = EV/0 → defaulted to 1.  The frontend hardcoded 50
     *       which made SPI relative to 50% schedule completion regardless of dates.
     *       Now we compute planPct from the real schedule if not provided.
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getEvm(TenantId tenantId, UUID projectId,
                                        BigDecimal planPct, BigDecimal earnedPct) {
        Project p = verifyProject(tenantId, projectId);

        // Auto-compute planPct from schedule dates if not supplied
        BigDecimal effectivePlanPct = (planPct == null || planPct.compareTo(BigDecimal.ZERO) == 0)
                ? projectService.computePlanPct(p)
                : planPct;

        BigDecimal bac       = budgetRepo.sumBudgetedByProject(projectId);   // Budget At Completion
        BigDecimal actual    = budgetRepo.sumActualByProject(projectId);
        BigDecimal committed = budgetRepo.sumCommittedByProject(projectId);
        BigDecimal variance  = bac.subtract(actual).subtract(committed);

        // PV = BAC × planPct%
        BigDecimal pv = safeMultiply(bac, effectivePlanPct);
        // EV = BAC × earnedPct%
        BigDecimal ev = safeMultiply(bac, earnedPct);
        // SPI = EV / PV  (> 1 = ahead of schedule, < 1 = behind)
        BigDecimal spi = pv.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(pv, 4, RoundingMode.HALF_UP);
        // CPI = EV / AC  (> 1 = under budget, < 1 = over)
        BigDecimal cpi = actual.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(actual, 4, RoundingMode.HALF_UP);
        // EAC = BAC / CPI  (estimated final cost)
        BigDecimal eac = cpi.compareTo(BigDecimal.ZERO) == 0
                ? bac
                : bac.divide(cpi, 2, RoundingMode.HALF_UP);
        // ETC = EAC - AC  (remaining cost to complete)
        BigDecimal etc = eac.subtract(actual);

        // Completion % of budget already consumed
        BigDecimal completionPct = bac.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : actual.add(committed).multiply(BigDecimal.valueOf(100))
                .divide(bac, 2, RoundingMode.HALF_UP);

        log.debug("EVM project={} bac={} pv={} ev={} spi={} cpi={}", projectId, bac, pv, ev, spi, cpi);

        return new BudgetSummaryResponse(bac, committed, actual, variance,
                completionPct, pv, ev, actual, spi, cpi, eac, etc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshProjectBudget(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId).ifPresent(proj -> {
            proj.setBudgetTotal(budgetRepo.sumBudgetedByProject(projectId));
            proj.setBudgetSpent(budgetRepo.sumActualByProject(projectId));
            proj.setBudgetCommitted(budgetRepo.sumCommittedByProject(projectId));
            proj.updateHealth();
            projectRepo.save(proj);
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
