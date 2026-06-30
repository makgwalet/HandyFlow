// security/application/internal/PatrolRoundService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PatrolRoundService — generates and manages patrol rounds for shifts.
 *
 * Two responsibilities:
 *
 * 1. GENERATION (called when a shift goes ACTIVE):
 *    For each PatrolRoute configured for the site, generate PatrolRound rows
 *    representing the expected patrol windows for the entire shift duration.
 *    e.g. 12-hour shift, 120-minute interval → 6 rounds generated upfront.
 *
 * 2. SCAN ROUTING (called from CheckpointScanService):
 *    When a guard scans a checkpoint, find the active PatrolRound and
 *    link the scan to it.  Detect OFF_SCHEDULE rounds (scan arrived too
 *    early relative to the previous round's completion).
 *
 * 3. MISSED ROUND DETECTION (scheduler, every 5 minutes):
 *    Rounds whose expectedEndAt has passed and status is still EXPECTED
 *    get marked MISSED and trigger a supervisor alert.
 *
 * WHY generate rounds upfront rather than on-demand?
 * The supervisor dashboard needs to show "Round 3 of 6 — due at 12:00"
 * before the guard starts it.  An on-demand model would only show the
 * round after the guard starts scanning.  Upfront generation also lets
 * the MISSED detection scheduler work without scanning history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatrolRoundService {

    private final PatrolRouteRepository    routeRepository;
    private final PatrolRoundRepository    roundRepository;
    private final CheckpointLogRepository  logRepository;
    private final SiteRepository           siteRepository;

    // ── Round Generation ───────────────────────────────────────────────────────

    /**
     * Generates PatrolRound rows for a newly-started shift.
     * Called by DeviceSessionService.openSession() after shift.start().
     */
    @Transactional
    public List<PatrolRound> generateRoundsForShift(TenantId tenantId, Shift shift) {
        List<PatrolRoute> routes = routeRepository.findActiveBySite(
                tenantId, shift.getSiteId());

        if (routes.isEmpty()) {
            log.debug("[Security] No patrol routes for site={} — no rounds generated",
                    shift.getSiteId());
            return List.of();
        }

        List<PatrolRound> generated = new ArrayList<>();
        long shiftMinutes = Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes();

        for (PatrolRoute route : routes) {
            int expectedRounds = route.expectedRoundsForShift(shiftMinutes);
            if (expectedRounds <= 0) continue;

            int checkpointCount = route.getCheckpoints().size();

            for (int i = 1; i <= expectedRounds; i++) {
                Instant expectedStart = shift.getStartAt()
                        .plus((long) route.getIntervalMinutes() * (i - 1), ChronoUnit.MINUTES);
                Instant expectedEnd = expectedStart
                        .plus(route.getIntervalMinutes(), ChronoUnit.MINUTES)
                        .minus(route.getToleranceMinutes(), ChronoUnit.MINUTES);

                PatrolRound round = PatrolRound.create(
                        tenantId, shift.getSiteId(), shift.getId(),
                        route.getId(), i, expectedStart, expectedEnd,
                        checkpointCount);
                generated.add(roundRepository.save(round));
            }

            log.info("[Security] Generated {} rounds for route={} shift={}",
                    expectedRounds, route.getId(), shift.getId());
        }

        return generated;
    }

    // ── Scan Routing ───────────────────────────────────────────────────────────

    /**
     * Links a checkpoint scan to the appropriate patrol round and detects
     * OFF_SCHEDULE scans (Part 6.6 — front-loading fraud detection).
     *
     * Called from CheckpointScanService after a scan is validated and saved.
     * Returns the matched round ID (null if no route is configured for the site).
     *
     * OFF_SCHEDULE logic:
     *   If the previous round was completed less than (intervalMinutes - toleranceMinutes)
     *   ago, the new round started too early — flag it OFF_SCHEDULE.
     *   This catches front-loading: all 6 rounds completed in the first 90 minutes
     *   of a 12-hour shift would have rounds 2-6 flagged OFF_SCHEDULE.
     */
    @Transactional
    public Optional<UUID> routeScanToRound(UUID shiftId, UUID checkpointId) {
        Optional<PatrolRound> currentRound = roundRepository.findCurrentRound(shiftId);
        if (currentRound.isEmpty()) return Optional.empty();

        PatrolRound round = currentRound.get();

        // OFF_SCHEDULE check: did this round start too soon after the previous one?
        boolean isOffSchedule = false;
        String offScheduleReason = null;

        if (round.getRoundNumber() > 1) {
            // Find previous round completion time
            List<PatrolRound> allRounds = roundRepository.findByShift(shiftId);
            Optional<PatrolRound> prevRound = allRounds.stream()
                    .filter(r -> r.getRoundNumber() == round.getRoundNumber() - 1)
                    .findFirst();

            if (prevRound.isPresent() && prevRound.get().getCompletedAt() != null) {
                long minutesSincePrev = ChronoUnit.MINUTES.between(
                        prevRound.get().getCompletedAt(), Instant.now());

                // Fetch route interval — approximated from expectedStartAt diff
                long expectedInterval = ChronoUnit.MINUTES.between(
                        prevRound.get().getExpectedStartAt(),
                        round.getExpectedStartAt());
                long minimumInterval = expectedInterval - 20; // tolerance

                if (minutesSincePrev < minimumInterval) {
                    isOffSchedule = true;
                    offScheduleReason = String.format(
                            "Round %d started %d min after round %d (minimum: %d min)",
                            round.getRoundNumber(), minutesSincePrev,
                            prevRound.get().getRoundNumber(), minimumInterval);
                    log.warn("[Security] OFF_SCHEDULE round detected shiftId={} round={}: {}",
                            shiftId, round.getRoundNumber(), offScheduleReason);
                }
            }
        }

        round.recordScan(isOffSchedule, offScheduleReason);
        roundRepository.save(round);

        return Optional.of(round.getId());
    }

    // ── Missed Round Detection (scheduler) ─────────────────────────────────────

    /**
     * Runs every 5 minutes — marks overdue EXPECTED rounds as MISSED.
     * Same cron as NoShowAlertScheduler (co-located in the same 5-minute window).
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void detectMissedRounds() {
        // Get all tenants with active sites
        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();

        int totalMissed = 0;
        for (UUID tenantId : tenantIds) {
            List<PatrolRound> overdue = roundRepository.findOverdueExpected(
                    TenantId.of(tenantId));
            for (PatrolRound round : overdue) {
                round.markMissed();
                roundRepository.save(round);
                totalMissed++;
                log.warn("[Security] MISSED round shiftId={} round={} expectedStart={}",
                        round.getShiftId(), round.getRoundNumber(), round.getExpectedStartAt());
            }
        }

        if (totalMissed > 0) {
            log.info("[Security] Marked {} rounds as MISSED", totalMissed);
        }
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PatrolRound> getRoundsForShift(UUID shiftId) {
        return roundRepository.findByShift(shiftId);
    }

    // ── Route CRUD ─────────────────────────────────────────────────────────────

    @Transactional
    public PatrolRoute createRoute(TenantId tenantId, UUID siteId, String name,
                                   int intervalMinutes, int toleranceMinutes) {
        PatrolRoute route = PatrolRoute.create(tenantId, siteId, name,
                intervalMinutes, toleranceMinutes);
        return routeRepository.save(route);
    }

    @Transactional
    public void addCheckpointToRoute(UUID routeId, UUID checkpointId, int sequence) {
        PatrolRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new za.co.handyflow.platform.shared
                        .ResourceNotFoundException("PatrolRoute", routeId.toString()));
        PatrolRouteCheckpoint rcp = PatrolRouteCheckpoint.create(
                routeId, checkpointId, sequence);
        route.getCheckpoints().add(rcp);
        routeRepository.save(route);
    }

    @Transactional
    public List<PatrolRoute> getRoutesForSite(TenantId tenantId, UUID siteId) {
        return routeRepository.findActiveBySite(tenantId, siteId);
    }
}