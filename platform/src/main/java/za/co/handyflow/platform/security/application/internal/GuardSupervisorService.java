package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.domain.model.ShiftSwapRequest;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftSwapRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.security.dto.ShiftSwapResponse;
import za.co.handyflow.platform.security.dto.SwapActionRequest;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.OnDutyGuard;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.OpenIncident;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.SiteOverview;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-only overview for a guard a manager has made a site supervisor. The site is the one the supervisor is on shift at
 * (from their own open session, never a parameter), the flag is read from the database on every call, and nothing here
 * changes anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardSupervisorService {

    static final int MAX_INCIDENTS = 20;
    static final Duration INCIDENT_LOOKBACK = Duration.ofHours(24);

    private final GuardRepository guardRepository;
    private final DeviceSessionService deviceSessionService;
    private final LiveOperationsService liveOperationsService;
    private final PatrolRoundRepository roundRepository;
    private final IncidentRepository incidentRepository;
    private final SiteRepository siteRepository;
    private final ShiftSwapService swapService;
    private final ShiftSwapRepository swapRepository;
    private final ShiftRepository shiftRepository;
    private final IncidentService incidentService;

    /** Manager action: make a guard a site supervisor, or take it away. */
    @Transactional
    public void setSiteSupervisor(TenantId tenantId, UUID guardId, boolean enabled) {
        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        guard.setSiteSupervisor(enabled);
        guardRepository.save(guard);
        log.info("[Security] Site supervisor {} for guard={} tenant={}", enabled ? "ENABLED" : "disabled", guardId, tenantId.getValue());
    }

    /** The caller must be an active site supervisor with an open shift session. Returns the site they are on shift at. */
    private Guard requireSupervisor(TenantId tenantId, UUID guardId) {
        Guard me = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        if (!me.isSiteSupervisor()) {
            throw new HandyFlowException("You are not set up as a site supervisor. Ask your manager.",
                    HttpStatus.FORBIDDEN, "NOT_SITE_SUPERVISOR");
        }
        return me;
    }

    /** Swaps at my site that a colleague has agreed to and that now wait for a supervisor, oldest first. */
    @Transactional(readOnly = true)
    public List<ShiftSwapResponse> swapsToDecide(TenantId tenantId, UUID guardId) {
        requireSupervisor(tenantId, guardId);
        UUID siteId = deviceSessionService.resolveCurrentSiteForGuard(guardId);
        return swapService.getPendingSwaps(tenantId, org.springframework.data.domain.PageRequest.of(0, 100)).getContent().stream()
                .filter(w -> "PROPOSED_ACCEPTED".equals(w.status()))
                .filter(w -> shiftSiteOf(tenantId, w.originalShiftId()).map(siteId::equals).orElse(false))
                .filter(w -> !guardId.equals(w.requestingGuardId()) && !guardId.equals(w.proposedGuardId()))
                .toList();
    }

    /** Approve or refuse a swap at my site. Not my own swap, and only one a colleague has already agreed to. */
    @Transactional
    public ShiftSwapResponse decideSwap(TenantId tenantId, UUID guardId, UUID swapId, boolean approve, String reason) {
        requireSupervisor(tenantId, guardId);
        UUID siteId = deviceSessionService.resolveCurrentSiteForGuard(guardId);
        ShiftSwapRequest swap = swapRepository.findByTenantAndId(tenantId, swapId)
                .filter(w -> shiftSiteOf(tenantId, w.getOriginalShiftId()).map(siteId::equals).orElse(false))
                .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", swapId.toString()));
        if (guardId.equals(swap.getRequestingGuardId()) || guardId.equals(swap.getProposedGuardId())) {
            throw new HandyFlowException("You cannot decide a swap you are part of.", HttpStatus.FORBIDDEN, "CANNOT_DECIDE_OWN_SWAP");
        }
        String status = String.valueOf(swap.getStatus());
        boolean decidable = approve ? "PROPOSED_ACCEPTED".equals(status) : ("PENDING".equals(status) || "PROPOSED_ACCEPTED".equals(status));
        if (!decidable) {
            throw new HandyFlowException("This swap is " + status.toLowerCase().replace('_', ' ') + " and cannot be decided now.",
                    HttpStatus.CONFLICT, "SWAP_NOT_AWAITING_DECISION");
        }
        SwapActionRequest body = new SwapActionRequest(reason);
        return approve ? swapService.approveSwap(tenantId, swapId, guardId, body) : swapService.rejectSwap(tenantId, swapId, guardId, body);
    }

    @Transactional
    public IncidentResponse acknowledgeIncident(TenantId tenantId, UUID guardId, UUID incidentId) {
        Guard me = requireSupervisor(tenantId, guardId);
        requireIncidentAtMySite(tenantId, guardId, incidentId);
        return incidentService.acknowledge(tenantId, incidentId, guardId, me.getFullName());
    }

    @Transactional
    public IncidentResponse resolveIncident(TenantId tenantId, UUID guardId, UUID incidentId, String note) {
        Guard me = requireSupervisor(tenantId, guardId);
        requireIncidentAtMySite(tenantId, guardId, incidentId);
        return incidentService.resolve(tenantId, incidentId, guardId, me.getFullName(), note);
    }

    private void requireIncidentAtMySite(TenantId tenantId, UUID guardId, UUID incidentId) {
        UUID siteId = deviceSessionService.resolveCurrentSiteForGuard(guardId);
        incidentRepository.findByIdAndTenantId(incidentId, tenantId)
                .filter(i -> siteId.equals(i.getSiteId()))
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId.toString()));
    }

    private java.util.Optional<UUID> shiftSiteOf(TenantId tenantId, UUID shiftId) {
        return shiftRepository.findByTenantAndId(tenantId, shiftId).map(s -> s.getSiteId());
    }

    @Transactional(readOnly = true)
    public SiteOverview siteOverview(TenantId tenantId, UUID guardId) {
        requireSupervisor(tenantId, guardId);
        UUID siteId = deviceSessionService.resolveCurrentSiteForGuard(guardId);
        Instant now = Instant.now();

        List<OnDutyGuard> onDuty = liveOperationsService.liveGuards(tenantId, siteId).stream()
                .map(this::toOnDuty).toList();
        List<OpenIncident> incidents = incidentRepository
                .findBySiteInRange(tenantId, siteId, now.minus(INCIDENT_LOOKBACK), now.plusSeconds(60)).stream()
                .filter(i -> !"RESOLVED".equals(i.getStatus()))
                .sorted(Comparator.comparing(i -> i.getCreatedAt(), Comparator.reverseOrder()))
                .limit(MAX_INCIDENTS)
                .map(i -> new OpenIncident(i.getId(), i.getTitle(), i.getSeverity(), i.getStatus(), i.getCreatedAt()))
                .toList();
        String siteName = siteRepository.findActiveById(tenantId, siteId).map(s -> s.getName()).orElse("Your site");
        return new SiteOverview(siteId, siteName, now, onDuty, incidents);
    }

    private OnDutyGuard toOnDuty(LiveGuardResponse g) {
        List<PatrolRound> rounds = roundRepository.findByShift(g.shiftId());
        int complete = (int) rounds.stream().filter(r -> r.getStatus() == PatrolRound.RoundStatus.COMPLETE).count();
        int missed = (int) rounds.stream().filter(r -> r.getStatus() == PatrolRound.RoundStatus.MISSED).count();
        return new OnDutyGuard(g.guardId(), g.guardName(), g.shiftStart(), g.shiftEnd(), g.overrunning(), g.gpsState(),
                g.lastScanAt(), g.lastScanCheckpoint(), rounds.size(), complete, missed);
    }
}
