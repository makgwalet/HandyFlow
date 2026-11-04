package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRisk;
import za.co.handyflow.platform.projects.dto.ReportResponse;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** On-time delivery, margin and the heat map, worked out from plain project facts. */
class ProjectReportTest {

    UUID tenant = UUID.randomUUID();
    LocalDate from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 11, 3), today = to;

    Project project(String number, String client, ProjectStatus status, LocalDate baselineEnd, LocalDate completedOn, String contract, String budget) throws Exception {
        Project p = Project.create(tenant, number, "Project " + number, "CONSTRUCTION", null, client, LocalDate.of(2026, 1, 10), baselineEnd, new BigDecimal(budget), null);
        set(p, "status", status);
        if (completedOn != null) set(p, "completedAt", completedOn.atTime(12, 0).atZone(ZoneId.of("Africa/Johannesburg")).toInstant());
        if (contract != null) p.setContractValue(new BigDecimal(contract));
        return p;
    }
    static void set(Object o, String field, Object v) throws Exception { Field f = o.getClass().getDeclaredField(field); f.setAccessible(true); f.set(o, v); }

    @Test
    void onTimeDeliveryAndMarginCountOnlyProjectsCompletedInThePeriod() throws Exception {
        var early = project("P1", "Acme", ProjectStatus.COMPLETED, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 20), "1000", "800");
        var late = project("P2", "Acme", ProjectStatus.CLOSED, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 10), "2000", "1500");
        var lastYear = project("P3", "Beta", ProjectStatus.CLOSED, LocalDate.of(2025, 6, 30), LocalDate.of(2025, 6, 1), "5000", "4000");
        var live = project("P4", "Beta", ProjectStatus.ACTIVE, LocalDate.of(2026, 10, 31), null, "3000", "2500");
        var hidden = project("P5", "Gamma", ProjectStatus.COMPLETED, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 30), "9000", "100");
        ReportResponse r = ProjectReportService.build(List.of(
                new ProjectReportService.Facts(early, true, new BigDecimal("700"), null),
                new ProjectReportService.Facts(late, true, new BigDecimal("2100"), null),
                new ProjectReportService.Facts(lastYear, true, new BigDecimal("4000"), null),
                new ProjectReportService.Facts(live, true, new BigDecimal("1200"), new BigDecimal("2700")),
                new ProjectReportService.Facts(hidden, false, new BigDecimal("50"), null)), from, to, today);

        assertEquals(3, r.delivery().completed(), "P1, P2 and P5; P3 finished last year");
        assertEquals(2, r.delivery().onTime());
        assertEquals(67, r.delivery().onTimePct());
        assertEquals(1, r.delivery().liveOverdue(), "P4 ended 31 Oct and is still active");
        assertEquals(new BigDecimal("3000"), r.money().revenue(), "P5's money is not visible to this person");
        assertEquals(new BigDecimal("2800"), r.money().cost());
        assertEquals(new BigDecimal("200"), r.money().margin());
        assertEquals(7, r.money().marginPct());
        assertEquals(new BigDecimal("300"), r.money().liveForecastMargin(), "3000 contract − 2700 forecast");
        assertEquals(1, r.hiddenCostProjects());
        assertEquals(2, r.money().overBudgetProjects(), "P2 (2100 > 1500) and P4 (2700 > 2500)");

        var p2 = r.projects().stream().filter(x -> x.projectNumber().equals("P2")).findFirst().orElseThrow();
        assertFalse(p2.onTime());
        assertEquals(10, p2.finishVarianceDays());
        assertEquals(new BigDecimal("-100"), p2.margin());
        var p5 = r.projects().stream().filter(x -> x.projectNumber().equals("P5")).findFirst().orElseThrow();
        assertNull(p5.contractValue(), "hidden cost stays hidden in the rows too");

        var acme = r.byClient().stream().filter(g -> g.key().equals("Acme")).findFirst().orElseThrow();
        assertEquals(2, acme.completed());
        assertEquals(new BigDecimal("200"), acme.margin());
    }

    @Test
    void theHeatMapPlacesOpenRisksByImpactAndProbability() {
        UUID project = UUID.randomUUID();
        var a = ProjectRisk.create(tenant, project, "Glass late", 4, 5, "SCHEDULE", null, null, "Lerato");
        var b = ProjectRisk.create(tenant, project, "Crane down", 4, 5, "SAFETY", null, null, null);
        var c = ProjectRisk.create(tenant, project, "Rain", 2, 1, "SCHEDULE", null, null, null);
        var h = ProjectReportService.heatmapOf(List.of(a, b, c), Map.of());
        assertEquals(2, h.cells()[4][3], "impact 5, probability 4");
        assertEquals(1, h.cells()[0][1]);
        assertEquals(3, h.openRisks());
        assertEquals("Crane down", h.top().get(0).title(), "same score: by title");
    }

    @Test
    void csvNeutralisesFormulasAndQuotesCommas() {
        assertEquals("'=SUM(A1)", ProjectReportExporter.esc("=SUM(A1)"));
        assertEquals("\"Smith, Jones\"", ProjectReportExporter.esc("Smith, Jones"));
        assertEquals("-R 1,250.50", ProjectReportExporter.zar(new BigDecimal("-1250.5")));
    }
}
