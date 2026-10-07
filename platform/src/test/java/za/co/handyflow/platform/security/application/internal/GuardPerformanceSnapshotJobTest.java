package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.PerformanceResponse;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RecommendationItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nightly snapshot: what is stored, and when administrators are told about a recommendation. */
class GuardPerformanceSnapshotJobTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final Instant NOW = Instant.parse("2026-10-07T00:15:00Z"); // 02:15 SAST, 7 October
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private final GuardRepository guards = mock(GuardRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final GuardPerformanceService performance = mock(GuardPerformanceService.class);
    private final GuardScoreHistoryStore store = mock(GuardScoreHistoryStore.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TenantAdminRecipients recipients = mock(TenantAdminRecipients.class);
    private final GuardPerformanceSnapshotJob job = new GuardPerformanceSnapshotJob(guards, sites, performance, store, notifications, recipients);

    private static RecommendationItem rec(String code, String level) { return new RecommendationItem(code, level, "Title " + code, "why"); }

    private static PerformanceResponse response(Integer score, RecommendationItem... recs) {
        return new PerformanceResponse(score, "GOOD", 80, List.of(), List.of(recs), null, null, null, 0, List.of());
    }

    private Guard guard() {
        Guard g = Guard.create(TENANT, "Gerhard", "Botha", "P1", "id", "082", "C", null, null, null);
        when(guards.findAllActiveList(TENANT)).thenReturn(List.of(g));
        return g;
    }

    @Test @DisplayName("A new code is picked out; a standing code and a change of level are not new")
    void newCodes() {
        assertThat(GuardPerformanceSnapshotJob.newCodes("WARNING_REVIEW:WARN", "WARNING_REVIEW:URGENT,SUPERVISOR_REVIEW:INFO")).containsExactly("SUPERVISOR_REVIEW");
        assertThat(GuardPerformanceSnapshotJob.newCodes("", "A:INFO")).containsExactly("A");
        assertThat(GuardPerformanceSnapshotJob.newCodes("A:INFO", "")).isEmpty();
        assertThat(GuardPerformanceSnapshotJob.newCodes(null, null)).isEmpty();
    }

    @Test @DisplayName("Recommendations are stored as CODE:LEVEL")
    void encode() {
        assertThat(GuardPerformanceSnapshotJob.encode(List.of(rec("A", "WARN"), rec("B", "URGENT")))).isEqualTo("A:WARN,B:URGENT");
        assertThat(GuardPerformanceSnapshotJob.encode(List.of())).isEmpty();
    }

    @Test @DisplayName("The first snapshot for a guard is a baseline: stored, nothing announced")
    void baseline() {
        Guard g = guard();
        when(performance.performance(TENANT, g.getId())).thenReturn(response(72, rec("WARNING_REVIEW", "WARN")));
        when(store.previousRecommendations(TENANT, g.getId(), TODAY)).thenReturn(Optional.empty());
        job.runForTenant(TENANT, NOW);
        verify(store).save(TENANT, g.getId(), TODAY, 72, "GOOD", 80, "WARNING_REVIEW:WARN");
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("A recommendation that was not on the previous snapshot is announced once")
    void announcesNew() {
        Guard g = guard();
        when(performance.performance(TENANT, g.getId())).thenReturn(response(60, rec("WARNING_REVIEW", "WARN"), rec("SUSPENSION_REVIEW", "URGENT")));
        when(store.previousRecommendations(TENANT, g.getId(), TODAY)).thenReturn(Optional.of("WARNING_REVIEW:WARN"));
        when(recipients.resolveTenantAdmins(TENANT)).thenReturn(List.of(Recipient.external("Admin", "a@x.co.za", null)));
        job.runForTenant(TENANT, NOW);
        var captor = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.GUARD_RISK_RECOMMENDATION);
        assertThat(captor.getValue().message()).contains("Gerhard Botha").contains("Title SUSPENSION_REVIEW").doesNotContain("Title WARNING_REVIEW");
    }

    @Test @DisplayName("Standing recommendations send nothing")
    void standingQuiet() {
        Guard g = guard();
        when(performance.performance(TENANT, g.getId())).thenReturn(response(60, rec("WARNING_REVIEW", "WARN")));
        when(store.previousRecommendations(TENANT, g.getId(), TODAY)).thenReturn(Optional.of("WARNING_REVIEW:WARN"));
        job.runForTenant(TENANT, NOW);
        verify(store).save(eq(TENANT), eq(g.getId()), eq(TODAY), eq(60), any(), eq(80), eq("WARNING_REVIEW:WARN"));
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("One guard failing does not stop the others being recorded")
    void failureIsolated() {
        Guard bad = Guard.create(TENANT, "Bad", "Guard", "P2", "id", "083", "C", null, null, null);
        Guard good = Guard.create(TENANT, "Good", "Guard", "P3", "id", "084", "C", null, null, null);
        when(guards.findAllActiveList(TENANT)).thenReturn(List.of(bad, good));
        when(performance.performance(TENANT, bad.getId())).thenThrow(new IllegalStateException("boom"));
        when(performance.performance(TENANT, good.getId())).thenReturn(response(90));
        when(store.previousRecommendations(any(), any(), any())).thenReturn(Optional.empty());
        job.runForTenant(TENANT, NOW);
        verify(store).save(TENANT, good.getId(), TODAY, 90, "GOOD", 80, "");
        verify(store, never()).save(eq(TENANT), eq(bad.getId()), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
