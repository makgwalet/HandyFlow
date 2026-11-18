package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.BudgetRules.View;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetControlMode;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Procurement budgets, commitments and overrides.
 * <p>
 * Model: Budget (limit) -> Requisition (checked, reserves nothing) -> approved PO = COMMITMENT
 * (checked, then recorded) -> approved invoice = ACTUAL. A PO is a commitment, never an expense:
 * per PO the budget carries max(committed, invoiced), so an invoice above its PO counts in full and
 * a cancelled PO drops out automatically.
 * <p>
 * Amount basis is EX-VAT by default (VAT is normally recoverable); app.scm.budget.include-vat=true
 * switches everything to VAT-inclusive — pick one before go-live, don't flip it mid-year.
 * Invoices with no PO count as actuals too once approved, against the department / cost centre / project they were coded to
 * when captured (an uncoded no-PO invoice belongs to no budget, so the capture screen should ask for the coding).
 * Config: app.scm.budget.near-limit-pct (default 90), app.scm.budget.include-vat (default false).
 */
@Slf4j
@Service("scmBudgetService")
@RequiredArgsConstructor
public class BudgetService {

    private static final List<PoStatus> COMMITTED_STATUSES = List.of(PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED,
            PoStatus.PARTIALLY_RECEIVED, PoStatus.FULLY_RECEIVED, PoStatus.INVOICED);

    private final ScBudgetRepository budgetRepo;
    private final ScBudgetCommitmentRepository commitmentRepo;
    private final ScBudgetOverrideRepository overrideRepo;
    private final ScBudgetEventRepository eventRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final ScRequisitionRepository requisitionRepo;
    private final ScRequisitionLineRepository requisitionLineRepo;
    private final JdbcTemplate jdbc;
    private final ControlExceptionFacade controlExceptionFacade;

    @Value("${app.scm.budget.near-limit-pct:90}")
    private BigDecimal nearLimitPct;

    private final ProcurementPolicy policy;

    private record Usage(BigDecimal committed, BigDecimal actual, BigDecimal used) {}
    private record Dims(String department, String costCentre, String project, LocalDate spendDate) {}

    // ── Budget maintenance ────────────────────────────────────────────────────

