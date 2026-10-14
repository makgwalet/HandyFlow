package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.Decision;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.Step;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.Strategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;
import za.co.handyflow.platform.debtcollection.dto.StrategyDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies the active strategies to live cases (finance F23). Preview changes nothing; apply raises at most one work item
 * per case per run and records it. It never sends a message, writes to a debtor or changes a case.
 */
@Service
@RequiredArgsConstructor
public class CollectionStrategyEngine {
    private static final Logger log = LoggerFactory.getLogger(CollectionStrategyEngine.class);
    private static final Set<CaseStatus> TERMINAL = EnumSet.of(CaseStatus.SETTLED, CaseStatus.WRITTEN_OFF, CaseStatus.CLOSED);

    private final CollectionStrategyService strategies;
    private final DebtCollectionCaseRepository cases;
    private final PromiseToPayRepository promises;
    private final InvoicingFacade invoicing;
    private final WorkItemService workItems;
    private final JdbcTemplate jdbc;

    private record Exposure(DebtCollectionCase c, BigDecimal balance, long days) {}

    public RunResult preview(TenantId tenantId, LocalDate today) { return run(tenantId, today, false); }

    public RunResult apply(TenantId tenantId, LocalDate today) { return run(tenantId, today, true); }

    private RunResult run(TenantId tenantId, LocalDate today, boolean apply) {
        List<Strategy> active = strategies.activeRules(tenantId);
        List<PreviewRow> rows = new ArrayList<>();
        if (active.isEmpty()) return new RunResult(0, 0, 0, rows);
        Map<UUID, Set<UUID>> handled = handledSteps(tenantId);
        int raised = 0, skippedLate = 0;
        for (Exposure e : exposures(tenantId, today)) {
            DebtCollectionCase c = e.c();
            Strategy s = StrategyRules.pick(active, e.balance(), e.days());
            if (s == null) { rows.add(row(e, null, "NONE", null, null, "No strategy fits this balance and age.")); continue; }
            String why = StrategyRules.skipReason(c.getStatus().name(), !promises.findOpenByCaseId(tenantId, c.getId()).isEmpty(),
                    c.getLastContactDate() == null ? null : ChronoUnit.DAYS.between(c.getLastContactDate(), today), s.quietDays());
            if (why != null) { rows.add(row(e, s, "SKIP", null, null, why)); continue; }
            Decision d = StrategyRules.decide(s.steps(), e.days(), handled.getOrDefault(c.getId(), Set.of()));
            if (d.raise() == null) { rows.add(row(e, s, "WAIT", null, null, "No step is due yet, or all due steps are done.")); continue; }
            Step step = d.raise();
            String title = StrategyRules.title(step, c.getCaseNumber(), c.getDebtorName(), e.days());
            if (apply) {
                try {
                    WorkItem w = workItems.raise(tenantId, c.getId(), StrategyRules.isLegal(step) ? WorkItemType.LEGAL_REVIEW : WorkItemType.MANUAL,
                            title, today, "STRATEGY:" + c.getId() + ":" + step.id(), null, "strategy");
                    record(tenantId, c.getId(), s.id(), step.id(), "RAISED", w.getId(), e.days(), today);
                    for (Step late : d.skipped()) record(tenantId, c.getId(), s.id(), late.id(), "SKIPPED_LATE", null, e.days(), today);
                    raised++;
                    skippedLate += d.skipped().size();
                } catch (RuntimeException ex) {
                    log.warn("[Debt Collection] strategy step not raised case={}: {}", c.getId(), ex.getMessage());
                    rows.add(row(e, s, "SKIP", step, null, "Could not raise the work item: " + ex.getMessage()));
                    continue;
                }
            }
            rows.add(row(e, s, "RAISE", step, title, d.skipped().isEmpty() ? null : d.skipped().size() + " earlier step(s) were missed and are skipped."));
        }
        return new RunResult(rows.size(), raised, skippedLate, rows);
    }

    private PreviewRow row(Exposure e, Strategy s, String decision, Step step, String title, String reason) {
        return new PreviewRow(e.c().getId(), e.c().getCaseNumber(), e.c().getDebtorName(), e.balance(), e.days(), s == null ? null : s.name(), decision,
                step == null ? null : step.action(), step == null ? null : step.afterDays(), title, reason);
    }

