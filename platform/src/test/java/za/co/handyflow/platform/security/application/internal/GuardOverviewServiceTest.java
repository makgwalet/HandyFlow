package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.GuardScreeningRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Guard 360 overview: look-back windows, counts for the header tiles, caps and the screening gate. */
@ExtendWith(MockitoExtension.class)
class GuardOverviewServiceTest {

    @Mock private GuardService guardService;
    @Mock private GuardScreeningService screeningService;
    @Mock private GuardScreeningRepository screeningRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private SiteRepository siteRepository;

    private static final TenantId TENANT = TenantId.generate();
    private static final Instant NOW = Instant.parse("2026-10-07T08:00:00Z");
    private final UUID guardId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private GuardOverviewService service() {
        return new GuardOverviewService(guardService, screeningService, screeningRepository,
                shiftRepository, incidentRepository, siteRepository);
    }

    private void guardExists() {
        var guard = new GuardResponse(guardId, "Thabo", "Mokoena", "Thabo Mokoena", null, null, null, null,
                "C", true, null, NOW, "ACTIVE", null, null, null, null, null, null, null, null, null, null);
        when(guardService.getGuard(TENANT, guardId)).thenReturn(guard);
        when(guardService.getDocuments(TENANT, guardId)).thenReturn(List.of());
        when(screeningRepository.findByGuard(TENANT, guardId)).thenReturn(List.of());
    }

    private Shift shift(Instant start, boolean completed) {
        Shift s = Shift.create(TENANT, siteId, guardId, start, start.plus(Duration.ofHours(8)), null);
        if (completed) { s.start(); s.complete(); }
        return s;
    }

    @Test
    @DisplayName("Looks back 90 days and forward 14 days for shifts, and 180 days back for incidents")
    void windows() {
        guardExists();
        service().overview(TENANT, guardId, NOW);

        var from = ArgumentCaptor.forClass(Instant.class);
        var to = ArgumentCaptor.forClass(Instant.class);
        verify(shiftRepository).findByGuardInRange(any(), any(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(NOW.minus(Duration.ofDays(90)));
        assertThat(to.getValue()).isEqualTo(NOW.plus(Duration.ofDays(14)));

        var incFrom = ArgumentCaptor.forClass(Instant.class);
        verify(incidentRepository).findByGuardInRange(any(), any(), incFrom.capture(), any());
        assertThat(incFrom.getValue()).isEqualTo(NOW.minus(Duration.ofDays(180)));
    }

    @Test
    @DisplayName("Counts only shifts that have started, and completed ones among them; upcoming shifts are listed but not counted")
    void shiftCounts() {
        guardExists();
        Shift done = shift(NOW.minus(Duration.ofDays(3)), true);
        Shift missedOrScheduled = shift(NOW.minus(Duration.ofDays(2)), false);
        Shift upcoming = shift(NOW.plus(Duration.ofDays(2)), false);
        when(shiftRepository.findByGuardInRange(any(), any(), any(), any()))
                .thenReturn(List.of(done, missedOrScheduled, upcoming));

        var out = service().overview(TENANT, guardId, NOW);

        assertThat(out.counts().shiftsLast90Days()).isEqualTo(2);
        assertThat(out.counts().completedLast90Days()).isEqualTo(1);
        assertThat(out.shifts()).hasSize(3);
        assertThat(out.shifts().get(0).startAt()).isAfter(out.shifts().get(2).startAt()); // newest first
    }

    @Test
    @DisplayName("Counts open incidents as anything not resolved")
    void openIncidents() {
        guardExists();
        Incident open = Incident.create(TENANT, siteId, null, guardId, "Open one", null, "HIGH", BigDecimal.ZERO, BigDecimal.ZERO);
        Incident resolved = Incident.create(TENANT, siteId, null, guardId, "Done", null, "LOW", BigDecimal.ZERO, BigDecimal.ZERO);
        resolved.resolve();
        when(incidentRepository.findByGuardInRange(any(), any(), any(), any())).thenReturn(List.of(open, resolved));

        var out = service().overview(TENANT, guardId, NOW);

        assertThat(out.counts().incidentsLast180Days()).isEqualTo(2);
        assertThat(out.counts().openIncidents()).isEqualTo(1);
    }

    @Test
    @DisplayName("Caps each list at 50 rows so a busy guard cannot produce a huge payload")
    void caps() {
        guardExists();
        List<Shift> many = new ArrayList<>();
        for (int i = 1; i <= 70; i++) many.add(shift(NOW.minus(Duration.ofHours(i * 9L)), false));
        when(shiftRepository.findByGuardInRange(any(), any(), any(), any())).thenReturn(many);

        var out = service().overview(TENANT, guardId, NOW);

        assertThat(out.shifts()).hasSize(50);
        assertThat(out.counts().shiftsLast90Days()).isEqualTo(70); // the tile counts all, the list is capped
    }

    @Test
    @DisplayName("Passes the screening gate warning through unchanged")
    void gate() {
        guardExists();
        when(screeningService.checkScreeningGate(guardId)).thenReturn("Guard has a FAILED screening");

        assertThat(service().overview(TENANT, guardId, NOW).screeningGate()).isEqualTo("Guard has a FAILED screening");
    }
}