    @Transactional
    public BudgetResponse create(TenantId tenantId, UUID userId, String userName, CreateBudgetRequest req) {
        UUID tid = tenantId.getValue();
        if (req.name() == null || req.name().isBlank()) throw bad("A budget name is required");
        BudgetScopeType scope = parse(BudgetScopeType.class, req.scopeType(), "scopeType");
        BudgetControlMode mode = parse(BudgetControlMode.class, req.controlMode(), "controlMode");
        if (req.scopeValue() == null || req.scopeValue().isBlank()) throw bad("scopeValue is required (e.g. the department name)");
        if (req.periodStart() == null || req.periodEnd() == null || req.periodEnd().isBefore(req.periodStart()))
            throw bad("A valid period is required (end on or after start)");
        if (req.amount() == null || req.amount().signum() <= 0) throw bad("Budget amount must be greater than zero");
        if (!budgetRepo.findOverlapping(tid, scope, req.scopeValue().trim(), req.periodStart(), req.periodEnd()).isEmpty())
            throw new HandyFlowException("An active budget for this " + scope + " already covers part of that period — close or shorten it first",
                    HttpStatus.CONFLICT, "BUDGET_OVERLAP");

        ScBudget b = ScBudget.create(tid, req.name().trim(), scope, req.scopeValue().trim(),
                req.periodStart(), req.periodEnd(), req.amount(), mode, req.currency(), userId);
        if (req.parentBudgetId() != null) {
            ScBudget parent = budgetRepo.lockByTenantAndId(tid, req.parentBudgetId())
                    .orElseThrow(() -> new HandyFlowException("Parent budget not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            requireAttachable(tid, parent, b);
            b.setParent(parent.getId());
        }
        b = budgetRepo.save(b);
        event(tid, b.getId(), "BUDGET_CREATED", b.getName() + " R " + b.getAmount() + " " + mode
                + (b.getParentBudgetId() != null ? " (allocated from budget " + b.getParentBudgetId() + ")" : ""), userId, userName);
        return respond(tid, b);
    }

    @Transactional
    public BudgetResponse update(TenantId tenantId, UUID id, UUID userId, String userName, UpdateBudgetRequest req) {
        UUID tid = tenantId.getValue();
        ScBudget b = requireBudget(tid, id);
        List<String> changes = new ArrayList<>();
        if (req.amount() != null && req.amount().signum() <= 0) throw bad("Budget amount must be greater than zero");
        if (req.amount() != null && req.amount().compareTo(b.getAmount()) != 0)
            changes.add("amount " + b.getAmount() + " -> " + req.amount());
        BudgetControlMode mode = req.controlMode() == null ? null : parse(BudgetControlMode.class, req.controlMode(), "controlMode");
        if (mode != null && mode != b.getControlMode()) changes.add("mode " + b.getControlMode() + " -> " + mode);
        if (req.name() != null && !req.name().equals(b.getName())) changes.add("name");

        if (b.isActive() && req.amount() != null && req.amount().compareTo(b.getAmount()) != 0) {
            // Serialise with other allocation changes under the same parent, and with this budget's own children.
            if (b.getParentBudgetId() != null) budgetRepo.lockByTenantAndId(tid, b.getParentBudgetId());
            budgetRepo.lockByTenantAndId(tid, b.getId());
            List<ScBudget> all = budgetRepo.findAllForTenant(tid);
            String reduce = BudgetHierarchyRules.reduceProblem(req.amount(), activeChildrenTotal(all, b.getId(), null));
            if (reduce != null) throw new HandyFlowException(reduce, HttpStatus.CONFLICT, "BUDGET_ALLOCATED");
            if (b.getParentBudgetId() != null) {
                ScBudget parent = all.stream().filter(x -> x.getId().equals(b.getParentBudgetId())).findFirst().orElse(null);
                if (parent != null) {
                    String over = BudgetHierarchyRules.allocationProblem(parent.getAmount(),
                            activeChildrenTotal(all, parent.getId(), b.getId()), req.amount());
                    if (over != null) throw new HandyFlowException(over, HttpStatus.CONFLICT, "BUDGET_OVER_ALLOCATED");
                }
            }
        }
        if ("CLOSED".equalsIgnoreCase(req.status()) && b.isActive()
                && activeChildrenTotal(budgetRepo.findAllForTenant(tid), b.getId(), null).signum() > 0)
            throw new HandyFlowException("Close or detach this budget's sub-budgets first", HttpStatus.CONFLICT, "BUDGET_HAS_CHILDREN");
        if (b.isActive()) b.update(req.name(), req.amount(), mode);
        else if (!changes.isEmpty()) throw new HandyFlowException("A closed budget cannot be changed", HttpStatus.CONFLICT, "BUDGET_CLOSED");
        if ("CLOSED".equalsIgnoreCase(req.status()) && b.isActive()) { b.close(); changes.add("closed"); }
        budgetRepo.save(b);
        if (!changes.isEmpty()) event(tid, id, "BUDGET_CHANGED", String.join("; ", changes), userId, userName);
        return respond(tid, b);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        List<ScBudget> all = budgetRepo.findAllForTenant(tid);
        return all.stream().map(b -> toResponse(b, usage(tid, b, null, all), all)).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(TenantId tenantId, UUID id) {
        ScBudget b = requireBudget(tenantId.getValue(), id);
        return respond(tenantId.getValue(), b);
    }

    // ── Allocation (parent / child) ────────────────────────────────────────────

    /** Allocate a budget out of a parent, or detach it (parentBudgetId null). Audited on both budgets. */
    @Transactional
    public BudgetResponse setParent(TenantId tenantId, UUID id, UUID userId, String userName, BudgetAllocationRequest req) {
        UUID tid = tenantId.getValue();
        UUID newParent = req != null ? req.parentBudgetId() : null;
        requireBudget(tid, id);
        // Lock in id order, like the PO paths, so two allocation changes cannot deadlock each other.
        List<UUID> order = new ArrayList<>(newParent != null ? List.of(id, newParent) : List.of(id));
        Collections.sort(order);
        for (UUID u : order) budgetRepo.lockByTenantAndId(tid, u);
        ScBudget b = requireBudget(tid, id);
        UUID oldParent = b.getParentBudgetId();
        if (Objects.equals(oldParent, newParent)) return respond(tid, b);
        if (newParent != null) {
            ScBudget parent = requireBudget(tid, newParent);
            requireAttachable(tid, parent, b);
            b.setParent(newParent);
            event(tid, id, "BUDGET_ALLOCATED", b.getName() + " allocated from '" + parent.getName() + "' R " + b.getAmount(), userId, userName);
            event(tid, newParent, "BUDGET_CHILD_ADDED", "'" + b.getName() + "' R " + b.getAmount(), userId, userName);
        } else {
            b.setParent(null);
            event(tid, id, "BUDGET_DETACHED", b.getName() + " is now a top-level budget", userId, userName);
            if (oldParent != null) event(tid, oldParent, "BUDGET_CHILD_REMOVED", "'" + b.getName() + "'", userId, userName);
        }
        budgetRepo.save(b);
        return respond(tid, b);
    }

    /** Every budget as a tree, parents first, with rolled-up usage and what is still unallocated. */
    @Transactional(readOnly = true)
    public List<BudgetTreeNode> tree(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        List<ScBudget> all = budgetRepo.findAllForTenant(tid);
        Set<UUID> ids = new HashSet<>();
        for (ScBudget b : all) ids.add(b.getId());
        Map<UUID, List<ScBudget>> byParent = new HashMap<>();
        List<ScBudget> roots = new ArrayList<>();
        for (ScBudget b : all) {
            if (b.getParentBudgetId() != null && ids.contains(b.getParentBudgetId()))
                byParent.computeIfAbsent(b.getParentBudgetId(), k -> new ArrayList<>()).add(b);
            else roots.add(b);
        }
        return roots.stream().map(r -> node(tid, r, byParent, all, 0)).toList();
    }

    private BudgetTreeNode node(UUID tid, ScBudget b, Map<UUID, List<ScBudget>> byParent, List<ScBudget> all, int depth) {
        List<BudgetTreeNode> kids = depth >= BudgetHierarchyRules.MAX_DEPTH ? List.of()
                : byParent.getOrDefault(b.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(ScBudget::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(c -> node(tid, c, byParent, all, depth + 1)).toList();
        return new BudgetTreeNode(toResponse(b, usage(tid, b, null, all), all), kids);
    }

    /** Throws 409 unless {@code child} may be allocated out of {@code parent} (rules in BudgetHierarchyRules). */
    private void requireAttachable(UUID tid, ScBudget parent, ScBudget child) {
        List<ScBudget> all = budgetRepo.findAllForTenant(tid);
        Map<UUID, UUID> parentOf = new HashMap<>();
        Map<UUID, List<UUID>> childrenOf = new HashMap<>();
        for (ScBudget x : all) if (x.getParentBudgetId() != null) {
            parentOf.put(x.getId(), x.getParentBudgetId());
            childrenOf.computeIfAbsent(x.getParentBudgetId(), k -> new ArrayList<>()).add(x.getId());
        }
        if (BudgetHierarchyRules.wouldCycle(child.getId(), parent.getId(), parentOf))
            throw new HandyFlowException("That would make the budget its own ancestor", HttpStatus.CONFLICT, "BUDGET_CYCLE");
        int depth = BudgetHierarchyRules.depthOf(parent.getId(), parentOf) + BudgetHierarchyRules.heightOf(child.getId(), childrenOf);
        if (depth > BudgetHierarchyRules.MAX_DEPTH)
            throw new HandyFlowException("Budgets can be nested at most " + BudgetHierarchyRules.MAX_DEPTH + " levels deep",
                    HttpStatus.CONFLICT, "BUDGET_TOO_DEEP");
        String problem = BudgetHierarchyRules.attachProblem(node(parent), node(child),
                activeChildrenTotal(all, parent.getId(), child.getId()));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "BUDGET_ALLOCATION");
    }

    private static BudgetHierarchyRules.Node node(ScBudget b) {
        return new BudgetHierarchyRules.Node(b.getId(), b.getScopeType(), b.getScopeValue(), b.getPeriodStart(), b.getPeriodEnd(),
                b.getAmount(), b.getCurrency(), b.isActive());
    }

    /** Sum of the active direct children of {@code parentId}, leaving out {@code except} (the budget being changed). */
    private static BigDecimal activeChildrenTotal(List<ScBudget> all, UUID parentId, UUID except) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScBudget x : all)
            if (parentId.equals(x.getParentBudgetId()) && x.isActive() && !x.getId().equals(except)) total = total.add(x.getAmount());
        return total;
    }

    /** The budget and everything allocated beneath it, at any depth and in any status (closed sub-budgets still carry history). */
    private static List<ScBudget> subtree(List<ScBudget> all, ScBudget root) {
        Map<UUID, List<ScBudget>> byParent = new HashMap<>();
        for (ScBudget x : all) if (x.getParentBudgetId() != null) byParent.computeIfAbsent(x.getParentBudgetId(), k -> new ArrayList<>()).add(x);
        List<ScBudget> out = new ArrayList<>(List.of(root));
        Set<UUID> seen = new HashSet<>(List.of(root.getId()));
        Deque<ScBudget> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty())
            for (ScBudget c : byParent.getOrDefault(queue.poll().getId(), List.of()))
                if (seen.add(c.getId())) { out.add(c); queue.add(c); }
        return out;
    }

    private BudgetResponse respond(UUID tid, ScBudget b) {
        List<ScBudget> all = budgetRepo.findAllForTenant(tid);
        return toResponse(b, usage(tid, b, null, all), all);
    }

    /** The POs that make up a budget's usage. */
    @Transactional(readOnly = true)
    public List<BudgetCommitmentResponse> commitments(TenantId tenantId, UUID budgetId) {
        UUID tid = tenantId.getValue();
        ScBudget b = requireBudget(tid, budgetId);
        List<Object> args = new ArrayList<>(List.of(tid, tid));
        String scope = poScopeClause(subtree(budgetRepo.findAllForTenant(tid), b), args);
        List<BudgetCommitmentResponse> out = new ArrayList<>();
        jdbc.query("SELECT c.purchase_order_id, po.order_number, po.supplier_name, po.status, c.amount, "
                        + "COALESCE(inv.actual, 0) AS actual, c.spend_date "
                        + "FROM sc_budget_commitments c JOIN sc_purchase_orders po ON po.id = c.purchase_order_id AND po.status <> 'CANCELLED' "
                        + invoiceJoin(tid) + " WHERE c.tenant_id = ? AND " + scope + " ORDER BY c.spend_date, po.order_number",
                rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal actual = rs.getBigDecimal("actual");
                    out.add(new BudgetCommitmentResponse((UUID) rs.getObject("purchase_order_id"), rs.getString("order_number"),
                            rs.getString("supplier_name"), rs.getString("status"), amount, actual,
                            amount.max(actual), rs.getDate("spend_date").toLocalDate()));
                },
                args.toArray());
        return out;
    }

