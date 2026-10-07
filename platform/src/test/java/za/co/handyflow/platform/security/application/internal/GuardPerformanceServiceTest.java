package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.*;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RatingItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Gathers the facts from the records and hands them to the pure score and risk rules. */
@ExtendWith(MockitoExtension.class)
class GuardPerformanceServiceTest {

    @Mock private GuardService guardService;
    @Mock private GuardOverviewService overviewService;
    @Mock private GuardRatingService ratingService;
    @Mock private GuardRiskSettingsService settingsService;
    @Mock private ShiftRepository shiftRepository;
    @Mock private CheckpointLogRepository checkpointLogRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private GuardComplaintRepository complaintRepository;

    private static final TenantId TENANT = TenantId.generate();
    private static final Instant NOW = Instant.parse("2026-10-07T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final UUID guardId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private GuardPerformanceService service() {
        return new GuardPerformanceService(guardService, overviewService, ratingService, settingsService, shiftRepository,
                checkpointLogRepository, incidentRepository, complaintRepository);
    }

    private void base(List<Shift> shifts, List<GuardComplaint> complaints) {
        when(settingsService.effective(TENANT)).thenReturn(GuardRiskEngine.Settings.defaults());
        when(shiftRepository.findByGuardInRange(any(), any(), any(), any())).thenReturn(shifts);
        when(incidentRepository.findByGuardInRange(any(), any(), any(), any())).thenReturn(List.of());
        when(complaintRepository.findForGuard(TENANT, guardId)).thenReturn(complaints);
        when(ratingService.recent(TENANT, guardId, TODAY)).thenReturn(List.<RatingItem>of());
        var ov = new GuardOverviewResponse(null, null, List.of(), List.of(), new GuardOverviewResponse.Readiness(100, true, List.of(), List.of()),
                List.of(), List.of(), List.of(), null, List.of(), null);
        when(overviewService.overview(TENANT, guardId, NOW)).thenReturn(ov);
    }

    private Shift completed(int daysAgo) {
        Instant start = NOW.minus(Duration.ofDays(daysAgo));
        Shift s = Shift.create(TENANT, siteId, guardId, start, start.plus(Duration.ofHours(8)), null);
        s.start(); s.complete();
        return s;
    }

    private GuardComplaint complaint(Category cat, Severity sev, LocalDate on, Finding finding, boolean withdraw) {
        var c = GuardComplaint.log(TENANT, "CMP-1", guardId, null, on, cat, sev, "x", ComplainantType.CLIENT, null, null, null, UUID.randomUUID(), "Sam");
        if (withdraw) c.withdraw("retracted", "Sam");
        else if (finding != null) { c.startInvestigation(null); c.recordFinding(finding, "n", "Sam"); }
        return c;
    }

    @Test @DisplayName("A guard with shifts, no complaints and full readiness is scored and has no recommendations")
    void clean() {
        base(List.of(completed(1), completed(2), completed(3), completed(4)), List.of());
        var out = service().performance(TENANT, guardId, NOW);
        assertThat(out.score()).isEqualTo(100);
        assertThat(out.recommendations()).isEmpty();
        assertThat(out.coverage()).isEqualTo(70);
    }

    @Test @DisplayName("Withdrawn and unsubstantiated complaints do not count towards the thresholds")
    void onlyStandingComplaintsCount() {
        base(List.of(), List.of(
                complaint(Category.LATENESS, Severity.LOW, TODAY.minusDays(5), null, false),
                complaint(Category.LATENESS, Severity.LOW, TODAY.minusDays(6), Finding.UNSUBSTANTIATED, false),
                complaint(Category.LATENESS, Severity.LOW, TODAY.minusDays(7), null, true),
                complaint(Category.LATENESS, Severity.LOW, TODAY.minusDays(200), null, false)));
        var out = service().performance(TENANT, guardId, NOW);
        assertThat(out.basis().complaintsCounted()).isEqualTo(1);
        assertThat(out.recommendations()).extracting("code").containsExactly("SUPERVISOR_REVIEW");
    }

    @Test @DisplayName("Two substantiated misconduct findings recommend disciplinary review, but lateness does not count as misconduct")
    void misconduct() {
        base(List.of(), List.of(
                complaint(Category.THEFT, Severity.MEDIUM, TODAY.minusDays(30), Finding.SUBSTANTIATED, false),
                complaint(Category.MISCONDUCT, Severity.LOW, TODAY.minusDays(60), Finding.SUBSTANTIATED, false),
                complaint(Category.LATENESS, Severity.LOW, TODAY.minusDays(10), Finding.SUBSTANTIATED, false)));
        var out = service().performance(TENANT, guardId, NOW);
        assertThat(out.basis().substantiatedMisconduct()).isEqualTo(2);
        assertThat(out.recommendations()).extracting("code").contains("DISCIPLINARY_REVIEW", "WARNING_REVIEW");
    }

    @Test @DisplayName("An open urgent complaint is flagged")
    void urgent() {
        base(List.of(), List.of(complaint(Category.EXCESSIVE_FORCE, Severity.HIGH, TODAY.minusDays(1), null, false)));
        var out = service().performance(TENANT, guardId, NOW);
        assertThat(out.basis().openUrgentComplaints()).isEqualTo(1);
        assertThat(out.recommendations()).extracting("code").contains("URGENT_COMPLAINT");
    }
}
