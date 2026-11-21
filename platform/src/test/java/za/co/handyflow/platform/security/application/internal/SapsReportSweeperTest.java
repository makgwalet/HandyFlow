package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.repository.ArmouryRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mockito test, not run in the authoring sandbox (no Maven jars). */
class SapsReportSweeperTest {
    private final ArmouryRepository repo = mock(ArmouryRepository.class);
    private final SecurityAlertNotifier alerts = mock(SecurityAlertNotifier.class);
    private final SapsReportSweeper sweeper = new SapsReportSweeper(repo, alerts);

    private Armoury lost(Instant reportedAt) {
        Armoury a = Armoury.register(TenantId.of(UUID.randomUUID()), "SN9", "Handgun", null, "L", null, LocalDate.now().plusYears(1), null);
        a.reportLost("stolen", null, reportedAt, reportedAt.plusSeconds(86400));
        return a;
    }

    @Test
    void alertsOnceThenWaitsADay() {
        Instant t0 = Instant.parse("2026-11-20T08:00:00Z");
        Armoury a = lost(t0);
        when(repo.findLostAwaitingSapsCase(any())).thenReturn(List.of(a));

        assertEquals(1, sweeper.sweepAt(t0.plusSeconds(86400 + 60)));
        assertEquals(0, sweeper.sweepAt(t0.plusSeconds(86400 + 3600)));
        assertEquals(1, sweeper.sweepAt(t0.plusSeconds(86400 + 60 + 86400)));
        verify(alerts, times(2)).sapsOverdue(any(), eq(a.getId()), eq("SN9"));
    }
}
