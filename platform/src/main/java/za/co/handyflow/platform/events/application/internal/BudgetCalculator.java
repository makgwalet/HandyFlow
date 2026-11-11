package za.co.handyflow.platform.events.application.internal;

import za.co.handyflow.platform.events.domain.model.EventBudgetLine;
import za.co.handyflow.platform.events.domain.model.EventCommitment;
import za.co.handyflow.platform.events.domain.model.EventCostEntry;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse.BudgetAlert;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse.LineSummary;
import za.co.handyflow.platform.events.dto.SupplierSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All the budget arithmetic in one place, as pure functions of their inputs
 * (no repositories, no clock, no Spring) so it can be unit-tested exhaustively
 * and so the figures can only ever be computed one way.
 * <p>
 * THE FORECAST RULE — what a commitment is expected to cost in the end:
 * <ul>
 *   <li>APPROVED: the larger of what was committed and what has been invoiced
 *       against it. Invoiced above the commitment is a real overrun and must
 *       show; invoiced below it still leaves the rest committed.</li>
 *   <li>COMPLETED: exactly what was invoiced. The work is done and closed, so
 *       an unused remainder is a saving, no longer an obligation.</li>
 *   <li>QUOTED / CANCELLED: nothing.</li>
 * </ul>
 * Invoices recorded with no commitment (or against one that no longer counts)
 * are added in full — money already incurred cannot be ignored.
 * <p>
 * Callers pass in only ACTIVE cost entries; voided ones are ignored here too
 * as a second line of defence.
 */