    /** What-if check without changing anything. */
    @Transactional(readOnly = true)
    public BudgetCheckResponse preview(TenantId tenantId, String department, String costCentre, String project,
                                       BigDecimal amount, LocalDate date) {
        if (amount == null || amount.signum() < 0) throw bad("amount is required");
        return toCheckResponse(evaluate(tenantId.getValue(), new Dims(department, costCentre, project,
                date != null ? date : LocalDate.now()), amount, null, false));
    }

    // ── Enforcement hooks ─────────────────────────────────────────────────────

    /**
     * Called by ScmService.approvePurchaseOrder() once the approval engine has approved. Checks the
     * budget (throws 409 BUDGET_EXCEEDED for a HARD_STOP budget without a covering override), then
     * records / restates the PO's commitment. Throwing rolls the whole approval back.
     */
    @Transactional
    public void commitForPo(TenantId tenantId, ScPurchaseOrder po, UUID actor, String actorName) {
        UUID tid = tenantId.getValue();
        Dims d = dimsForPo(tid, po);
        BigDecimal amount = poAmount(po);
        enforce(tenantId, ScBudgetOverride.PURCHASE_ORDER, po.getId(), "PO " + po.getOrderNumber(),
                evaluate(tid, d, amount, po.getId(), true), amount, true);
        upsertCommitment(tid, po, d, amount);
    }

