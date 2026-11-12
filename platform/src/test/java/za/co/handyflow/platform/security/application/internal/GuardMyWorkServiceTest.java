package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.CheckpointLog;
import za.co.handyflow.platform.security.domain.model.DeviceSession;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.PatrolRoute;
import za.co.handyflow.platform.security.domain.model.PatrolRouteCheckpoint;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRouteRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.RoundView;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.ShiftView;
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
import static org.mockito.Mockito.when;

/** The guard's own roster and tonight's patrol: always the token's guard, checkpoints in order with done ticks. */
class GuardMyWorkServiceTest {

    private static final TenantId TENANT = TenantId.generate();

    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final DeviceSessionRepository sessions = mock(DeviceSessionRepository.class);
    private final PatrolRoundService rounds = mock(PatrolRoundService.class);
    private final PatrolRouteRepository routes = mock(PatrolRouteRepository.class);
    private final CheckpointRepository checkpoints = mock(CheckpointRepository.class);
    private final CheckpointLogRepository logs = mock(CheckpointLogRepository.class);
    private final GuardMyWorkService service = new GuardMyWorkService(shifts, sites, sessions, rounds, routes, checkpoints, logs);

    private final UUID me = UUID.randomUUID();

    @Test
    void myShiftsAreLookedUpForTheGivenGuardWithSiteNames() {
        UUID siteId = UUID.randomUUID();
        Shift s = mock(Shift.class);
        when(s.getId()).thenReturn(UUID.randomUUID());
        when(s.getSiteId()).thenReturn(siteId);
        when(s.getStartAt()).thenReturn(Instant.now());
        when(s.getEndAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(shifts.findForGuardBetween(any(), any(), any(), any())).thenReturn(List.of(s));
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("Sandton Mall");
        when(sites.findActiveById(TENANT, siteId)).thenReturn(Optional.of(site));

        List<ShiftView> out = service.myShifts(TENANT, me);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).siteName()).isEqualTo("Sandton Mall");
    }

    @Test
    void roundsNeedAnOpenSession() {
        when(sessions.findOpenByGuard(me)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.myRounds(TENANT, me))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Clock in");
    }

    @Test
    void roundsNeedALinkedShift() {
        DeviceSession session = mock(DeviceSession.class);
        when(session.getShiftId()).thenReturn(null);
        when(sessions.findOpenByGuard(me)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.myRounds(TENANT, me)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void checkpointsComeInRouteOrderWithScannedOnesTicked() {
        UUID shiftId = UUID.randomUUID(), routeId = UUID.randomUUID(), roundId = UUID.randomUUID();
        UUID gate = UUID.randomUUID(), yard = UUID.randomUUID();
        DeviceSession session = mock(DeviceSession.class);
        when(session.getShiftId()).thenReturn(shiftId);
        when(sessions.findOpenByGuard(me)).thenReturn(Optional.of(session));

        PatrolRound round = mock(PatrolRound.class);
        when(round.getId()).thenReturn(roundId);
        when(round.getRouteId()).thenReturn(routeId);
        when(round.getRoundNumber()).thenReturn(1);
        when(round.getExpectedStartAt()).thenReturn(Instant.now());
        when(round.getScansExpected()).thenReturn(2);
        when(round.getScansCompleted()).thenReturn(1);
        when(rounds.getRoundsForShift(shiftId)).thenReturn(List.of(round));

        PatrolRouteCheckpoint second = mock(PatrolRouteCheckpoint.class);
        when(second.getCheckpointId()).thenReturn(yard);
        when(second.getSequence()).thenReturn(2);
        PatrolRouteCheckpoint first = mock(PatrolRouteCheckpoint.class);
        when(first.getCheckpointId()).thenReturn(gate);
        when(first.getSequence()).thenReturn(1);
        PatrolRoute route = mock(PatrolRoute.class);
        when(route.getCheckpoints()).thenReturn(List.of(second, first)); // stored out of order on purpose
        when(routes.findById(routeId)).thenReturn(Optional.of(route));

        Checkpoint gateCp = mock(Checkpoint.class);
        when(gateCp.getName()).thenReturn("Main gate");
        Checkpoint yardCp = mock(Checkpoint.class);
        when(yardCp.getName()).thenReturn("Yard");
        when(checkpoints.findById(gate)).thenReturn(Optional.of(gateCp));
        when(checkpoints.findById(yard)).thenReturn(Optional.of(yardCp));

        CheckpointLog scan = mock(CheckpointLog.class);
        when(scan.getCheckpointId()).thenReturn(gate);
        when(logs.findByRound(TENANT, roundId)).thenReturn(List.of(scan));

        List<RoundView> out = service.myRounds(TENANT, me);

        assertThat(out).hasSize(1);
        var cps = out.get(0).checkpoints();
        assertThat(cps).extracting("name").containsExactly("Main gate", "Yard");
        assertThat(cps).extracting("scanned").containsExactly(true, false);
        assertThat(out.get(0).checkpointsScanned()).isEqualTo(1);
    }
}
