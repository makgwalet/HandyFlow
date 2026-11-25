package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BillingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Test
    @DisplayName("Events are made for past dates and dates inside the horizon, not beyond, and not twice")
    void generation() {
        List<LocalDate> schedule = AgreementRules.schedule(LocalDate.of(2027, 1, 1), null, "MONTHLY");
        Set<LocalDate> have = Set.of(LocalDate.of(2027, 1, 1));
        List<LocalDate> d = BillingRules.datesToGenerate(schedule, have, TODAY);
        assertThat(d).containsExactly(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 5, 1));
        assertThat(BillingRules.datesToGenerate(schedule, Set.copyOf(d), TODAY).stream().filter(x -> x.isAfter(LocalDate.of(2027, 1, 1))).count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Horizon edge: a date exactly 60 days out is included, one day later is not")
    void horizonEdge() {
        assertThat(BillingRules.datesToGenerate(List.of(TODAY.plusDays(60)), Set.of(), TODAY)).hasSize(1);
        assertThat(BillingRules.datesToGenerate(List.of(TODAY.plusDays(61)), Set.of(), TODAY)).isEmpty();
    }

    @Test
    @DisplayName("The description names the agreement, title and date, and is capped")
    void description() {
        assertThat(BillingRules.describe("CA-000001", " Annual ", LocalDate.of(2027, 4, 1))).isEqualTo("Compliance services, agreement CA-000001 Annual, billing of 2027-04-01");
        assertThat(BillingRules.describe("CA-000001", null, LocalDate.of(2027, 4, 1)).contains("null")).isFalse();
        assertThat(BillingRules.describe("CA-1", "x".repeat(600), TODAY).length()).isEqualTo(500);
    }

    @Test
    @DisplayName("Raising is refused unless the bill is scheduled, the agreement is active, in term, linked to a customer and close enough to its date")
    void raiseProblems() {
        LocalDate due = TODAY.plusDays(7);
        assertThat(BillingRules.raiseProblem("SCHEDULED", due, "ACTIVE", null, true, TODAY)).isNull();
        assertThat(BillingRules.raiseProblem("INVOICED", due, "ACTIVE", null, true, TODAY)).contains("scheduled");
        assertThat(BillingRules.raiseProblem("SCHEDULED", due, "SUSPENDED", null, true, TODAY)).contains("suspended");
        assertThat(BillingRules.raiseProblem("SCHEDULED", due, "ACTIVE", due.minusDays(1), true, TODAY)).contains("end date");
        assertThat(BillingRules.raiseProblem("SCHEDULED", due, "ACTIVE", null, false, TODAY)).contains("customer");
        assertThat(BillingRules.raiseProblem("SCHEDULED", due.plusDays(1), "ACTIVE", null, true, TODAY)).contains("not due yet").contains("2027-03-11");
        assertThat(BillingRules.raiseProblem("SCHEDULED", TODAY.minusDays(30), "ACTIVE", null, true, TODAY)).isNull();
    }

    @Test
    @DisplayName("Stage tells a person what to do today")
    void stage() {
        assertThat(BillingRules.stage("SCHEDULED", TODAY.minusDays(1), TODAY)).isEqualTo("OVERDUE_TO_RAISE");
        assertThat(BillingRules.stage("SCHEDULED", TODAY, TODAY)).isEqualTo("DUE");
        assertThat(BillingRules.stage("SCHEDULED", TODAY.plusDays(7), TODAY)).isEqualTo("DUE");
        assertThat(BillingRules.stage("SCHEDULED", TODAY.plusDays(8), TODAY)).isEqualTo("UPCOMING");
        assertThat(BillingRules.stage("INVOICED", TODAY.minusDays(9), TODAY)).isEqualTo("INVOICED");
        assertThat(BillingRules.daysLate(TODAY.minusDays(4), TODAY)).isEqualTo(4L);
        assertThat(BillingRules.daysLate(TODAY.plusDays(4), TODAY)).isEqualTo(0L);
    }

    @Test
    @DisplayName("A live invoice cannot be released; a cancelled or missing one can")
    void release() {
        assertThat(BillingRules.releaseProblem("SCHEDULED", null)).contains("with an invoice");
        assertThat(BillingRules.releaseProblem("INVOICED", "ISSUED")).contains("issued").contains("Cancel it there");
        assertThat(BillingRules.releaseProblem("INVOICED", "PARTIALLY_PAID")).contains("partially paid");
        assertThat(BillingRules.releaseProblem("INVOICED", "DRAFT")).contains("draft");
        assertThat(BillingRules.releaseProblem("INVOICED", "CANCELLED")).isNull();
        assertThat(BillingRules.releaseProblem("INVOICED", null)).isNull();
    }

    @Test
    @DisplayName("Actions follow the status")
    void actions() {
        assertThat(BillingRules.actionsFor("SCHEDULED")).containsExactly("raise", "skip");
        assertThat(BillingRules.actionsFor("INVOICED")).containsExactly("release");
        assertThat(BillingRules.actionsFor("SKIPPED")).containsExactly("restore");
    }
}
