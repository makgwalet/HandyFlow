package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.ShiftSwapRequest;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftSwapRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.SwapActionRequest;
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
    private final ShiftSwapService swapService = mock(ShiftSwapService.class);
    private final ShiftSwapRepository swapRepository = mock(ShiftSwapRepository.class);
    private final ShiftRepository shiftRepository = mock(ShiftRepository.class);
    private final IncidentService incidentService = mock(IncidentService.class);
    private final GuardSupervisorService service = new GuardSupervisorService(guards, sessions, live, rounds, incidents, sites,
            swapService, swapRepository, shiftRepository, incidentService);

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

    // ── Actions ──────────────────────────────────────────────────────────────

    private ShiftSwapRequest swapAt(UUID swapSite, String status, UUID requester, UUID proposed) {
        UUID swapId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        ShiftSwapRequest w = mock(ShiftSwapRequest.class);
        when(w.getOriginalShiftId()).thenReturn(shiftId);
        when(w.getRequestingGuardId()).thenReturn(requester);
        when(w.getProposedGuardId()).thenReturn(proposed);
        when(w.getStatus()).thenReturn(ShiftSwapRequest.SwapStatus.valueOf(status));
        Shift shift = mock(Shift.class);
        when(shift.getSiteId()).thenReturn(swapSite);
        when(shiftRepository.findByTenantAndId(TENANT, shiftId)).thenReturn(Optional.of(shift));
        when(swapRepository.findByTenantAndId(any(), any())).thenReturn(Optional.of(w));
        return w;
    }

    @Test
    void aGuardWhoIsNotASupervisorCannotDecideASwap() {
        guard(false);
        assertThatThrownBy(() -> service.decideSwap(TENANT, me, UUID.randomUUID(), true, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not set up as a site supervisor");
        verify(swapService, never()).approveSwap(any(), any(), any(), any());
    }

    @Test
    void aSupervisorCannotDecideASwapTheyArePartOf() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        swapAt(siteId, "PROPOSED_ACCEPTED", me, UUID.randomUUID());
        assertThatThrownBy(() -> service.decideSwap(TENANT, me, UUID.randomUUID(), true, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("part of");
        verify(swapService, never()).approveSwap(any(), any(), any(), any());
    }

    @Test
    void aSwapAtAnotherSiteIsNotFound() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        swapAt(UUID.randomUUID(), "PROPOSED_ACCEPTED", UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> service.decideSwap(TENANT, me, UUID.randomUUID(), true, null))
                .isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
    }

    @Test
    void aSwapNobodyHasAgreedToYetCannotBeApproved() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        swapAt(siteId, "PENDING", UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> service.decideSwap(TENANT, me, UUID.randomUUID(), true, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("cannot be decided now");
        verify(swapService, never()).approveSwap(any(), any(), any(), any());
    }

    @Test
    void aSupervisorApprovesAnAgreedSwapAtTheirSiteUnderTheirOwnId() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        swapAt(siteId, "PROPOSED_ACCEPTED", UUID.randomUUID(), UUID.randomUUID());
        UUID swapId = UUID.randomUUID();
        service.decideSwap(TENANT, me, swapId, true, "fine");
        verify(swapService).approveSwap(org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.eq(swapId),
                org.mockito.ArgumentMatchers.eq(me), org.mockito.ArgumentMatchers.any(SwapActionRequest.class));
    }

    @Test
    void anIncidentAtAnotherSiteCannotBeAcknowledged() {
        guard(true);
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        UUID incidentId = UUID.randomUUID();
        Incident elsewhere = mock(Incident.class);
        when(elsewhere.getSiteId()).thenReturn(UUID.randomUUID());
        when(incidents.findByIdAndTenantId(incidentId, TENANT)).thenReturn(Optional.of(elsewhere));
        assertThatThrownBy(() -> service.acknowledgeIncident(TENANT, me, incidentId))
                .isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
        verify(incidentService, never()).acknowledge(any(), any(), any(), any());
    }

    @Test
    void anIncidentAtMySiteIsResolvedWithMyNameAndTheNote() {
        Guard g = guard(true);
        when(g.getFullName()).thenReturn("Lerato Mokoena");
        when(sessions.resolveCurrentSiteForGuard(me)).thenReturn(siteId);
        UUID incidentId = UUID.randomUUID();
        Incident mine = mock(Incident.class);
        when(mine.getSiteId()).thenReturn(siteId);
        when(incidents.findByIdAndTenantId(incidentId, TENANT)).thenReturn(Optional.of(mine));
        service.resolveIncident(TENANT, me, incidentId, "Gate secured");
        verify(incidentService).resolve(TENANT, incidentId, me, "Lerato Mokoena", "Gate secured");
    }
}
