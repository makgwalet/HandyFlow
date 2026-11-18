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
 * Not covered: invoices with no PO (direct AP bills) aren't counted as actuals yet.
 * Config: app.scm.budget.near-limit-pct (default 90), app.scm.budget.include-vat (default false).
 */
@Slf4j
@Service
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

    @Value("${app.scm.budget.include-vat:false}")
    private boolean includeVat;

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

        ScBudget b = budgetRepo.save(ScBudget.create(tid, req.name().trim(), scope, req.scopeValue().trim(),
                req.periodStart(), req.periodEnd(), req.amount(), mode, req.currency(), userId));
        event(tid, b.getId(), "BUDGET_CREATED", b.getName() + " R " + b.getAmount() + " " + mode, userId, userName);
        return toResponse(b, usage(tid, b, null));
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

        if (b.isActive()) b.update(req.name(), req.amount(), mode);
        else if (!changes.isEmpty()) throw new HandyFlowException("A closed budget cannot be changed", HttpStatus.CONFLICT, "BUDGET_CLOSED");
        if ("CLOSED".equalsIgnoreCase(req.status()) && b.isActive()) { b.close(); changes.add("closed"); }
        budgetRepo.save(b);
        if (!changes.isEmpty()) event(tid, id, "BUDGET_CHANGED", String.join("; ", changes), userId, userName);
        return toResponse(b, usage(tid, b, null));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        return budgetRepo.findAllForTenant(tid).stream().map(b -> toResponse(b, usage(tid, b, null))).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(TenantId tenantId, UUID id) {
        ScBudget b = requireBudget(tenantId.getValue(), id);
        return toResponse(b, usage(tenantId.getValue(), b, null));
    }

    /** The POs that make up a budget's usage. */
    @Transactional(readOnly = true)
    public List<BudgetCommitmentResponse> commitments(TenantId tenantId, UUID budgetId) {
        UUID tid = tenantId.getValue();
        ScBudget b = requireBudget(tid, budgetId);
        String col = column(b.getScopeType());
        List<BudgetCommitmentResponse> out = new ArrayList<>();
        jdbc.query("SELECT c.purchase_order_id, po.order_number, po.supplier_name, po.status, c.amount, "
                        + "COALESCE(inv.actual, 0) AS actual, c.spend_date "
                        + "FROM sc_budget_commitments c JOIN sc_purchase_orders po ON po.id = c.purchase_order_id AND po.status <> 'CANCELLED' "
                        + invoiceJoin() + " WHERE c.tenant_id = ? AND " + col + " IS NOT NULL AND lower(" + col + ") = lower(?) "
                        + "AND c.spend_date BETWEEN ? AND ? ORDER BY c.spend_date, po.order_number",
                rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal actual = rs.getBigDecimal("actual");
                    out.add(new BudgetCommitmentResponse((UUID) rs.getObject("purchase_order_id"), rs.getString("order_number"),
                            rs.getString("supplier_name"), rs.getString("status"), amount, actual,
                            amount.max(actual), rs.getDate("spend_date").toLocalDate()));
                },
                tid, tid, b.getScopeValue(), java.sql.Date.valueOf(b.getPeriodStart()), java.sql.Date.valueOf(b.getPeriodEnd()));
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
        BigDecimal amount = requisitionAmount(lines);
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
            amount = requisitionAmount(requisitionLineRepo.findByRequisition(tid, entityId));
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
        List<View> views = new ArrayList<>();
        for (ScBudget b : lock ? budgetRepo.lockActiveCovering(tid, d.spendDate())
                               : budgetRepo.findActiveCovering(tid, d.spendDate())) {
            if (!BudgetRules.matches(b.getScopeType(), b.getScopeValue(), d.department(), d.costCentre(), d.project())) continue;
            views.add(new View(b.getId(), b.getName(), b.getScopeType(), b.getScopeValue(), b.getAmount(),
                    b.getControlMode(), usage(tid, b, excludePoId).used()));
        }
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

    private BigDecimal poAmount(ScPurchaseOrder po) { return includeVat ? po.getTotalAmount() : po.getSubtotal(); }

    private BigDecimal requisitionAmount(List<ScRequisitionLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScRequisitionLine l : lines) {
            BigDecimal net = l.getQtyRequested().multiply(l.getEstimatedUnitCost()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(net);
            if (includeVat) total = total.add(net.multiply(l.getVatRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return total;
    }

    private Usage usage(UUID tid, ScBudget b, UUID excludePoId) {
        String col = column(b.getScopeType());
        Usage[] box = new Usage[1];
        jdbc.query("SELECT COALESCE(SUM(c.amount), 0) AS committed, COALESCE(SUM(COALESCE(inv.actual, 0)), 0) AS actual, "
                        + "COALESCE(SUM(GREATEST(c.amount, COALESCE(inv.actual, 0))), 0) AS used "
                        + "FROM sc_budget_commitments c JOIN sc_purchase_orders po ON po.id = c.purchase_order_id AND po.status <> 'CANCELLED' "
                        + invoiceJoin() + " WHERE c.tenant_id = ? AND " + col + " IS NOT NULL AND lower(" + col + ") = lower(?) "
                        + "AND c.spend_date BETWEEN ? AND ? AND c.purchase_order_id <> ?",
                rs -> { box[0] = new Usage(rs.getBigDecimal("committed"), rs.getBigDecimal("actual"), rs.getBigDecimal("used")); },
                tid, tid, b.getScopeValue(), java.sql.Date.valueOf(b.getPeriodStart()), java.sql.Date.valueOf(b.getPeriodEnd()),
                excludePoId != null ? excludePoId : UUID.randomUUID());
        return box[0] != null ? box[0] : new Usage(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Approved/paid invoices per PO (ex-VAT or inc-VAT to match the commitment basis). First JDBC arg is the tenant id. */
    private String invoiceJoin() {
        return "LEFT JOIN (SELECT purchase_order_id, SUM(" + (includeVat ? "total_amount" : "subtotal") + ") AS actual "
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

    private BudgetResponse toResponse(ScBudget b, Usage u) {
        BigDecimal available = b.getAmount().subtract(u.used());
        double pct = b.getAmount().signum() == 0 ? 0
                : u.used().multiply(BigDecimal.valueOf(100)).divide(b.getAmount(), 1, RoundingMode.HALF_UP).doubleValue();
        String state = available.signum() < 0 ? "EXCEEDED" : pct >= nearLimitPct.doubleValue() ? "NEAR_LIMIT" : "OK";
        return new BudgetResponse(b.getId(), b.getName(), b.getScopeType().name(), b.getScopeValue(), b.getPeriodStart(),
                b.getPeriodEnd(), b.getAmount(), b.getControlMode().name(), b.getStatus(), b.getCurrency(),
                u.committed(), u.actual(), u.used(), available, pct, state);
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