final class BudgetCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int DEPOSIT_WARNING_DAYS = 7;

    private BudgetCalculator() {}

    // ── Summary ───────────────────────────────────────────────────────────────

    static BudgetSummaryResponse summarize(String budgetStatus,
                                           List<EventBudgetLine> lines,
                                           List<EventCommitment> commitments,
                                           List<EventCostEntry> entries,
                                           LocalDate today,
                                           long registered,
                                           long checkedIn) {
        List<EventCostEntry> active = entries.stream().filter(EventCostEntry::isActive).toList();

        List<LineSummary> lineSummaries = new ArrayList<>();
        List<BudgetAlert> alerts = new ArrayList<>();
        BigDecimal planned = ZERO, committed = ZERO, invoiced = ZERO, paid = ZERO, forecast = ZERO;

        for (EventBudgetLine line : lines) {
            BigDecimal lCommitted = commitments.stream()
                    .filter(c -> c.getBudgetLineId().equals(line.getId()) && c.isCounted())
                    .map(EventCommitment::getAmount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal lInvoiced = sumEntries(active, line.getId(), EventCostEntry.INVOICE);
            BigDecimal lPaid     = sumEntries(active, line.getId(), EventCostEntry.PAYMENT);
            BigDecimal lForecast = lineForecast(line.getId(), commitments, active);
            BigDecimal lVariance = line.getPlannedAmount().subtract(lForecast);
            boolean over = lForecast.compareTo(line.getPlannedAmount()) > 0;

            lineSummaries.add(new LineSummary(line.getId(), line.getCategory(), line.getDescription(),
                    line.getPlannedAmount(), lCommitted, lInvoiced, lPaid, lForecast, lVariance, over));

            if (over) {
                alerts.add(new BudgetAlert("OVER_BUDGET", "WARNING",
                        line.getDescription() + " (" + line.getCategory() + ") is forecast "
                                + money(lForecast.subtract(line.getPlannedAmount())) + " over its "
                                + money(line.getPlannedAmount()) + " budget", null));
            }

            planned   = planned.add(line.getPlannedAmount());
            committed = committed.add(lCommitted);
            invoiced  = invoiced.add(lInvoiced);
            paid      = paid.add(lPaid);
            forecast  = forecast.add(lForecast);
        }

        alerts.addAll(commitmentAlerts(commitments, active, today));

        long unreconciled = active.stream().filter(e -> !e.isReconciled()).count();
        if (unreconciled > 0) {
            alerts.add(new BudgetAlert("UNRECONCILED", "INFO",
                    unreconciled + " cost entr" + (unreconciled == 1 ? "y is" : "ies are")
                            + " not linked to an accounting record", null));
        }

        BigDecimal outstanding = forecast.subtract(paid).max(ZERO);
        return new BudgetSummaryResponse(budgetStatus, planned, committed, invoiced, paid, forecast,
                planned.subtract(forecast), outstanding,
                perHead(forecast, registered), perHead(forecast, checkedIn),
                lineSummaries, alerts);
    }

    // ── Forecast ──────────────────────────────────────────────────────────────

    /** Expected final cost of one commitment given what has been invoiced against it. */
    static BigDecimal forecastOf(EventCommitment c, BigDecimal invoicedAgainst) {
        if (!c.isCounted()) return ZERO;
        return c.isCompleted() ? invoicedAgainst : c.getAmount().max(invoicedAgainst);
    }

    /** Expected final cost of a whole budget line — see the class Javadoc for the rule. */
    static BigDecimal lineForecast(UUID lineId, List<EventCommitment> commitments, List<EventCostEntry> entries) {
        BigDecimal total = ZERO;
        Set<UUID> countedIds = new HashSet<>();

        for (EventCommitment c : commitments) {
            if (!c.getBudgetLineId().equals(lineId) || !c.isCounted()) continue;
            countedIds.add(c.getId());
            total = total.add(forecastOf(c, invoicedAgainst(c.getId(), entries)));
        }
        for (EventCostEntry e : entries) {
            if (!e.isActive() || !EventCostEntry.INVOICE.equals(e.getKind())
                    || !e.getBudgetLineId().equals(lineId)) continue;
            if (e.getCommitmentId() == null || !countedIds.contains(e.getCommitmentId())) {
                total = total.add(e.getAmount());
            }
        }
        return total;
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    /**
     * One row per supplier (plus one for commitments with no supplier). Only
     * costs tied to a commitment can be attributed to a supplier, so invoices
     * entered with no commitment are not in these rows.
     */
    static List<SupplierSummary> bySupplier(List<EventCommitment> commitments,
                                            List<EventCostEntry> entries,
                                            Map<UUID, String> vendorNames) {
        List<EventCostEntry> active = entries.stream().filter(EventCostEntry::isActive).toList();
        Map<UUID, List<EventCommitment>> grouped = new LinkedHashMap<>();
        for (EventCommitment c : commitments) {
            if (!c.isCounted()) continue;
            grouped.computeIfAbsent(c.getVendorId(), k -> new ArrayList<>()).add(c);
        }

        List<SupplierSummary> out = new ArrayList<>();
        for (Map.Entry<UUID, List<EventCommitment>> g : grouped.entrySet()) {
            BigDecimal committed = ZERO, invoiced = ZERO, paid = ZERO, forecast = ZERO;
            for (EventCommitment c : g.getValue()) {
                BigDecimal inv = invoicedAgainst(c.getId(), active);
                committed = committed.add(c.getAmount());
                invoiced  = invoiced.add(inv);
                paid      = paid.add(paidAgainst(c.getId(), active));
                forecast  = forecast.add(forecastOf(c, inv));
            }
            UUID vendorId = g.getKey();
            String name = vendorId == null ? "No supplier attached" : vendorNames.getOrDefault(vendorId, "Unknown supplier");
            out.add(new SupplierSummary(vendorId, name, g.getValue().size(), committed, invoiced, paid,
                    forecast.subtract(paid).max(ZERO)));
        }
        return out;
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    private static List<BudgetAlert> commitmentAlerts(List<EventCommitment> commitments,
                                                      List<EventCostEntry> active, LocalDate today) {
        List<BudgetAlert> alerts = new ArrayList<>();
        for (EventCommitment c : commitments) {
            BigDecimal invoiced = invoicedAgainst(c.getId(), active);
            BigDecimal paid     = paidAgainst(c.getId(), active);

            if (c.isCounted() && invoiced.compareTo(c.getAmount()) > 0) {
                alerts.add(new BudgetAlert("OVER_COMMITMENT", "WARNING",
                        c.getDescription() + " has been invoiced " + money(invoiced.subtract(c.getAmount()))
                                + " above its " + money(c.getAmount()) + " commitment", c.getId()));
            }

            if (EventCommitment.APPROVED.equals(c.getStatus())) {
                BigDecimal depositPaid = depositsPaidAgainst(c.getId(), active);
                if (c.getDepositAmount() != null && c.getDepositAmount().signum() > 0
                        && c.getDepositDueDate() != null
                        && depositPaid.compareTo(c.getDepositAmount()) < 0) {
                    long days = ChronoUnit.DAYS.between(today, c.getDepositDueDate());
                    BigDecimal due = c.getDepositAmount().subtract(depositPaid);
                    if (days < 0) {
                        alerts.add(new BudgetAlert("DEPOSIT_DUE", "CRITICAL",
                                "Deposit of " + money(due) + " for " + c.getDescription() + " was due on "
                                        + c.getDepositDueDate() + " (" + (-days) + " day" + (days == -1 ? "" : "s")
                                        + " overdue)", c.getId()));
                    } else if (days <= DEPOSIT_WARNING_DAYS) {
                        alerts.add(new BudgetAlert("DEPOSIT_DUE", "WARNING",
                                "Deposit of " + money(due) + " for " + c.getDescription() + " is due on "
                                        + c.getDepositDueDate() + (days == 0 ? " (today)" : " (in " + days + " day" + (days == 1 ? "" : "s") + ")"),
                                c.getId()));
                    }
                }
                if (c.getDueDate() != null && c.getDueDate().isBefore(today)) {
                    alerts.add(new BudgetAlert("DELIVERABLE_OVERDUE", "WARNING",
                            c.getDescription() + " was due on " + c.getDueDate()
                                    + " and is not marked completed", c.getId()));
                }
            }

            if (EventCommitment.CANCELLED.equals(c.getStatus()) && paid.signum() > 0) {
                alerts.add(new BudgetAlert("PAID_ON_CANCELLED", "WARNING",
                        money(paid) + " was paid on cancelled commitment '" + c.getDescription()
                                + "' — recover it or confirm a cancellation fee", c.getId()));
            }
        }
        return alerts;
    }

    // ── Sums ──────────────────────────────────────────────────────────────────

    private static BigDecimal sumEntries(List<EventCostEntry> entries, UUID lineId, String kind) {
        return entries.stream()
                .filter(e -> e.isActive() && e.getBudgetLineId().equals(lineId) && kind.equals(e.getKind()))
                .map(EventCostEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    static BigDecimal invoicedAgainst(UUID commitmentId, List<EventCostEntry> entries) {
        return entries.stream()
                .filter(e -> e.isActive() && commitmentId.equals(e.getCommitmentId())
                        && EventCostEntry.INVOICE.equals(e.getKind()))
                .map(EventCostEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    static BigDecimal paidAgainst(UUID commitmentId, List<EventCostEntry> entries) {
        return entries.stream()
                .filter(e -> e.isActive() && commitmentId.equals(e.getCommitmentId())
                        && EventCostEntry.PAYMENT.equals(e.getKind()))
                .map(EventCostEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal depositsPaidAgainst(UUID commitmentId, List<EventCostEntry> entries) {
        return entries.stream()
                .filter(e -> e.isActive() && commitmentId.equals(e.getCommitmentId())
                        && EventCostEntry.PAYMENT.equals(e.getKind()) && e.isDeposit())
                .map(EventCostEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal perHead(BigDecimal total, long heads) {
        return heads <= 0 ? null : total.divide(BigDecimal.valueOf(heads), 2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}