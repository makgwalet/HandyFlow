// security/application/internal/GuardMyWorkService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.RoundCheckpoint;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.RoundView;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.ShiftView;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The guard's own roster and tonight's patrol, for the guard app. The guard is always the token's guard, passed in by the
 * controller; nothing here takes a guard id from the request.
 */
@Service
@RequiredArgsConstructor
public class GuardMyWorkService {

    /** A shift that ended up to 12 hours ago still shows, so a guard running late can see it. */
    static final Duration LOOK_BACK = Duration.ofHours(12);
    static final Duration LOOK_AHEAD = Duration.ofDays(7);

    private final ShiftRepository shifts;
    private final SiteRepository sites;
    private final DeviceSessionRepository sessions;
    private final PatrolRoundService rounds;
    private final PatrolRouteRepository routes;
    private final CheckpointRepository checkpoints;
    private final CheckpointLogRepository logs;

    @Transactional(readOnly = true)
    public List<ShiftView> myShifts(TenantId tenantId, UUID guardId) {
        Instant now = Instant.now();
        List<Shift> mine = shifts.findForGuardBetween(tenantId, guardId, now.minus(LOOK_BACK), now.plus(LOOK_AHEAD));
        Map<UUID, String> siteNames = new HashMap<>();
        return mine.stream().map(s -> new ShiftView(
                s.getId(), s.getSiteId(),
                siteNames.computeIfAbsent(s.getSiteId(), id -> sites.findActiveById(tenantId, id).map(Site::getName).orElse("Unknown site")),
                s.getStartAt(), s.getEndAt(), String.valueOf(s.getStatus()), s.getActualStartAt())).toList();
    }

    /** Rounds of the shift on the guard's open session, each with its checkpoints in order and which are done. */
    @Transactional(readOnly = true)
    public List<RoundView> myRounds(TenantId tenantId, UUID guardId) {
        DeviceSession session = sessions.findOpenByGuard(guardId).orElseThrow(() -> new HandyFlowException(
                "No open session. Clock in to see tonight's patrol.", HttpStatus.CONFLICT, "NO_OPEN_SESSION"));
        if (session.getShiftId() == null) {
            throw new HandyFlowException("Your session has no linked shift, so there are no patrol rounds.",
                    HttpStatus.CONFLICT, "NO_SHIFT");
        }
        return rounds.getRoundsForShift(session.getShiftId()).stream()
                .sorted(Comparator.comparing(PatrolRound::getExpectedStartAt))
                .map(r -> view(tenantId, r)).toList();
    }

    private RoundView view(TenantId tenantId, PatrolRound r) {
        Set<UUID> done = new HashSet<>();
        for (CheckpointLog l : logs.findByRound(tenantId, r.getId())) done.add(l.getCheckpointId());

        List<RoundCheckpoint> list = List.of();
        if (r.getRouteId() != null) {
            PatrolRoute route = routes.findById(r.getRouteId()).orElse(null);
            if (route != null) {
                list = route.getCheckpoints().stream()
                        .sorted(Comparator.comparingInt(PatrolRouteCheckpoint::getSequence))
                        .map(rc -> new RoundCheckpoint(rc.getCheckpointId(),
                                checkpoints.findById(rc.getCheckpointId()).map(Checkpoint::getName).orElse("Checkpoint"),
                                rc.getSequence(), done.contains(rc.getCheckpointId())))
                        .toList();
            }
        }
        return new RoundView(r.getId(), r.getRoundNumber(), String.valueOf(r.getStatus()), r.getExpectedStartAt(),
                r.getExpectedEndAt(), r.getStartedAt(), r.getCompletedAt(), r.getScansExpected(), r.getScansCompleted(), list);
    }
}
