package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterReportRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void rangeDefaultsAndLimits() {
        assertThat(RecruiterReportRules.rangeProblem(null, null, TODAY)).isNull();
        assertThat(RecruiterReportRules.fromOrDefault(null, null, TODAY)).isEqualTo(TODAY.minusDays(89));
        assertThat(RecruiterReportRules.fromOrDefault(null, LocalDate.of(2026, 6, 30), TODAY)).isEqualTo(LocalDate.of(2026, 6, 30).minusDays(89));
        assertThat(RecruiterReportRules.rangeProblem(TODAY, TODAY.minusDays(1), TODAY)).contains("after");
        assertThat(RecruiterReportRules.rangeProblem(TODAY.minusDays(5), TODAY.plusDays(1), TODAY)).contains("future");
        assertThat(RecruiterReportRules.rangeProblem(TODAY.minusDays(732), TODAY, TODAY)).contains("two years");
        assertThat(RecruiterReportRules.rangeProblem(TODAY.minusDays(731), TODAY, TODAY)).isNull();
    }

    @Test
    void aRangeIncludesItsWholeLastDayInSouthAfricanTime() {
        assertThat(RecruiterReportRules.start(LocalDate.of(2026, 10, 1))).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
        assertThat(RecruiterReportRules.endExclusive(LocalDate.of(2026, 10, 31))).isEqualTo(Instant.parse("2026-10-31T22:00:00Z"));
    }

    @Test
    void ratesAndMedians() {
        assertThat(RecruiterReportRules.percent(1, 3)).isEqualTo(33.3);
        assertThat(RecruiterReportRules.percent(2, 3)).isEqualTo(66.7);
        assertThat(RecruiterReportRules.percent(0, 5)).isEqualTo(0.0);
        assertThat(RecruiterReportRules.percent(0, 0)).isNull();
        assertThat(RecruiterReportRules.median(List.of())).isNull();
        assertThat(RecruiterReportRules.median(List.of(5.0, 1.0, 3.0))).isEqualTo(3.0);
        assertThat(RecruiterReportRules.median(List.of(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5);
    }

    @Test
    void csvIsQuotedAndSafeInASpreadsheet() {
        assertThat(RecruiterReportRules.csvCell("plain")).isEqualTo("plain");
        assertThat(RecruiterReportRules.csvCell("a,b")).isEqualTo("\"a,b\"");
        assertThat(RecruiterReportRules.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(RecruiterReportRules.csvCell("=HYPERLINK(\"http://x\")")).startsWith("\"'=HYPERLINK");
        assertThat(RecruiterReportRules.csvCell("+27 82 555 1001")).isEqualTo("'+27 82 555 1001");
        assertThat(RecruiterReportRules.csvCell(-5)).isEqualTo("-5");
        assertThat(RecruiterReportRules.csvCell(null)).isEmpty();
        assertThat(RecruiterReportRules.csv(List.of("A", "B"), List.of(Arrays.asList((Object) "x", 2), Arrays.asList((Object) "y", null))))
                .isEqualTo("A,B\r\nx,2\r\ny,\r\n");
    }
}