    /**
     * Called by PoAmendmentService after an amendment changes the PO total. Only an INCREASE is
     * re-checked (a decrease can't breach anything); the commitment is restated either way.
     */
    @Transactional
    public void adjustCommitment(TenantId tenantId, ScPurchaseOrder po, UUID actor, String actorName) {
        UUID tid = tenantId.getValue();
        Dims d = dimsForPo(tid, po);
        BigDecimal amount = poAmount(po);
        Optional<ScBudgetCommitment> existing = commitmentRepo.findByPo(tid, po.getId());
        if (existing.isEmpty() || amount.compareTo(existing.get().getAmount()) > 0) {
            enforce(tenantId, ScBudgetOverride.PURCHASE_ORDER, po.getId(), "PO " + po.getOrderNumber() + " (amended)",
                    evaluate(tid, d, amount, po.getId(), true), amount, true);
        }
        upsertCommitment(tid, po, d, amount);
    }

    /** Called by RequisitionService on submit (alert=true) and approve (alert=false). Reserves nothing. */
    @Transactional
    public void checkRequisition(TenantId tenantId, ScRequisition r, List<ScRequisitionLine> lines, boolean alert) {
        UUID tid = tenantId.getValue();
        BigDecimal amount = requisitionAmount(tid, lines);
        Dims d = new Dims(r.getDepartment(), r.getCostCentre(), r.getProjectRef(),
                r.getRequiredByDate() != null ? r.getRequiredByDate() : LocalDate.now());
        enforce(tenantId, ScBudgetOverride.REQUISITION, r.getId(), "Requisition " + r.getRequisitionNumber(),
                evaluate(tid, d, amount, null, false), amount, alert);
    }

