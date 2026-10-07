package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.model.PatrolRoute;
import za.co.handyflow.platform.security.domain.model.PatrolRouteCheckpoint;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
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
    private final CheckpointRepository checkpointRepo = mock(CheckpointRepository.class);
    private final PatrolRoundService service = new PatrolRoundService(routes, rounds, logs, mock(SiteRepository.class), checkpointRepo);

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

    // ── Off-schedule check is per route and uses the route's own tolerance ─────────────────────────────

    private PatrolRound roundAt(UUID routeId, int number, Instant start) {
        return PatrolRound.create(TENANT, site, shift, routeId, number, start, start.plusSeconds(3000), 1);
    }

    @Test
    void startingARoundTooSoonAfterThePreviousRoundOfTheSameRouteIsOffSchedule() {
        UUID routeId = UUID.randomUUID(); PatrolRoute route = routeWith(routeId, cpA);
        when(route.getToleranceMinutes()).thenReturn(10);
        PatrolRound r1 = roundAt(routeId, 1, Instant.now().minusSeconds(600));
        PatrolRound r2 = roundAt(routeId, 2, Instant.now().plusSeconds(3000));
        r1.recordScan(false, null); // complete, just now
        when(rounds.findByShift(shift)).thenReturn(List.of(r1, r2));

        service.routeScanToRound(shift, cpA);
        assertThat(r2.isOffSchedule()).isTrue();
        assertThat(r2.getOffScheduleReason()).contains("Round 2 started").contains("after round 1");
    }

    @Test
    void aRoundOfAnotherRouteIsNotTheirPreviousRound() {
        UUID routeA = UUID.randomUUID(), routeB = UUID.randomUUID();
        routeWith(routeA, cpA); routeWith(routeB, cpB);
        PatrolRound a1 = roundAt(routeA, 1, Instant.now().minusSeconds(600));
        a1.recordScan(false, null); // route A round 1 completed just now
        PatrolRound b2 = roundAt(routeB, 2, Instant.now().plusSeconds(3000)); // route B has no round 1 here
        when(rounds.findByShift(shift)).thenReturn(List.of(a1, b2));

        service.routeScanToRound(shift, cpB);
        assertThat(b2.isOffSchedule()).isFalse();
    }

    @Test
    void theRoutesToleranceDecidesHowEarlyIsTooEarly() {
        UUID routeId = UUID.randomUUID(); PatrolRoute route = routeWith(routeId, cpA);
        when(route.getToleranceMinutes()).thenReturn(120); // wider than the 70 minute interval, so nothing is too early
        PatrolRound r1 = roundAt(routeId, 1, Instant.now().minusSeconds(600));
        PatrolRound r2 = roundAt(routeId, 2, Instant.now().plusSeconds(3000));
        r1.recordScan(false, null);
        when(rounds.findByShift(shift)).thenReturn(List.of(r1, r2));

        service.routeScanToRound(shift, cpA);
        assertThat(r2.isOffSchedule()).isFalse();
    }

    @Test
    void theMinimumGapIsTheIntervalLessTheToleranceAndNeverNegative() {
        assertThat(PatrolRoundService.minimumIntervalMinutes(60, 10)).isEqualTo(50);
        assertThat(PatrolRoundService.minimumIntervalMinutes(60, 90)).isZero();
    }

    // ── Generation: numbers run per route, and only active checkpoints count ──────────────────────────

    private Checkpoint checkpoint(boolean active) {
        Checkpoint c = Checkpoint.create(TENANT, mock(Site.class), "cp", null, 0);
        if (!active) c.updateDetails("cp", null, null, null, false);
        return c;
    }

    private PatrolRoute routeFor(UUID routeId, int interval, List<Checkpoint> cps) {
        PatrolRoute r = mock(PatrolRoute.class);
        when(r.getId()).thenReturn(routeId);
        when(r.getIntervalMinutes()).thenReturn(interval);
        when(r.getToleranceMinutes()).thenReturn(10);
        when(r.expectedRoundsForShift(480L)).thenReturn(480 / interval);
        List<PatrolRouteCheckpoint> list = new java.util.ArrayList<>();
        for (Checkpoint c : cps) { PatrolRouteCheckpoint p = mock(PatrolRouteCheckpoint.class); when(p.getCheckpointId()).thenReturn(c.getId()); list.add(p); }
        when(r.getCheckpoints()).thenReturn(list);
        when(checkpointRepo.findAllById(cps.stream().map(Checkpoint::getId).toList())).thenReturn(cps);
        return r;
    }

    @Test
    void twoActiveRoutesAtOneSiteEachGetRoundsNumberedFromOne() {
        Shift sh = mock(Shift.class);
        Instant start = Instant.parse("2026-10-07T06:00:00Z");
        when(sh.getId()).thenReturn(shift); when(sh.getSiteId()).thenReturn(site);
        when(sh.getStartAt()).thenReturn(start); when(sh.getEndAt()).thenReturn(start.plusSeconds(8 * 3600));
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID();
        PatrolRoute a = routeFor(r1, 120, List.of(checkpoint(true), checkpoint(true)));
        PatrolRoute b = routeFor(r2, 240, List.of(checkpoint(true)));
        when(routes.findActiveBySite(TENANT, site)).thenReturn(List.of(a, b));
        when(rounds.save(any(PatrolRound.class))).thenAnswer(i -> i.getArgument(0));

        List<PatrolRound> made = service.generateRoundsForShift(TENANT, sh);

        assertThat(made).hasSize(4 + 2);
        assertThat(made.stream().filter(r -> r1.equals(r.getRouteId())).map(PatrolRound::getRoundNumber)).containsExactly(1, 2, 3, 4);
        assertThat(made.stream().filter(r -> r2.equals(r.getRouteId())).map(PatrolRound::getRoundNumber)).containsExactly(1, 2);
    }

    @Test
    void aSwitchedOffCheckpointDoesNotCountTowardsARoundAndARouteWithNoneIsSkipped() {
        Shift sh = mock(Shift.class);
        Instant start = Instant.parse("2026-10-07T06:00:00Z");
        when(sh.getId()).thenReturn(shift); when(sh.getSiteId()).thenReturn(site);
        when(sh.getStartAt()).thenReturn(start); when(sh.getEndAt()).thenReturn(start.plusSeconds(8 * 3600));
        PatrolRoute partlyOff = routeFor(UUID.randomUUID(), 240, List.of(checkpoint(true), checkpoint(true), checkpoint(false)));
        PatrolRoute allOff = routeFor(UUID.randomUUID(), 240, List.of(checkpoint(false)));
        when(routes.findActiveBySite(TENANT, site)).thenReturn(List.of(partlyOff, allOff));
        when(rounds.save(any(PatrolRound.class))).thenAnswer(i -> i.getArgument(0));

        List<PatrolRound> made = service.generateRoundsForShift(TENANT, sh);

        assertThat(made).hasSize(2);
        assertThat(made).allSatisfy(r -> assertThat(r.getScansExpected()).isEqualTo(2));
    }
}
