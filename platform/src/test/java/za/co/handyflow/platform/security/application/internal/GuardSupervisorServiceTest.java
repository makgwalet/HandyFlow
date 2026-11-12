package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.SiteOverview;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only a guard a manager has made a site supervisor sees the overview, and only of the site they are on shift at. */
class GuardSupervisorServiceTest {

    private static final TenantId TENANT = TenantId.generate();

    private final GuardRepository guards = mock(GuardRepository.class);
    private final DeviceSessionService sessions = mock(DeviceSessionService.class);
    private final LiveOperationsService live = mock(LiveOperationsService.class);
    private final PatrolRoundRepository rounds = mock(PatrolRoundRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final GuardSupervisorService service = new GuardSupervisorService(guards, sessions, live, rounds, incidents, sites);

    private final UUID me = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private Guard guard(boolean supervisor) {
        Guard g = mock(Guard.class);
        when(g.isSiteSupervisor()).thenReturn(supervisor);
        when(guards.findActiveById(TENANT, me)).thenReturn(Optional.of(g));
        return g;
    }

    private Incident incident(String status, String title) {
        Incident i = mock(Incident.class);
        when(i.getId()).thenReturn(UUID.randomUUID());
        when(i.getStatus()).thenReturn(status);
        when(i.getTitle()).thenReturn(title);
        when(i.getSeverity()).thenReturn("HIGH");
        when(i.getCreatedAt()).thenReturn(Instant.now());
        return i;
    }

    @Test
    void aGuardWhoIsNotASiteSupervisorIsRefusedAndNothingIsRead() {
        guard(false);
        assertThatThrownBy(() -> service.siteOverview(TENANT, me))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not set up as a site supervisor");
        verify(live, never()).liveGuards(any(), any());
        verify(sessions, never()).resolveCurrentSiteForGuard(any());
    }

    @Test
    void theOverviewIsOfTheSiteTheSupervisorIsOnShiftAtWithRoundCountsAndOnlyUnresolvedIncidents() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        UUID shiftId = UUID.randomUUID();
        when(live.liveGuards(TENANT, siteId)).thenReturn(List.of(new LiveGuardResponse(UUID.randomUUID(), "Sipho Dlamini", "C",
                shiftId, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), false, siteId, "Pilot Site",
                null, null, null, "FRESH", Instant.now().minusSeconds(300), "Main Gate")));
        PatrolRound done = mock(PatrolRound.class);
        when(done.getStatus()).thenReturn(PatrolRound.RoundStatus.COMPLETE);
        PatrolRound missed = mock(PatrolRound.class);
        when(missed.getStatus()).thenReturn(PatrolRound.RoundStatus.MISSED);
        PatrolRound later = mock(PatrolRound.class);
        when(later.getStatus()).thenReturn(PatrolRound.RoundStatus.EXPECTED);
        when(rounds.findByShift(shiftId)).thenReturn(List.of(done, missed, later));
        when(incidents.findBySiteInRange(any(), any(), any(), any()))
                .thenReturn(List.of(incident("OPEN", "Gate forced"), incident("RESOLVED", "Old alarm")));
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("Pilot Site");
        when(sites.findActiveById(TENANT, siteId)).thenReturn(Optional.of(site));

        SiteOverview o = service.siteOverview(TENANT, me);

        verify(live).liveGuards(TENANT, siteId);
        assertThat(o.siteName()).isEqualTo("Pilot Site");
        assertThat(o.onDuty()).hasSize(1);
        assertThat(o.onDuty().get(0).roundsTotal()).isEqualTo(3);
        assertThat(o.onDuty().get(0).roundsComplete()).isEqualTo(1);
        assertThat(o.onDuty().get(0).roundsMissed()).isEqualTo(1);
        assertThat(o.openIncidents()).extracting("title").containsExactly("Gate forced");
    }

    @Test
    void makingAGuardASupervisorIsSavedAndAnUnknownGuardIsNotFound() {
        Guard g = guard(false);
        service.setSiteSupervisor(TENANT, me, true);
        verify(g).setSiteSupervisor(true);
        verify(guards).save(g);

        UUID other = UUID.randomUUID();
        when(guards.findActiveById(TENANT, other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setSiteSupervisor(TENANT, other, true))
                .isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
    }
}
