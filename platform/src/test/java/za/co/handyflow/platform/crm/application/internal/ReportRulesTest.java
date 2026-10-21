package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.FunnelRow;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.Lead;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.SourceRow;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.ReportFrequency;
import za.co.handyflow.platform.crm.domain.model.ReportType;

class ReportRulesTest {
    private static final UUID U = new UUID(0, 1), V = new UUID(0, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    private static void refuses(String name, ReportType t, ReportFrequency f, List<UUID> r, String msg) {
        assertThatThrownBy(() -> ReportRules.check(name, t, f, r)).isInstanceOf(IllegalArgumentException.class).hasMessage(msg);
    }

    @Test
    void aScheduleNeedsANameAReportAndAFrequency() {
        ReportRules.check("Weekly", ReportType.PIPELINE, ReportFrequency.WEEKLY, List.of(U, V));
        ReportRules.check("x", ReportType.FUNNEL, ReportFrequency.MONTHLY, List.of());
        refuses(" ", ReportType.PIPELINE, ReportFrequency.WEEKLY, List.of(), "Name the report");
        refuses("x", null, ReportFrequency.WEEKLY, List.of(), "Choose which report to send");
        refuses("x", ReportType.SOURCES, null, List.of(), "Choose how often to send it");
        refuses("x", ReportType.SOURCES, ReportFrequency.WEEKLY, List.of(U, U), "Each person can only be listed once");
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(new UUID(0, i));
        refuses("x", ReportType.SOURCES, ReportFrequency.WEEKLY, many, "A report can go to at most 20 people");
    }

    @Test
    void weeklyGoesOnMondaysAndMonthlyOnTheFirstNeverTwiceInADay() {
        LocalDate monday = LocalDate.of(2026, 10, 19), first = LocalDate.of(2026, 11, 1);
        assertThat(ReportRules.isDue(ReportFrequency.WEEKLY, null, monday)).isTrue();
        assertThat(ReportRules.isDue(ReportFrequency.WEEKLY, null, monday.plusDays(1))).isFalse();
        assertThat(ReportRules.isDue(ReportFrequency.WEEKLY, monday, monday)).isFalse();
        assertThat(ReportRules.isDue(ReportFrequency.WEEKLY, monday.minusDays(7), monday)).isTrue();
        assertThat(ReportRules.isDue(ReportFrequency.MONTHLY, LocalDate.of(2026, 10, 1), first)).isTrue();
        assertThat(ReportRules.isDue(ReportFrequency.MONTHLY, first, first)).isFalse();
        assertThat(ReportRules.isDue(ReportFrequency.MONTHLY, null, monday)).isFalse();
        assertThat(ReportRules.describe(ReportType.PIPELINE, ReportFrequency.WEEKLY)).isEqualTo("Pipeline summary, every Monday");
        assertThat(ReportRules.describe(ReportType.FUNNEL, ReportFrequency.MONTHLY)).isEqualTo("Sales funnel, on the 1st of each month");
    }

    @Test
    void moneyIsWrittenTheSouthAfricanWay() {
        assertThat(ReportDigest.money(new BigDecimal("1234567.5"))).isEqualTo("R 1 234 568");
        assertThat(ReportDigest.money(null)).isEqualTo("R 0");
    }

    @Test
    void pipelineDigestCountsOpenWonAndLostDeals() {
        var m = ReportDigest.pipeline(List.of(
                new Lead(LeadStage.NEW, new BigDecimal("1000"), null), new Lead(null, null, null),
                new Lead(LeadStage.QUALIFIED, new BigDecimal("60000"), TODAY.minusDays(1)), new Lead(LeadStage.QUALIFIED, new BigDecimal("500"), TODAY),
                new Lead(LeadStage.WON, new BigDecimal("9000"), TODAY.minusDays(40)), new Lead(LeadStage.LOST, new BigDecimal("700"), null)), TODAY);
        assertThat(m.title()).isEqualTo("Pipeline summary: 4 open deals");
        assertThat(m.body()).contains("Open deals: 4 worth R 61 500.", "- New: 2 (R 1 000)", "- Qualified: 2 (R 60 500)",
                "1 open deal is past the expected close date.", "Won so far: 1 (R 9 000).", "Lost so far: 1 (R 700).");
        var one = ReportDigest.pipeline(List.of(new Lead(LeadStage.NEW, null, null)), TODAY);
        assertThat(one.title()).isEqualTo("Pipeline summary: 1 open deal");
        assertThat(one.body()).doesNotContain("past the expected");
        assertThat(ReportDigest.pipeline(List.of(), TODAY).title()).isEqualTo("Pipeline summary: 0 open deals");
    }

    @Test
    void sourceAndFunnelDigests() {
        var s = ReportDigest.sources(List.of(new SourceRow("Website", 10, 3, 2, new BigDecimal("0.6")), new SourceRow(null, 1, 0, 0, null)));
        assertThat(s.title()).isEqualTo("Lead sources: 11 leads");
        assertThat(s.body()).contains("- Website: 10 leads, 3 won, 2 lost (60% win rate)", "- Not recorded: 1 lead, 0 won, 0 lost");
        List<SourceRow> big = new ArrayList<>();
        for (int i = 0; i < 12; i++) big.add(new SourceRow("S" + i, 1, 0, 0, null));
        assertThat(ReportDigest.sources(big).body()).endsWith("...and 2 more sources.");
        assertThat(ReportDigest.sources(List.of()).title()).isEqualTo("Lead sources: no leads yet");
        var f = ReportDigest.funnel(List.of(new FunnelRow(LeadStage.NEW, 10), new FunnelRow(LeadStage.WON, 2)), 10, 2, new BigDecimal("0.2"));
        assertThat(f.title()).isEqualTo("Sales funnel: 10 leads");
        assertThat(f.body()).contains("- New: 10").endsWith("2 of 10 leads were won (20%).");
        assertThat(ReportDigest.funnel(List.of(), 0, 0, null).body()).endsWith("No leads yet.");
    }
}
