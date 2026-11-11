package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.domain.model.EventBudgetLine;
import za.co.handyflow.platform.events.domain.model.EventCommitment;
import za.co.handyflow.platform.events.domain.model.EventCostEntry;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse.BudgetAlert;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the budget arithmetic — the forecast rule above all, since a wrong
 * forecast silently misleads every budget decision made from it.
 */
class BudgetCalculatorTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    // ── Forecast rule ─────────────────────────────────────────────────────────

    @Test
    void approvedCommitment_invoicedBelowCommitment_forecastStaysAtTheCommitment() {
        EventBudgetLine line = line("10000");
        EventCommitment c = commitment(line, "8000", true);

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of(invoice(line, c, "6000")));

        assertThat(s.lines().get(0).committed()).isEqualByComparingTo("8000");
        assertThat(s.lines().get(0).invoiced()).isEqualByComparingTo("6000");
        assertThat(s.lines().get(0).forecast()).isEqualByComparingTo("8000");   // the other 2000 is still owed
        assertThat(s.variance()).isEqualByComparingTo("2000");
    }

    @Test
    void approvedCommitment_invoicedAboveCommitment_forecastShowsTheOverrun() {
        EventBudgetLine line = line("10000");
        EventCommitment c = commitment(line, "8000", true);

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of(invoice(line, c, "9500")));

        assertThat(s.lines().get(0).forecast()).isEqualByComparingTo("9500");
        assertThat(alertsOfType(s, "OVER_COMMITMENT")).hasSize(1);
    }

    @Test
    void completedCommitment_forecastIsExactlyWhatWasInvoiced() {
        EventBudgetLine line = line("10000");
        EventCommitment c = commitment(line, "8000", true);
        c.complete();

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of(invoice(line, c, "7000")));

        assertThat(s.lines().get(0).forecast()).isEqualByComparingTo("7000");   // 1000 saving realised
        assertThat(s.variance()).isEqualByComparingTo("3000");
    }

    @Test
    void invoiceWithNoCommitment_countsInFull() {
        EventBudgetLine line = line("5000");

        BudgetSummaryResponse s = summarize(line, List.of(), List.of(invoice(line, null, "1200")));

        assertThat(s.lines().get(0).forecast()).isEqualByComparingTo("1200");
        assertThat(s.lines().get(0).committed()).isEqualByComparingTo("0");
    }

    @Test
    void quotedAndCancelledCommitments_countForNothing() {
        EventBudgetLine line = line("5000");
        EventCommitment quoted = commitment(line, "3000", false);
        EventCommitment cancelled = commitment(line, "2000", false);
        cancelled.cancel("no longer needed");

        BudgetSummaryResponse s = summarize(line, List.of(quoted, cancelled), List.of());

        assertThat(s.committed()).isEqualByComparingTo("0");
        assertThat(s.forecast()).isEqualByComparingTo("0");
    }

    @Test
    void voidedEntries_areIgnored() {
        EventBudgetLine line = line("5000");
        EventCommitment c = commitment(line, "3000", true);
        EventCostEntry inv = invoice(line, c, "3000");
        inv.voidEntry("entered twice", "Tester");

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of(inv));

        assertThat(s.invoiced()).isEqualByComparingTo("0");
        assertThat(s.lines().get(0).forecast()).isEqualByComparingTo("3000");   // back to the commitment
    }

    // ── Budget position ───────────────────────────────────────────────────────

    @Test
    void lineForecastAbovePlanned_isOverBudget_withNegativeVarianceAndAlert() {
        EventBudgetLine line = line("5000");
        EventCommitment c = commitment(line, "6000", true);

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of());

        assertThat(s.lines().get(0).overBudget()).isTrue();
        assertThat(s.lines().get(0).variance()).isEqualByComparingTo("-1000");
        assertThat(alertsOfType(s, "OVER_BUDGET")).hasSize(1);
    }

    @Test
    void outstanding_isForecastMinusPaid_neverNegative() {
        EventBudgetLine line = line("10000");
        EventCommitment c = commitment(line, "8000", true);

        BudgetSummaryResponse partlyPaid = summarize(line, List.of(c),
                List.of(payment(line, c, "3000", false)));
        assertThat(partlyPaid.outstanding()).isEqualByComparingTo("5000");

        BudgetSummaryResponse overpaid = summarize(line, List.of(c),
                List.of(payment(line, c, "9000", false)));
        assertThat(overpaid.outstanding()).isEqualByComparingTo("0");
    }

    @Test
    void costPerHead_isForecastOverAttendance_andNullWithoutAttendees() {
        EventBudgetLine line = line("10000");
        EventCommitment c = commitment(line, "8000", true);

        BudgetSummaryResponse withGuests = BudgetCalculator.summarize("APPROVED", List.of(line),
                List.of(c), List.of(), TODAY, 80, 40);
        assertThat(withGuests.costPerRegistered()).isEqualByComparingTo("100.00");
        assertThat(withGuests.costPerCheckedIn()).isEqualByComparingTo("200.00");

        BudgetSummaryResponse noGuests = BudgetCalculator.summarize("APPROVED", List.of(line),
                List.of(c), List.of(), TODAY, 0, 0);
        assertThat(noGuests.costPerRegistered()).isNull();
        assertThat(noGuests.costPerCheckedIn()).isNull();
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    @Test
    void unpaidDepositPastItsDueDate_isCritical_andClearsOncePaid() {
        EventBudgetLine line = line("10000");
        EventCommitment c = EventCommitment.create(TENANT, EVENT, line.getId(), null, "Venue hire",
                new BigDecimal("8000"), null, null, null, null, null,
                new BigDecimal("2000"), TODAY.minusDays(1), USER);
        c.approve(USER, "Tester", null);

        BudgetSummaryResponse unpaid = summarize(line, List.of(c), List.of());
        List<BudgetAlert> due = alertsOfType(unpaid, "DEPOSIT_DUE");
        assertThat(due).hasSize(1);
        assertThat(due.get(0).severity()).isEqualTo("CRITICAL");

        BudgetSummaryResponse paid = summarize(line, List.of(c),
                List.of(payment(line, c, "2000", true)));
        assertThat(alertsOfType(paid, "DEPOSIT_DUE")).isEmpty();
    }

    @Test
    void depositDueWithinAWeek_isAWarning_notCritical() {
        EventBudgetLine line = line("10000");
        EventCommitment c = EventCommitment.create(TENANT, EVENT, line.getId(), null, "Caterer",
                new BigDecimal("8000"), null, null, null, null, null,
                new BigDecimal("2000"), TODAY.plusDays(3), USER);
        c.approve(USER, "Tester", null);

        List<BudgetAlert> due = alertsOfType(summarize(line, List.of(c), List.of()), "DEPOSIT_DUE");

        assertThat(due).hasSize(1);
        assertThat(due.get(0).severity()).isEqualTo("WARNING");
    }

    @Test
    void approvedCommitmentPastItsDueDate_raisesDeliverableOverdue_untilCompleted() {
        EventBudgetLine line = line("10000");
        EventCommitment c = EventCommitment.create(TENANT, EVENT, line.getId(), null, "AV hire",
                new BigDecimal("3000"), null, null, null, "Sound system", TODAY.minusDays(2),
                null, null, USER);
        c.approve(USER, "Tester", null);

        assertThat(alertsOfType(summarize(line, List.of(c), List.of()), "DELIVERABLE_OVERDUE")).hasSize(1);

        c.complete();
        assertThat(alertsOfType(summarize(line, List.of(c), List.of()), "DELIVERABLE_OVERDUE")).isEmpty();
    }

    @Test
    void entriesNotLinkedToAnAccountingRecord_areFlaggedAsUnreconciled() {
        EventBudgetLine line = line("5000");
        EventCommitment c = commitment(line, "3000", true);

        BudgetSummaryResponse s = summarize(line, List.of(c), List.of(invoice(line, c, "3000")));

        assertThat(alertsOfType(s, "UNRECONCILED")).hasSize(1);
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @Test
    void supplierRollup_groupsByVendor_andUsesTheSameForecastRule() {
        EventBudgetLine line = line("20000");
        UUID vendorA = UUID.randomUUID();
        EventCommitment a1 = EventCommitment.create(TENANT, EVENT, line.getId(), vendorA, "Catering",
                new BigDecimal("8000"), null, null, null, null, null, null, null, USER);
        a1.approve(USER, "Tester", null);
        EventCommitment a2 = EventCommitment.create(TENANT, EVENT, line.getId(), vendorA, "Extra staff",
                new BigDecimal("2000"), null, null, null, null, null, null, null, USER);
        a2.approve(USER, "Tester", null);

        List<EventCostEntry> entries = List.of(
                invoice(line, a1, "9000"),             // overrun on a1
                payment(line, a1, "4000", false));

        var rows = BudgetCalculator.bySupplier(List.of(a1, a2), entries, java.util.Map.of(vendorA, "Tasty Bites"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).vendorName()).isEqualTo("Tasty Bites");
        assertThat(rows.get(0).commitmentCount()).isEqualTo(2);
        assertThat(rows.get(0).committed()).isEqualByComparingTo("10000");
        assertThat(rows.get(0).invoiced()).isEqualByComparingTo("9000");
        assertThat(rows.get(0).paid()).isEqualByComparingTo("4000");
        assertThat(rows.get(0).outstanding()).isEqualByComparingTo("7000");   // forecast 9000 + 2000 - paid 4000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BudgetSummaryResponse summarize(EventBudgetLine line, List<EventCommitment> commitments,
                                            List<EventCostEntry> entries) {
        return BudgetCalculator.summarize("APPROVED", List.of(line), commitments, entries, TODAY, 0, 0);
    }

    private List<BudgetAlert> alertsOfType(BudgetSummaryResponse s, String type) {
        return s.alerts().stream().filter(a -> type.equals(a.type())).toList();
    }

    private EventBudgetLine line(String planned) {
        return EventBudgetLine.create(TENANT, EVENT, "CATERING", "Catering", new BigDecimal(planned), null);
    }

    private EventCommitment commitment(EventBudgetLine line, String amount, boolean approve) {
        EventCommitment c = EventCommitment.create(TENANT, EVENT, line.getId(), null, "Supplier work",
                new BigDecimal(amount), null, null, null, null, null, null, null, USER);
        if (approve) c.approve(USER, "Tester", null);
        return c;
    }

    private EventCostEntry invoice(EventBudgetLine line, EventCommitment c, String amount) {
        return EventCostEntry.create(TENANT, EVENT, line.getId(), c != null ? c.getId() : null,
                EventCostEntry.INVOICE, false, new BigDecimal(amount), TODAY, null, null, null, null,
                USER, "Tester");
    }

    private EventCostEntry payment(EventBudgetLine line, EventCommitment c, String amount, boolean deposit) {
        return EventCostEntry.create(TENANT, EVENT, line.getId(), c.getId(),
                EventCostEntry.PAYMENT, deposit, new BigDecimal(amount), TODAY, null, null, null, null,
                USER, "Tester");
    }
}