    // ── Overrides ─────────────────────────────────────────────────────────────

    /** Authorises a PO or requisition up to its CURRENT amount; if the amount grows later, a new override is needed. */
    @Transactional
    public void override(TenantId tenantId, String entityType, UUID entityId, String reason, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("A reason is required for a budget override", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        BigDecimal amount;
        String label;
        if (ScBudgetOverride.PURCHASE_ORDER.equals(entityType)) {
            ScPurchaseOrder po = poRepo.findByTenantIdAndId(tid, entityId)
                    .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            amount = poAmount(po);
            label = "PO " + po.getOrderNumber();
        } else {
            ScRequisition r = requisitionRepo.findByTenantAndId(tid, entityId)
                    .orElseThrow(() -> new HandyFlowException("Requisition not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            amount = requisitionAmount(tid, requisitionLineRepo.findByRequisition(tid, entityId));
            label = "Requisition " + r.getRequisitionNumber();
        }
        overrideRepo.save(ScBudgetOverride.create(tid, entityType, entityId, amount, reason.trim(), userId, userName));
        event(tid, null, "BUDGET_OVERRIDE", label + " up to R " + amount + ": " + reason.trim(), userId, userName);
        log.info("[SCM] Budget override on {} up to R {} by {}", label, amount, userName);
    }

    // ── Backfill ──────────────────────────────────────────────────────────────

    /** Creates commitments for approved POs that predate budgets. No checks, no alerts — history, not new spend. */
    @Transactional
    public int backfillCommitments(TenantId tenantId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        int n = 0;
        for (ScPurchaseOrder po : commitmentRepo.findPosWithoutCommitment(tid, COMMITTED_STATUSES)) {
            upsertCommitment(tid, po, dimsForPo(tid, po), poAmount(po));
            n++;
        }
        event(tid, null, "COMMITMENT_BACKFILL", n + " purchase order(s)", userId, userName);
        return n;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** lock=true (PO commit paths) serialises on the budget rows; read-only checks never lock. */
    private BudgetRules.Result evaluate(UUID tid, Dims d, BigDecimal requested, UUID excludePoId, boolean lock) {
        List<ScBudget> covering = lock ? budgetRepo.lockActiveCovering(tid, d.spendDate())
                                       : budgetRepo.findActiveCovering(tid, d.spendDate());
        List<ScBudget> all = budgetRepo.findAllForTenant(tid);
        Map<UUID, ScBudget> allById = new HashMap<>(), coveringById = new HashMap<>();
        for (ScBudget x : all) allById.put(x.getId(), x);
        for (ScBudget x : covering) coveringById.put(x.getId(), x);
        // Budgets the spend is coded to, then every ancestor of those: spend against a sub-budget also counts against the budget it came out of.
        // (In lock mode every active covering budget is already locked, in id order, so the ancestors are held too.)
        Map<UUID, ScBudget> applicable = new LinkedHashMap<>();
        for (ScBudget b : covering) {
            if (!BudgetRules.matches(b.getScopeType(), b.getScopeValue(), d.department(), d.costCentre(), d.project())) continue;
            applicable.putIfAbsent(b.getId(), b);
            UUID up = b.getParentBudgetId();
            for (int guard = 0; up != null && guard < BudgetHierarchyRules.MAX_DEPTH + 1; guard++) {
                ScBudget p = coveringById.get(up);
                if (p != null) applicable.putIfAbsent(p.getId(), p);
                ScBudget raw = allById.get(up);
                up = raw != null ? raw.getParentBudgetId() : null;
            }
        }
        List<View> views = new ArrayList<>();
        for (ScBudget b : applicable.values())
            views.add(new View(b.getId(), b.getName(), b.getScopeType(), b.getScopeValue(), b.getAmount(),
                    b.getControlMode(), usage(tid, b, excludePoId, all).used()));
        return BudgetRules.evaluate(views, requested, nearLimitPct);
    }

    /** Blocks on an un-overridden hard stop; otherwise alerts on warnings/overridden breaches when asked. */
    private void enforce(TenantId tenantId, String entityType, UUID entityId, String label,
                         BudgetRules.Result result, BigDecimal amount, boolean alert) {
        if (!result.hasWarnings()) return;
        if (result.hardStop()) {
            if (overrideRepo.countCovering(tenantId.getValue(), entityType, entityId, amount) == 0)
                throw new HandyFlowException(label + " would exceed a hard-stop budget: " + result.message()
                        + ". An authorised budget override is required.", HttpStatus.CONFLICT, "BUDGET_EXCEEDED");
            if (alert) raise(tenantId, entityType, entityId, "CRITICAL", label + " proceeded under a budget override: " + result.message());
        } else if (alert) {
            raise(tenantId, entityType, entityId, "WARNING", label + " breaches a budget (warn mode): " + result.message());
        }
    }

    private void raise(TenantId tenantId, String entityType, UUID entityId, String severity, String message) {
        try {
            controlExceptionFacade.raise(tenantId, "supplychain", "BUDGET_EXCEEDED",
                    ScBudgetOverride.PURCHASE_ORDER.equals(entityType) ? "ScPurchaseOrder" : "ScRequisition",
                    entityId, severity, message);
        } catch (RuntimeException e) {
            log.warn("[SCM] Could not raise budget control exception: {}", e.getMessage());
        }
    }

    private void upsertCommitment(UUID tid, ScPurchaseOrder po, Dims d, BigDecimal amount) {
        commitmentRepo.findByPo(tid, po.getId()).ifPresentOrElse(
                c -> { c.restate(amount, d.department(), d.costCentre(), d.project(), d.spendDate()); commitmentRepo.save(c); },
                () -> commitmentRepo.save(ScBudgetCommitment.create(tid, po.getId(), amount, d.department(),
                        d.costCentre(), d.project(), d.spendDate())));
    }

    /** Department / cost centre come from the requisition the PO was raised from; project from either. */
    private Dims dimsForPo(UUID tid, ScPurchaseOrder po) {
        String dept = null, cc = null, project = po.getProjectRef();
        if (po.getRequisitionId() != null) {
            ScRequisition r = requisitionRepo.findByTenantAndId(tid, po.getRequisitionId()).orElse(null);
            if (r != null) {
                dept = r.getDepartment();
                cc = r.getCostCentre();
                if (project == null || project.isBlank()) project = r.getProjectRef();
            }
        }
        return new Dims(dept, cc, project, po.getOrderDate() != null ? po.getOrderDate() : LocalDate.now());
    }

    private BigDecimal poAmount(ScPurchaseOrder po) {
        BigDecimal vat = po.getTotalAmount().subtract(po.getSubtotal());
        return ProcurementPolicy.budgetAmount(po.getSubtotal(), vat, policy.forTenant(po.getTenantId()));
    }

    /** SQL fragment: the budget amount of a document with subtotal and total_amount columns, under the company's VAT policy. */
    private String amountSql(UUID tid) {
        // The factor is a number computed from validated policy values (0..1, four decimals), never user text.
        return "(subtotal + (total_amount - subtotal) * " + ProcurementPolicy.vatFactor(policy.forTenant(tid)).toPlainString() + ")";
    }

    private BigDecimal requisitionAmount(UUID tid, List<ScRequisitionLine> lines) {
        BigDecimal factor = ProcurementPolicy.vatFactor(policy.forTenant(tid));
        BigDecimal total = BigDecimal.ZERO;
        for (ScRequisitionLine l : lines) {
            BigDecimal net = l.getQtyRequested().multiply(l.getEstimatedUnitCost()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(net);
            total = total.add(net.multiply(l.getVatRate()).multiply(factor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return total;
    }

    /**
     * What a budget carries. For a budget with sub-budgets this is the union of its own scope and every descendant's scope,
     * counted once per purchase order / invoice (an OR over the scopes, never a sum of the budgets), so a spend coded to both
     * a department and one of its projects is not counted twice.
     */
    private Usage usage(UUID tid, ScBudget b, UUID excludePoId, List<ScBudget> all) {
        List<ScBudget> scopes = subtree(all, b);
        List<Object> args = new ArrayList<>(List.of(tid, tid));
        String scope = poScopeClause(scopes, args);
        args.add(excludePoId != null ? excludePoId : UUID.randomUUID());
        Usage[] box = new Usage[1];
        jdbc.query("SELECT COALESCE(SUM(c.amount), 0) AS committed, COALESCE(SUM(COALESCE(inv.actual, 0)), 0) AS actual, "
                        + "COALESCE(SUM(GREATEST(c.amount, COALESCE(inv.actual, 0))), 0) AS used "
                        + "FROM sc_budget_commitments c JOIN sc_purchase_orders po ON po.id = c.purchase_order_id AND po.status <> 'CANCELLED' "
                        + invoiceJoin(tid) + " WHERE c.tenant_id = ? AND " + scope + " AND c.purchase_order_id <> ?",
                rs -> { box[0] = new Usage(rs.getBigDecimal("committed"), rs.getBigDecimal("actual"), rs.getBigDecimal("used")); },
                args.toArray());
        Usage poUsage = box[0] != null ? box[0] : new Usage(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal noPo = noPoInvoiceActuals(tid, scopes);
        return new Usage(poUsage.committed(), poUsage.actual().add(noPo), poUsage.used().add(noPo));
    }

    /** "(col matches AND date in period) OR ..." over the given budgets, on the commitment row; appends its bind values to args. */
    private static String poScopeClause(List<ScBudget> scopes, List<Object> args) {
        List<String> parts = new ArrayList<>();
        for (ScBudget s : scopes) {
            String col = column(s.getScopeType());
            parts.add("(" + col + " IS NOT NULL AND lower(" + col + ") = lower(?) AND c.spend_date BETWEEN ? AND ?)");
            args.add(s.getScopeValue());
            args.add(java.sql.Date.valueOf(s.getPeriodStart()));
            args.add(java.sql.Date.valueOf(s.getPeriodEnd()));
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    /** True when the tenant has any active budget: only then does an uncoded no-PO invoice escape something. */
    @Transactional(readOnly = true)
    public boolean hasActiveBudgets(TenantId tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sc_budgets WHERE tenant_id = ? AND status = 'ACTIVE' AND control_mode <> 'NONE'", Integer.class, tenantId.getValue());
        return n != null && n > 0;
    }

    /** Approved/paid invoices with no PO, coded to any of these budgets' scope values and dated inside that budget's period. */
    private BigDecimal noPoInvoiceActuals(UUID tid, List<ScBudget> scopes) {
        List<Object> args = new ArrayList<>(List.of(tid));
        List<String> parts = new ArrayList<>();
        for (ScBudget s : scopes) {
            String col = invoiceColumn(s.getScopeType());
            parts.add("(" + col + " IS NOT NULL AND lower(" + col + ") = lower(?) AND invoice_date BETWEEN ? AND ?)");
            args.add(s.getScopeValue());
            args.add(java.sql.Date.valueOf(s.getPeriodStart()));
            args.add(java.sql.Date.valueOf(s.getPeriodEnd()));
        }
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(" + amountSql(tid) + "), 0) "
                        + "FROM sc_supplier_invoices WHERE tenant_id = ? AND purchase_order_id IS NULL AND status IN ('APPROVED','PAID') "
                        + "AND (" + String.join(" OR ", parts) + ")",
                BigDecimal.class, args.toArray());
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String invoiceColumn(BudgetScopeType t) {
        return switch (t) {
            case DEPARTMENT -> "department";
            case COST_CENTRE -> "cost_centre";
            case PROJECT -> "project_ref";
        };
    }

    /** Approved/paid invoices per PO (ex-VAT or inc-VAT to match the commitment basis). First JDBC arg is the tenant id. */
    private String invoiceJoin(UUID tid) {
        return "LEFT JOIN (SELECT purchase_order_id, SUM(" + amountSql(tid) + ") AS actual "
                + "FROM sc_supplier_invoices WHERE tenant_id = ? AND status IN ('APPROVED','PAID') AND purchase_order_id IS NOT NULL "
                + "GROUP BY purchase_order_id) inv ON inv.purchase_order_id = c.purchase_order_id";
    }

    /** Column names come from this fixed switch only — never from user input. */
    private static String column(BudgetScopeType t) {
        return switch (t) {
            case DEPARTMENT -> "c.department";
            case COST_CENTRE -> "c.cost_centre";
            case PROJECT -> "c.project_ref";
        };
    }

    private BudgetResponse toResponse(ScBudget b, Usage u, List<ScBudget> all) {
        BigDecimal allocated = activeChildrenTotal(all, b.getId(), null);
        int children = (int) all.stream().filter(x -> b.getId().equals(x.getParentBudgetId())).count();
        BigDecimal available = b.getAmount().subtract(u.used());
        double pct = b.getAmount().signum() == 0 ? 0
                : u.used().multiply(BigDecimal.valueOf(100)).divide(b.getAmount(), 1, RoundingMode.HALF_UP).doubleValue();
        String state = available.signum() < 0 ? "EXCEEDED" : pct >= nearLimitPct.doubleValue() ? "NEAR_LIMIT" : "OK";
        return new BudgetResponse(b.getId(), b.getName(), b.getScopeType().name(), b.getScopeValue(), b.getPeriodStart(),
                b.getPeriodEnd(), b.getAmount(), b.getControlMode().name(), b.getStatus(), b.getCurrency(),
                u.committed(), u.actual(), u.used(), available, pct, state,
                b.getParentBudgetId(), allocated, b.getAmount().subtract(allocated), children);
    }

    private BudgetCheckResponse toCheckResponse(BudgetRules.Result r) {
        return new BudgetCheckResponse(r.hardStop(), r.hasWarnings(), r.findings().stream()
                .map(f -> new BudgetCheckResponse.Finding(f.budgetId(), f.name(), f.scope().name(), f.scopeValue(),
                        f.mode().name(), f.amount(), f.used(), f.requested(), f.availableAfter(), f.state().name(), f.message()))
                .toList());
    }

    private ScBudget requireBudget(UUID tid, UUID id) {
        return budgetRepo.findByTenantAndId(tid, id)
                .orElseThrow(() -> new HandyFlowException("Budget not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private void event(UUID tid, UUID budgetId, String type, String detail, UUID actor, String actorName) {
        eventRepo.save(ScBudgetEvent.of(tid, budgetId, type, detail, actor, actorName));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        try { return Enum.valueOf(type, raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw bad("Invalid " + field + " '" + raw + "'"); }
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "INVALID_BUDGET"); }
}
