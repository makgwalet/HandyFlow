package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Test @DisplayName("deadlines fall into weeks starting today, and overdue ones are counted apart")
    void weeks() {
        List<LocalDate> dates = Arrays.asList(TODAY.minusDays(2), TODAY, TODAY.plusDays(6), TODAY.plusDays(7), TODAY.plusDays(55), TODAY.plusDays(56), null);
        var weeks = DashboardRules.weeks(TODAY, dates);
        assertThat(weeks.size()).isEqualTo(8);
        assertThat(weeks.get(0).start()).isEqualTo(TODAY);
        assertThat(weeks.get(0).count()).isEqualTo(2);
        assertThat(weeks.get(1).count()).isEqualTo(1);
        assertThat(weeks.get(7).count()).isEqualTo(1);
        assertThat(weeks.stream().mapToInt(DashboardRules.Week::count).sum()).isEqualTo(4);
        assertThat(DashboardRules.overdue(TODAY, dates)).isEqualTo(1);
    }

    @Test @DisplayName("billing is summed by the month it falls due, with empty months kept and earlier ones counted as behind")
    void months() {
        var items = List.of(
                new DashboardRules.Dated(LocalDate.of(2026, 11, 30), new BigDecimal("100.00")),
                new DashboardRules.Dated(LocalDate.of(2026, 11, 10), new BigDecimal("50.00")),
                new DashboardRules.Dated(LocalDate.of(2027, 1, 5), new BigDecimal("700.00")),
                new DashboardRules.Dated(LocalDate.of(2026, 9, 1), new BigDecimal("300.00")),
                new DashboardRules.Dated(LocalDate.of(2028, 1, 1), new BigDecimal("999.00")));
        var months = DashboardRules.months(TODAY, items);
        assertThat(months.size()).isEqualTo(6);
        assertThat(months.get(0).month()).isEqualTo("2026-11");
        assertThat(months.get(0).amount().compareTo(new BigDecimal("150.00")) == 0).isTrue();
        assertThat(months.get(1).count()).isEqualTo(0);
        assertThat(months.get(2).month()).isEqualTo("2027-01");
        assertThat(months.get(2).amount().compareTo(new BigDecimal("700.00")) == 0).isTrue();
        var behind = DashboardRules.behind(TODAY, items);
        assertThat(behind.amount().compareTo(new BigDecimal("300.00")) == 0).isTrue();
        assertThat(behind.count()).isEqualTo(1);
    }

    @Test @DisplayName("a sent quote past its date is shown as expired, and other statuses are left alone")
    void quoteStage() {
        assertThat(DashboardRules.quoteStage("SENT", TODAY.minusDays(1), TODAY)).isEqualTo("EXPIRED");
        assertThat(DashboardRules.quoteStage("SENT", TODAY, TODAY)).isEqualTo("SENT");
        assertThat(DashboardRules.quoteStage("SENT", null, TODAY)).isEqualTo("SENT");
        assertThat(DashboardRules.quoteStage("ACCEPTED", TODAY.minusDays(30), TODAY)).isEqualTo("ACCEPTED");
    }

    @Test @DisplayName("a pipeline lists only stages that have something in them, in order, with unknown stages last")
    void pipeline() {
        var stages = DashboardRules.pipeline(DashboardRules.QUOTE_ORDER, List.of("SENT", "DRAFT", "SENT", "ODD", "ACCEPTED"),
                Arrays.asList(new BigDecimal("10"), new BigDecimal("5"), null, new BigDecimal("1"), new BigDecimal("20")));
        assertThat(stages.stream().map(DashboardRules.Stage::status).toList()).isEqualTo(List.of("DRAFT", "SENT", "ACCEPTED", "ODD"));
        assertThat(stages.get(1).count()).isEqualTo(2);
        assertThat(stages.get(1).value().compareTo(new BigDecimal("10")) == 0).isTrue();
        assertThat(DashboardRules.pipeline(DashboardRules.TENDER_ORDER, List.of(), List.of()).isEmpty()).isTrue();
    }
}
