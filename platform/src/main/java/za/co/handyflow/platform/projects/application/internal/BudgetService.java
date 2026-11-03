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
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Budget line management and EVM (Earned Value Management) calculations.
 *
 * CHUNK 4 CHANGES
 * ───────────────
 * - All money bookkeeping (cached totals, revisions, ledger) now lives in ProjectCostService.
 *   This class's private refreshProjectBudget() — the second writer of Project.budgetSpent —
 *   is gone; it delegates to ProjectCostService.refreshTotals().
 * - createBudgetLine(): needs an editable project; once the baseline is locked it needs a reason
 *   and is recorded as an ADDITION revision.
 * - updateBudgetLine(): delegates to ProjectCostService.reviseLine() — reason required after the
 *   baseline locks, recorded as a revision, can't go below spent + committed.
 * - deleteBudgetLine(): refused once the baseline is locked (revise the amount to zero instead),
 *   and refused for any line that has ledger entries — deleting it would orphan real costs.
 * - getEvm() is unchanged. For the fuller cost position (original vs revised, commitments, EAC,
 *   contingency, margin) see GET /{projectId}/budget/cost-summary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final ProjectBudgetLineRepository budgetRepo;
    private final ProjectRepository           projectRepo;
    private final ProjectService              projectService;   // for computePlanPct()
    private final SequenceService             sequenceService;
    private final ProjectReferenceValidator   referenceValidator;
    private final ProjectCostService          costService;

    // ── Budget lines ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectBudgetLine> getBudgetLines(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return budgetRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectBudgetLine createBudgetLine(TenantId tenantId, UUID projectId,
                                              CreateBudgetLineRequest req) {
        Project project = verifyProject(tenantId, projectId);
        requireEditable(project);
        referenceValidator.requirePhaseInProject(tenantId, projectId, req.phaseId());

        boolean locked = project.getStatus().isBaselineLocked();
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().trim();
        if (locked && reason == null) {
            throw new HandyFlowException(
                    "A reason is required to add a budget line once the project baseline is approved",
                    HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }

        int nextOrder = sequenceService.nextSortOrder(tenantId.getValue(), projectId, "BUDGET");
        ProjectBudgetLine line = budgetRepo.save(ProjectBudgetLine.create(
                tenantId.getValue(), projectId, req.phaseId(),
                req.category(), req.description(), req.budgetedAmount(),
                req.isProvisional(), req.isPrimeCost(), nextOrder));

        if (locked) {
            costService.recordLineAdded(tenantId, project, line, reason, currentUserId(), TenantContext.getCurrentUserName());
        }
        costService.refreshTotals(tenantId, projectId);
        return line;
    }

    @Transactional
    public ProjectBudgetLine updateBudgetLine(TenantId tenantId, UUID lineId, BigDecimal budgetedAmount,
                                              String description, String reason) {
        return costService.reviseLine(tenantId, lineId, budgetedAmount, description, reason,
                currentUserId(), TenantContext.getCurrentUserName());
    }

    @Transactional
    public void deleteBudgetLine(TenantId tenantId, UUID lineId) {
        ProjectBudgetLine line = budgetRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> notFound("Budget line"));
        Project project = verifyProject(tenantId, line.getProjectId());
        requireEditable(project);

        if (project.getStatus().isBaselineLocked()) {
            throw new HandyFlowException(
                    "Budget lines cannot be deleted once the baseline is approved — revise the amount instead "
                            + "(it must be unspent and uncommitted to reduce it to zero)",
                    HttpStatus.CONFLICT, "BASELINE_LOCKED");
        }
        if (costService.lineHasEntries(lineId)) {
            throw new HandyFlowException(
                    "This line has cost entries and cannot be deleted — reverse them first or keep the line",
                    HttpStatus.CONFLICT, "HAS_COST_ENTRIES");
        }
        UUID projectId = line.getProjectId();
        budgetRepo.delete(line);
        costService.refreshTotals(tenantId, projectId);
    }

    // ── EVM ───────────────────────────────────────────────────────────────────

    /**
     * Earned Value Management — SPI, CPI, EAC, ETC.
     * planPct 0/null auto-computes from project dates; earnedPct is the weighted task completion %.
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getEvm(TenantId tenantId, UUID projectId,
                                        BigDecimal planPct, BigDecimal earnedPct) {
        Project p = verifyProject(tenantId, projectId);

        BigDecimal effectivePlanPct = (planPct == null || planPct.compareTo(BigDecimal.ZERO) == 0)
                ? projectService.computePlanPct(p)
                : planPct;

        BigDecimal bac       = budgetRepo.sumBudgetedByProject(projectId);   // Budget At Completion
        BigDecimal actual    = budgetRepo.sumActualByProject(projectId);
        BigDecimal committed = budgetRepo.sumCommittedByProject(projectId);
        BigDecimal variance  = bac.subtract(actual).subtract(committed);

        BigDecimal pv = safeMultiply(bac, effectivePlanPct);
        BigDecimal ev = safeMultiply(bac, earnedPct);
        BigDecimal spi = pv.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(pv, 4, RoundingMode.HALF_UP);
        BigDecimal cpi = actual.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : ev.divide(actual, 4, RoundingMode.HALF_UP);
        BigDecimal eac = cpi.compareTo(BigDecimal.ZERO) == 0
                ? bac
                : bac.divide(cpi, 2, RoundingMode.HALF_UP);
        BigDecimal etc = eac.subtract(actual);

        BigDecimal completionPct = bac.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : actual.add(committed).multiply(BigDecimal.valueOf(100))
                .divide(bac, 2, RoundingMode.HALF_UP);

        log.debug("EVM project={} bac={} pv={} ev={} spi={} cpi={}", projectId, bac, pv, ev, spi, cpi);

        return new BudgetSummaryResponse(bac, committed, actual, variance,
                completionPct, pv, ev, actual, spi, cpi, eac, etc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private static void requireEditable(Project p) {
        if (!p.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + p.getStatus() + " and its budget can no longer be changed",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
    }

    private static UUID currentUserId() {
        String id = TenantContext.getUserId();
        return id != null ? UUID.fromString(id) : null;
    }

    private BigDecimal safeMultiply(BigDecimal base, BigDecimal pct) {
        if (pct == null || base == null) return BigDecimal.ZERO;
        return base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}