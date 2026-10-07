package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.PatrolRoute;
import za.co.handyflow.platform.security.domain.model.PatrolRouteCheckpoint;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRouteRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Scan routing: which round a scan counts towards, and that a repeat scan or an off-route scan does not count. */
class PatrolRoundServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final PatrolRouteRepository routes = mock(PatrolRouteRepository.class);
    private final PatrolRoundRepository rounds = mock(PatrolRoundRepository.class);
    private final CheckpointLogRepository logs = mock(CheckpointLogRepository.class);
    private final PatrolRoundService service = new PatrolRoundService(routes, rounds, logs, mock(SiteRepository.class));

    private final UUID shift = UUID.randomUUID(), site = UUID.randomUUID();
    private final UUID cpA = UUID.randomUUID(), cpB = UUID.randomUUID(), cpOther = UUID.randomUUID();

    private PatrolRoute routeWith(UUID routeId, UUID... cps) {
        PatrolRoute r = mock(PatrolRoute.class);
        when(r.getId()).thenReturn(routeId);
        List<PatrolRouteCheckpoint> list = new java.util.ArrayList<>();
        for (UUID c : cps) { PatrolRouteCheckpoint p = mock(PatrolRouteCheckpoint.class); when(p.getCheckpointId()).thenReturn(c); list.add(p); }
        when(r.getCheckpoints()).thenReturn(list);
        when(routes.findById(routeId)).thenReturn(Optional.of(r));
        return r;
    }

    private PatrolRound round(UUID routeId, int number, int expected) {
        return PatrolRound.create(TENANT, site, shift, routeId, number, Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600), expected);
    }

    @Test
    void aScanOnTheRoutesCheckpointStartsAndCountsTowardsTheRound() {
        UUID routeId = UUID.randomUUID(); routeWith(routeId, cpA, cpB);
        PatrolRound r = round(routeId, 1, 2);
        when(rounds.findByShift(shift)).thenReturn(List.of(r));

        assertThat(service.routeScanToRound(shift, cpA)).contains(r.getId());
        assertThat(r.getScansCompleted()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(PatrolRound.RoundStatus.IN_PROGRESS);
        verify(rounds).save(r);
    }

    @Test
    void scanningEveryCheckpointCompletesTheRound() {
        UUID routeId = UUID.randomUUID(); routeWith(routeId, cpA, cpB);
        PatrolRound r = round(routeId, 1, 2);
        when(rounds.findByShift(shift)).thenReturn(List.of(r));

        service.routeScanToRound(shift, cpA);
        service.routeScanToRound(shift, cpB);
        assertThat(r.getStatus()).isEqualTo(PatrolRound.RoundStatus.COMPLETE);
        assertThat(r.getCompletedAt()).isNotNull();
    }

    @Test
    void aRepeatScanOfTheSameCheckpointIsLinkedButNotCounted() {
        UUID routeId = UUID.randomUUID(); routeWith(routeId, cpA, cpB);
        PatrolRound r = round(routeId, 1, 2);
        when(rounds.findByShift(shift)).thenReturn(List.of(r));
        when(logs.existsByRoundIdAndCheckpointId(r.getId(), cpA)).thenReturn(true);

        assertThat(service.routeScanToRound(shift, cpA)).contains(r.getId());
        assertThat(r.getScansCompleted()).isZero();
        assertThat(r.getStatus()).isEqualTo(PatrolRound.RoundStatus.EXPECTED);
        verify(rounds, never()).save(any());
    }

    @Test
    void aCheckpointOnNoOpenRouteBelongsToNoRound() {
        UUID routeId = UUID.randomUUID(); routeWith(routeId, cpA, cpB);
        PatrolRound r = round(routeId, 1, 2);
        when(rounds.findByShift(shift)).thenReturn(List.of(r));

        assertThat(service.routeScanToRound(shift, cpOther)).isEmpty();
        assertThat(r.getScansCompleted()).isZero();
    }

    @Test
    void withTwoRoutesTheScanGoesToTheRoundOfTheRouteThatHasTheCheckpoint() {
        UUID routeA = UUID.randomUUID(), routeB = UUID.randomUUID();
        routeWith(routeA, cpA); routeWith(routeB, cpB);
        PatrolRound ra = round(routeA, 1, 1), rb = round(routeB, 1, 1);
        when(rounds.findByShift(shift)).thenReturn(List.of(ra, rb));

        assertThat(service.routeScanToRound(shift, cpB)).contains(rb.getId());
        assertThat(ra.getScansCompleted()).isZero();
        assertThat(rb.getStatus()).isEqualTo(PatrolRound.RoundStatus.COMPLETE);
    }

    @Test
    void finishedRoundsAreSkippedAndNoOpenRoundMeansNoRound() {
        UUID routeId = UUID.randomUUID(); routeWith(routeId, cpA);
        PatrolRound done = round(routeId, 1, 1);
        done.recordScan(false, null); // complete
        when(rounds.findByShift(shift)).thenReturn(List.of(done));
        assertThat(service.routeScanToRound(shift, cpA)).isEmpty();

        when(rounds.findByShift(shift)).thenReturn(List.of());
        assertThat(service.routeScanToRound(shift, cpA)).isEmpty();
    }
}