    private List<Exposure> exposures(TenantId tenantId, LocalDate today) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicing.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity(), (a, b) -> a));
        List<Exposure> out = new ArrayList<>();
        for (DebtCollectionCase c : cases.findAllActive(tenantId)) {
            if (TERMINAL.contains(c.getStatus())) continue;
            BigDecimal balance = BigDecimal.ZERO;
            long days = 0;
            boolean any = false;
            for (UUID invoiceId : c.getLinkedInvoiceIds()) {
                OutstandingInvoiceSummary inv = byId.get(invoiceId);
                if (inv == null) continue;
                BigDecimal open = inv.total().subtract(inv.settled());
                if (open.signum() <= 0) continue;
                any = true;
                balance = balance.add(open);
                if (inv.dueDate() != null) days = Math.max(days, ChronoUnit.DAYS.between(inv.dueDate(), today));
            }
            if (!any) {   // no linked open invoice: fall back to the case's own balance and age
                balance = c.getTotalOutstanding() == null ? BigDecimal.ZERO : c.getTotalOutstanding();
                days = c.getOpenedDate() == null ? 0 : ChronoUnit.DAYS.between(c.getOpenedDate(), today);
            }
            out.add(new Exposure(c, balance, Math.max(0, days)));
        }
        return out;
    }

    private Map<UUID, Set<UUID>> handledSteps(TenantId tenantId) {
        Map<UUID, Set<UUID>> m = new HashMap<>();
        jdbc.query("SELECT case_id, step_id FROM dc_strategy_applications WHERE tenant_id = ?",
                rs -> { m.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>()).add(rs.getObject(2, UUID.class)); }, tenantId.getValue());
        return m;
    }

    private void record(TenantId tenantId, UUID caseId, UUID strategyId, UUID stepId, String outcome, UUID workItemId, long days, LocalDate today) {
        jdbc.update("""
                INSERT INTO dc_strategy_applications (id, tenant_id, case_id, strategy_id, step_id, outcome, work_item_id, days_overdue, applied_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (case_id, step_id) DO NOTHING""",
                UUID.randomUUID(), tenantId.getValue(), caseId, strategyId, stepId, outcome, workItemId, (int) days, Date.valueOf(today));
    }

    /** Which strategy a single case falls under, what comes next, and what was raised before. */
    public CaseStrategy forCase(TenantId tenantId, UUID caseId, LocalDate today) {
        List<Strategy> active = strategies.activeRules(tenantId);
        Exposure ex = exposures(tenantId, today).stream().filter(e -> e.c().getId().equals(caseId)).findFirst().orElse(null);
        List<HistoryRow> history = jdbc.query("""
                SELECT a.applied_on, s.name, st.after_days, st.action, a.outcome, a.days_overdue
                  FROM dc_strategy_applications a
                  JOIN dc_strategies s ON s.id = a.strategy_id
                  LEFT JOIN dc_strategy_steps st ON st.id = a.step_id
                 WHERE a.tenant_id = ? AND a.case_id = ? ORDER BY a.applied_on DESC, a.created_at DESC""",
                (rs, n) -> new HistoryRow(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getInt(6)),
                tenantId.getValue(), caseId);
        if (ex == null) return new CaseStrategy(caseId, null, 0, BigDecimal.ZERO, null, null, "The case is closed or not found.", history);
        Strategy s = StrategyRules.pick(active, ex.balance(), ex.days());
        if (s == null) return new CaseStrategy(caseId, null, ex.days(), ex.balance(), null, null, "No strategy fits this balance and age.", history);
        Set<UUID> done = handledSteps(tenantId).getOrDefault(caseId, Set.of());
        Step next = s.steps().stream().filter(x -> !done.contains(x.id())).min(Comparator.comparingInt(Step::afterDays)).orElse(null);
        String why = StrategyRules.skipReason(ex.c().getStatus().name(), !promises.findOpenByCaseId(tenantId, caseId).isEmpty(),
                ex.c().getLastContactDate() == null ? null : ChronoUnit.DAYS.between(ex.c().getLastContactDate(), today), s.quietDays());
        return new CaseStrategy(caseId, s.name(), ex.days(), ex.balance(), next == null ? null : next.afterDays(), next == null ? null : next.action(), why, history);
    }

    /** Daily job: every practice that has an active strategy. */
    public int applyAllTenants(LocalDate today) {
        int total = 0;
        for (UUID t : jdbc.queryForList("SELECT DISTINCT tenant_id FROM dc_strategies WHERE active", UUID.class)) {
            try {
                total += apply(TenantId.of(t), today).raised();
            } catch (RuntimeException e) {
                log.error("[Debt Collection] strategy run failed tenant={}: {}", t, e.getMessage(), e);
            }
        }
        return total;
    }
}
