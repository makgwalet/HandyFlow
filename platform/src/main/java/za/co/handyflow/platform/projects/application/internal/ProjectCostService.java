package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.cost.CostEngine;
import za.co.handyflow.platform.projects.domain.enums.BudgetRevisionType;
import za.co.handyflow.platform.projects.domain.enums.CostEntryType;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetRevision;
import za.co.handyflow.platform.projects.domain.model.ProjectCostEntry;
import za.co.handyflow.platform.projects.domain.repository.ProjectBudgetLineRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectBudgetRevisionRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectCostEntryRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The single owner of project money.
 *
 * SINGLE SOURCE OF TRUTH
 *  Before this chunk two classes wrote Project.budgetSpent from different places (task
 *  actual-cost vs budget lines) and the last writer won; budget-line committed/actual were
 *  bare numbers with no history. Now:
 *    cost ledger (project_cost_entries, append-only)
 *      → SUM per line  → ProjectBudgetLine.committed/actual (cache)
 *      → SUM of lines  → Project.budgetTotal / budgetSpent / budgetCommitted
 *  refreshTotals() is the only code that writes those cached figures.
 *
 * CONCURRENCY
 *  Every operation that changes ledger or budget lines takes a per-project PostgreSQL advisory
 *  lock for the length of its transaction, so idempotency checks, "total cannot go negative"
 *  checks and totals recalculation cannot interleave across requests or instances.
 *
 * RULES
 *  - Ledger entries are never edited or deleted; a mistake is reversed by a new entry (once).
 *  - A posting with a known idempotency key returns the original entry (same details) or 409
 *    (different details) — retries cannot double-post.
 *  - A line's committed or actual total can never go below zero.
 *  - Costs may exceed budget (they are facts) — the response flags it; nothing is blocked.
 *  - Postings are refused only on CLOSED projects. Budget STRUCTURE changes (revise, transfer,
 *    add line) need an editable project (not COMPLETED / CLOSED / CANCELLED).
 *  - After the baseline locks, changing a line's budget needs a reason, is recorded as a revision,
 *    and cannot go below what is already spent + committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCostService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private final ProjectRepository               projectRepo;
    private final ProjectBudgetLineRepository     lineRepo;
    private final ProjectCostEntryRepository      entryRepo;
    private final ProjectBudgetRevisionRepository revisionRepo;

    @PersistenceContext
    private EntityManager em;

    public record CostPosting(UUID budgetLineId, CostEntryType type, BigDecimal amount,
                              String sourceType, String sourceRef, String description,
                              LocalDate entryDate, String idempotencyKey,
                              UUID postedBy, String postedByName) {}

    public record PostedEntry(ProjectCostEntry entry, boolean duplicate, boolean lineOverBudget) {}

    public record TransferResult(UUID transferId, ProjectBudgetLine from, ProjectBudgetLine to) {}

    // ── Ledger: post / reverse ────────────────────────────────────────────────

    @Transactional
    public PostedEntry post(TenantId tenantId, UUID projectId, CostPosting cmd) {
        Project project = loadProject(tenantId, projectId);
        requireNotClosed(project);

        if (cmd.type() == null) throw bad("Entry type is required", "TYPE_REQUIRED");
        BigDecimal amount = normalise(cmd.amount());
        String sourceType = requireText(cmd.sourceType(), "Source type is required", "SOURCE_REQUIRED");
        String sourceRef  = requireText(cmd.sourceRef(),  "Source reference is required", "SOURCE_REQUIRED");

        lock(projectId);
        ProjectBudgetLine line = lineInProject(tenantId, projectId, cmd.budgetLineId());

        String key = blankToNull(cmd.idempotencyKey());
        if (key != null) {
            Optional<ProjectCostEntry> existing = entryRepo.findByKey(tenantId.getValue(), key);
            if (existing.isPresent()) {
                ProjectCostEntry e = existing.get();
                boolean same = e.getProjectId().equals(projectId)
                        && e.getBudgetLineId().equals(line.getId())
                        && e.getEntryType() == cmd.type()
                        && e.getAmount().compareTo(amount) == 0;
                if (!same) {
                    throw new HandyFlowException(
                            "This idempotency key was already used for a different posting",
                            HttpStatus.CONFLICT, "IDEMPOTENCY_MISMATCH");
                }
                return new PostedEntry(e, true, overBudget(line));
            }
        }

        requireNonNegativeAfter(line, cmd.type(), amount);

        ProjectCostEntry saved = entryRepo.save(ProjectCostEntry.create(
                tenantId.getValue(), projectId, line.getId(), cmd.type(), amount,
                sourceType, sourceRef, trim(cmd.description(), 500),
                cmd.entryDate() != null ? cmd.entryDate() : LocalDate.now(SAST),
                null, key, cmd.postedBy(), cmd.postedByName()));

        refreshTotals(project);
        log.info("Cost entry {} {} {} on line={} project={} source={}:{}",
                saved.getId(), cmd.type(), amount, line.getId(), projectId, sourceType, sourceRef);
        return new PostedEntry(saved, false, overBudget(line));
    }

    @Transactional
    public PostedEntry reverse(TenantId tenantId, UUID projectId, UUID entryId, String reason,
                               UUID by, String byName) {
        Project project = loadProject(tenantId, projectId);
        requireNotClosed(project);
        String why = requireText(reason, "A reason is required to reverse a ledger entry", "REASON_REQUIRED");

        lock(projectId);
        ProjectCostEntry original = entryRepo.findByTenantAndId(tenantId.getValue(), entryId)
                .filter(e -> projectId.equals(e.getProjectId()))
                .orElseThrow(() -> notFound("Ledger entry"));
        if (original.getReversalOf() != null) {
            throw new HandyFlowException("A reversal cannot itself be reversed — post a new entry instead",
                    HttpStatus.CONFLICT, "CANNOT_REVERSE_REVERSAL");
        }
        if (entryRepo.isReversed(entryId)) {
            throw new HandyFlowException("This entry has already been reversed",
                    HttpStatus.CONFLICT, "ALREADY_REVERSED");
        }
        ProjectBudgetLine line = lineInProject(tenantId, projectId, original.getBudgetLineId());
        BigDecimal negated = original.getAmount().negate();
        requireNonNegativeAfter(line, original.getEntryType(), negated);

        ProjectCostEntry reversal = entryRepo.save(ProjectCostEntry.create(
                tenantId.getValue(), projectId, line.getId(), original.getEntryType(), negated,
                original.getSourceType(), original.getSourceRef(),
                trim("Reversal: " + why, 500), LocalDate.now(SAST),
                original.getId(), "REVERSAL:" + original.getId(), by, byName));

        refreshTotals(project);
        log.info("Cost entry {} reversed by {} (reason: {}) project={}", entryId, reversal.getId(), why, projectId);
        return new PostedEntry(reversal, false, overBudget(line));
    }

    // ── Budget structure: revise / add / transfer / baseline / forecast ───────

    @Transactional
    public ProjectBudgetLine reviseLine(TenantId tenantId, UUID lineId, BigDecimal newAmount,
                                        String description, String reason, UUID by, String byName) {
        ProjectBudgetLine line = lineRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .orElseThrow(() -> notFound("Budget line"));
        Project project = loadProject(tenantId, line.getProjectId());
        requireEditable(project);
        lock(project.getId());

        if (newAmount != null && newAmount.compareTo(line.getBudgetedAmount()) != 0) {
            String why = blankToNull(reason);
            if (project.getStatus().isBaselineLocked() && why == null) {
                throw bad("A reason is required to change the budget once the project baseline is approved",
                        "REASON_REQUIRED");
            }
            BigDecimal floor = line.getActualAmount().add(line.getCommittedAmount());
            if (newAmount.compareTo(floor) < 0) {
                throw new HandyFlowException(
                        "The budget cannot be reduced below what is already spent and committed (" + floor + ")",
                        HttpStatus.CONFLICT, "BELOW_COMMITTED");
            }
            revisionRepo.save(ProjectBudgetRevision.record(tenantId.getValue(), project.getId(), lineId,
                    BudgetRevisionType.REVISION, line.getBudgetedAmount(), newAmount, why, null, by, byName));
            line.setBudgetedAmount(newAmount);
        }
        if (description != null) line.setDescription(description);

        lineRepo.save(line);
        refreshTotals(project);
        return line;
    }

    /** Called by BudgetService after it creates a line on a project whose baseline is locked. */
    @Transactional
    public void recordLineAdded(TenantId tenantId, Project project, ProjectBudgetLine line,
                                String reason, UUID by, String byName) {
        revisionRepo.save(ProjectBudgetRevision.record(tenantId.getValue(), project.getId(), line.getId(),
                BudgetRevisionType.ADDITION, BigDecimal.ZERO, line.getBudgetedAmount(),
                blankToNull(reason), null, by, byName));
    }

    @Transactional
    public TransferResult transfer(TenantId tenantId, UUID projectId, UUID fromId, UUID toId,
                                   BigDecimal amount, String reason, UUID by, String byName) {
        if (amount == null || amount.signum() <= 0) throw bad("Transfer amount must be greater than zero", "INVALID_AMOUNT");
        BigDecimal amt = normalise(amount);
        if (fromId.equals(toId)) throw bad("Source and destination lines must differ", "SAME_LINE");
        String why = requireText(reason, "A reason is required for a budget transfer", "REASON_REQUIRED");

        Project project = loadProject(tenantId, projectId);
        requireEditable(project);
        lock(projectId);

        ProjectBudgetLine from = lineInProject(tenantId, projectId, fromId);
        ProjectBudgetLine to   = lineInProject(tenantId, projectId, toId);

        BigDecimal available = CostEngine.maxTransferable(
                from.getBudgetedAmount(), from.getActualAmount(), from.getCommittedAmount());
        if (amt.compareTo(available) > 0) {
            throw new HandyFlowException(
                    "Only " + available + " of the source line is unspent and uncommitted",
                    HttpStatus.CONFLICT, "EXCEEDS_AVAILABLE");
        }

        UUID transferId = UUID.randomUUID();
        BigDecimal fromOld = from.getBudgetedAmount(), toOld = to.getBudgetedAmount();
        from.setBudgetedAmount(fromOld.subtract(amt));
        to.setBudgetedAmount(toOld.add(amt));
        revisionRepo.save(ProjectBudgetRevision.record(tenantId.getValue(), projectId, fromId,
                BudgetRevisionType.TRANSFER_OUT, fromOld, from.getBudgetedAmount(), why, transferId, by, byName));
        revisionRepo.save(ProjectBudgetRevision.record(tenantId.getValue(), projectId, toId,
                BudgetRevisionType.TRANSFER_IN, toOld, to.getBudgetedAmount(), why, transferId, by, byName));

        lineRepo.saveAll(List.of(from, to));
        refreshTotals(project);
        log.info("Budget transfer {} {} from line={} to line={} project={}", transferId, amt, fromId, toId, projectId);
        return new TransferResult(transferId, from, to);
    }

    /** Captures each line's current budget as its approved original. Called when the baseline locks. */
    @Transactional
    public int snapshotBaseline(TenantId tenantId, UUID projectId) {
        List<ProjectBudgetLine> changed = lineRepo.findByProject(projectId).stream()
                .filter(l -> tenantId.getValue().equals(l.getTenantId()))
                .filter(ProjectBudgetLine::captureOriginal)
                .toList();
        if (!changed.isEmpty()) lineRepo.saveAll(changed);
        log.info("Budget baseline snapshot project={} lines={}", projectId, changed.size());
        return changed.size();
    }

    @Transactional
    public ProjectBudgetLine setForecast(TenantId tenantId, UUID projectId, UUID lineId, BigDecimal etc) {
        Project project = loadProject(tenantId, projectId);
        requireEditable(project);
        ProjectBudgetLine line = lineInProject(tenantId, projectId, lineId);
        line.setForecastToComplete(etc == null ? null : normalise(etc, true));
        return lineRepo.save(line);
    }

    // ── Change-order application (CHUNK 5) ────────────────────────────────────

    public record AppliedAllocation(UUID lineId, BigDecimal oldAmount, BigDecimal newAmount, boolean created) {}

    /**
     * Applies ONE approved change-order cost allocation and records a revision linked to the change
     * order. Adjusts an existing line (amount may be negative, but never below spent + committed) or,
     * when budgetLineId is null, creates a new line whose description starts "Change Order:" so cost
     * reports group it under VARIATIONS. Does NOT refresh totals — the caller does that once after all
     * allocations, so it runs a single recalculation.
     */
    @Transactional
    public AppliedAllocation applyChangeOrderAllocation(TenantId tenantId, Project project, UUID changeOrderId,
                                                        String changeNumber, UUID budgetLineId, String category,
                                                        String description, BigDecimal amount,
                                                        UUID by, String byName) {
        lock(project.getId());
        BigDecimal amt = normalise(amount);
        String reason = "Change order " + changeNumber;

        if (budgetLineId == null) {
            if (amt.signum() < 0) throw bad("A new budget line cannot have a negative amount", "NEW_LINE_NEGATIVE");
            int order = lineRepo.findMaxSortOrder(project.getId()) + 1;
            String cat = blankToNull(category) == null ? "CONTINGENCY" : trim(category, 50);
            String desc = trim("Change Order: " + changeNumber + (blankToNull(description) != null ? " — " + description.trim() : ""), 300);
            ProjectBudgetLine line = lineRepo.save(ProjectBudgetLine.create(
                    tenantId.getValue(), project.getId(), null, cat, desc, amt, false, false, order));
            revisionRepo.save(ProjectBudgetRevision.recordForChangeOrder(tenantId.getValue(), project.getId(),
                    line.getId(), BudgetRevisionType.ADDITION, BigDecimal.ZERO, amt, reason, changeOrderId, by, byName));
            return new AppliedAllocation(line.getId(), BigDecimal.ZERO, amt, true);
        }

        ProjectBudgetLine line = lineInProject(tenantId, project.getId(), budgetLineId);
        BigDecimal old = line.getBudgetedAmount();
        BigDecimal updated = old.add(amt);
        BigDecimal floor = line.getActualAmount().add(line.getCommittedAmount());
        if (updated.compareTo(floor) < 0) {
            throw new HandyFlowException(
                    "Change order " + changeNumber + " would take a budget line below what is already spent and committed (" + floor + ")",
                    HttpStatus.CONFLICT, "BELOW_COMMITTED");
        }
        line.setBudgetedAmount(updated);
        lineRepo.save(line);
        revisionRepo.save(ProjectBudgetRevision.recordForChangeOrder(tenantId.getValue(), project.getId(),
                line.getId(), BudgetRevisionType.REVISION, old, updated, reason, changeOrderId, by, byName));
        return new AppliedAllocation(line.getId(), old, updated, false);
    }

    // ── Totals: the ONLY writer of cached cost figures ────────────────────────

    @Transactional
    public void refreshTotals(TenantId tenantId, UUID projectId) {
        refreshTotals(loadProject(tenantId, projectId));
    }

    void refreshTotals(Project project) {
        List<Object[]> rows = em.createQuery(
                        "SELECT e.budgetLineId, e.entryType, SUM(e.amount) FROM ProjectCostEntry e "
                                + "WHERE e.tenantId = :t AND e.projectId = :p GROUP BY e.budgetLineId, e.entryType",
                        Object[].class)
                .setParameter("t", project.getTenantId())
                .setParameter("p", project.getId())
                .getResultList();
        Map<UUID, BigDecimal> committed = new HashMap<>(), actual = new HashMap<>();
        for (Object[] r : rows) {
            BigDecimal sum = (BigDecimal) r[2];
            if (r[1] == CostEntryType.COMMITMENT) committed.put((UUID) r[0], sum);
            else                                   actual.put((UUID) r[0], sum);
        }

        List<ProjectBudgetLine> lines = lineRepo.findByProject(project.getId());
        BigDecimal budget = BigDecimal.ZERO, spent = BigDecimal.ZERO, com = BigDecimal.ZERO;
        for (ProjectBudgetLine l : lines) {
            BigDecimal c = committed.getOrDefault(l.getId(), BigDecimal.ZERO);
            BigDecimal a = actual.getOrDefault(l.getId(), BigDecimal.ZERO);
            l.applyLedgerTotals(c, a);
            budget = budget.add(l.getBudgetedAmount());
            spent  = spent.add(a);
            com    = com.add(c);
        }
        lineRepo.saveAll(lines);

        project.setBudgetTotal(budget);
        project.setBudgetSpent(spent);
        project.setBudgetCommitted(com);
        project.updateHealth();
        projectRepo.save(project);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CostEngine.Summary summary(TenantId tenantId, UUID projectId) {
        Project project = loadProject(tenantId, projectId);
        List<CostEngine.Line> lines = lineRepo.findByProject(projectId).stream()
                .map(l -> new CostEngine.Line(l.getId(), l.getCategory(), l.getDescription(),
                        l.getBudgetedAmount(), l.getOriginalAmount(),
                        l.getCommittedAmount(), l.getActualAmount(), l.getForecastToComplete()))
                .toList();

        Object[] pending = em.createQuery(
                        "SELECT COUNT(c), COALESCE(SUM(c.costImpact), 0) FROM ChangeOrder c "
                                + "WHERE c.projectId = :pid AND c.status = 'SUBMITTED'", Object[].class)
                .setParameter("pid", projectId)
                .getSingleResult();
        int count = ((Number) pending[0]).intValue();
        BigDecimal value = new BigDecimal(pending[1].toString());

        return CostEngine.summarize(lines, count, value, project.getContractValue());
    }

    @Transactional(readOnly = true)
    public List<ProjectCostEntry> ledger(TenantId tenantId, UUID projectId, UUID lineId, int limit) {
        loadProject(tenantId, projectId);
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        return lineId != null
                ? entryRepo.findByProjectAndLine(tenantId.getValue(), projectId, lineId, page)
                : entryRepo.findByProject(tenantId.getValue(), projectId, page);
    }

    @Transactional(readOnly = true)
    public List<ProjectBudgetRevision> revisions(TenantId tenantId, UUID projectId) {
        loadProject(tenantId, projectId);
        return revisionRepo.findByProject(tenantId.getValue(), projectId);
    }

    @Transactional(readOnly = true)
    public boolean lineHasEntries(UUID lineId) {
        return entryRepo.countByLine(lineId) > 0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void lock(UUID projectId) {
        em.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(hashtext(:k)) AS text)")
                .setParameter("k", "project-cost:" + projectId)
                .getResultList();
    }

    private ProjectBudgetLine lineInProject(TenantId tenantId, UUID projectId, UUID lineId) {
        return lineRepo.findByTenantAndId(tenantId.getValue(), lineId)
                .filter(l -> projectId.equals(l.getProjectId()))
                .orElseThrow(() -> notFound("Budget line"));
    }

    private void requireNonNegativeAfter(ProjectBudgetLine line, CostEntryType type, BigDecimal delta) {
        BigDecimal current = entryRepo.sumByLineAndType(line.getId(), type);
        if (current.add(delta).signum() < 0) {
            throw new HandyFlowException(
                    "This would make the line's " + type.name().toLowerCase() + " total negative (currently " + current + ")",
                    HttpStatus.CONFLICT, "NEGATIVE_TOTAL");
        }
    }

    private static boolean overBudget(ProjectBudgetLine l) {
        return l.getActualAmount().add(l.getCommittedAmount()).compareTo(l.getBudgetedAmount()) > 0;
    }

    private static BigDecimal normalise(BigDecimal v) { return normalise(v, false); }

    private static BigDecimal normalise(BigDecimal v, boolean allowZero) {
        if (v == null) throw bad("Amount is required", "AMOUNT_REQUIRED");
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP);
        if (!allowZero && r.signum() == 0) throw bad("Amount must not be zero", "AMOUNT_ZERO");
        if (r.abs().compareTo(MAX_AMOUNT) > 0) throw bad("Amount is too large", "AMOUNT_TOO_LARGE");
        return r;
    }

    private Project loadProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private static void requireEditable(Project p) {
        if (!p.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + p.getStatus() + " and its budget can no longer be changed",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
    }

    private static void requireNotClosed(Project p) {
        if (p.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — no further costs can be posted",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
    }

    private static String requireText(String v, String message, String code) {
        String t = blankToNull(v);
        if (t == null) throw bad(message, code);
        return t;
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static String trim(String s, int max) {
        String t = blankToNull(s);
        return t == null ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}