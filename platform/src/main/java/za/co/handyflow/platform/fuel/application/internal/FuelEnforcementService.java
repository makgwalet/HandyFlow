package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelEnforcement;
import za.co.handyflow.platform.fuel.domain.model.FuelEnforcementObservation;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalPolicyRequest;
import za.co.handyflow.platform.fuel.dto.EnforcementDtos.*;
import za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicyRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Rollout of the arrival check and the POD policy: OFF, MONITOR (watch and record what would have been refused), PILOT (enforce at chosen
 * depots, watch elsewhere) and ENFORCED. A step up needs the previous stage to have run for a while, a scope, a reason, and an acceptable
 * would-have-refused rate; going back down is always allowed. Every change is audited. Completed deliveries are never re-judged.
 * The older boolean switches are kept in step: they are true exactly when the state is ENFORCED.
 */
@Service
@RequiredArgsConstructor
public class FuelEnforcementService {

    public enum Mode { OFF, MONITOR, ENFORCE }

    /** The stage before must have run this long before the next one starts. */
    static final Duration MIN_STAGE = Duration.ofDays(7);
    /** Enforcing is blocked while more than this share of the last week's deliveries would have been refused. */
    static final double MAX_REFUSAL_SHARE = 0.05;

    private final FuelEnforcementRepository states;
    private final FuelEnforcementObservationRepository observations;
    private final FuelDeliveryRepository deliveries;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final FuelSettingsService settings;
    private final AuditTrailFacade audit;

    /** What a control does to this delivery now. A tank in no pilot depot is only watched. */
    @Transactional(readOnly = true)
    public Mode mode(TenantId tenantId, String control, UUID tankId) {
        FuelEnforcement e = states.find(tenantId, control).orElse(null);
        if (e == null) return Mode.OFF;
        return switch (e.getState()) {
            case "ENFORCED" -> Mode.ENFORCE;
            case "MONITOR" -> Mode.MONITOR;
            case "PILOT" -> {
                UUID depot = tankId == null ? null : tanks.findActiveById(tenantId, tankId).map(t -> t.getDepotId()).orElse(null);
                yield depot != null && e.pilotDepots().contains(depot) ? Mode.ENFORCE : Mode.MONITOR;
            }
            default -> Mode.OFF;
        };
    }

    @Transactional
    public void observe(TenantId tenantId, String control, UUID deliveryId, String message, Instant now) {
        observations.save(FuelEnforcementObservation.of(tenantId, control, deliveryId, message, now));
    }

    @Transactional(readOnly = true)
    public List<ControlState> list(TenantId tenantId) { return list(tenantId, Instant.now()); }

    List<ControlState> list(TenantId tenantId, Instant now) {
        List<ControlState> out = new ArrayList<>();
        for (String c : List.of(FuelEnforcement.ARRIVAL_CHECK, FuelEnforcement.POD_POLICY)) out.add(view(tenantId, c, states.find(tenantId, c).orElse(null), now));
        return out;
    }

    private ControlState view(TenantId t, String control, FuelEnforcement e, Instant now) {
        String state = e == null ? "OFF" : e.getState();
        int r = FuelEnforcement.rank(state);
        List<String> next = new ArrayList<>(FuelEnforcement.ORDER.subList(0, r));
        if (r < FuelEnforcement.ORDER.size() - 1) next.add(FuelEnforcement.ORDER.get(r + 1));
        if ("PILOT".equals(state)) next.add("PILOT");
        return new ControlState(control, state, e == null ? List.of() : new ArrayList<>(e.pilotDepots()), e == null ? null : e.getReason(), e == null ? null : e.getStateSince(),
                e == null ? 0 : e.getVersionNo(), e == null ? null : e.getChangedBy(), observations.countSince(t, control, now.minus(Duration.ofDays(7))), next);
    }

    @Transactional(readOnly = true)
    public Blockers blockers(TenantId tenantId, String control, String target, Collection<UUID> depotIds, String reason) {
        return blockers(tenantId, control, target, depotIds, reason, Instant.now());
    }

    Blockers blockers(TenantId tenantId, String control, String target, Collection<UUID> depotIds, String reason, Instant now) {
        List<String> b = new ArrayList<>();
        if (!FuelEnforcement.CONTROLS.contains(control)) { b.add("Unknown control " + control + "."); return new Blockers(control, target, false, b); }
        int to = FuelEnforcement.rank(target);
        if (to < 0) { b.add("The state must be one of OFF, MONITOR, PILOT or ENFORCED."); return new Blockers(control, target, false, b); }
        FuelEnforcement cur = states.find(tenantId, control).orElse(null);
        String from = cur == null ? "OFF" : cur.getState();
        int fromRank = FuelEnforcement.rank(from);
        if (reason == null || reason.trim().length() < 5) b.add("Give a reason for the change (at least 5 characters).");
        if (to > fromRank) {
            if (to != fromRank + 1) b.add("Move to " + FuelEnforcement.ORDER.get(fromRank + 1) + " first; a control is rolled out one stage at a time.");
            else {
                if (fromRank >= 1 && cur != null && cur.getStateSince().isAfter(now.minus(MIN_STAGE)))
                    b.add(from + " has run since " + cur.getStateSince() + "; it needs at least " + MIN_STAGE.toDays() + " days before the next stage.");
                if ("ENFORCED".equals(target)) {
                    Instant since = now.minus(Duration.ofDays(7));
                    long seen = observations.countSince(tenantId, control, since), delivered = deliveries.countDeliveredSince(tenantId, since);
                    if (seen > 0 && (delivered == 0 || (double) seen / delivered > MAX_REFUSAL_SHARE))
                        b.add(seen + " deliveries would have been refused in the last 7 days (of " + delivered + " completed). Review them, or fix the cause, before enforcing.");
                }
            }
        }
        if ("PILOT".equals(target)) {
            if (depotIds == null || depotIds.isEmpty()) b.add("Choose the depots the pilot covers.");
            else for (UUID d : depotIds) if (depots.findActiveById(tenantId, d).isEmpty()) b.add("Depot " + d + " was not found.");
        }
        return new Blockers(control, target, b.isEmpty(), b);
    }

    @Transactional
    public ControlState change(TenantId tenantId, UUID userId, String control, ChangeRequest req) { return change(tenantId, userId, control, req, Instant.now()); }

    ControlState change(TenantId tenantId, UUID userId, String control, ChangeRequest req, Instant now) {
        Blockers b = blockers(tenantId, control, req.state(), req.pilotDepotIds(), req.reason(), now);
        if (!b.allowed()) throw new IllegalStateException("Cannot move " + control + " to " + req.state() + ": " + String.join(" ", b.blockers()));
        FuelEnforcement e = states.find(tenantId, control).orElseGet(() -> FuelEnforcement.off(tenantId, control));
        String before = e.getState();
        Set<UUID> beforeScope = e.pilotDepots();
        e.change(req.state(), req.pilotDepotIds(), req.reason(), userId, now);
        states.save(e);
        syncLegacySwitch(tenantId, control, "ENFORCED".equals(e.getState()));
        audit.record(tenantId, "FUEL_ENFORCEMENT_CHANGED", "FuelEnforcement", e.getId(),
                Map.of("control", control, "state", before, "pilotDepots", beforeScope.toString()),
                Map.of("control", control, "state", e.getState(), "pilotDepots", e.pilotDepots().toString(), "reason", String.valueOf(e.getReason()),
                        "changedBy", String.valueOf(userId), "effectiveFrom", now.toString(), "version", e.getVersionNo()));
        return view(tenantId, control, e, now);
    }

    @Transactional(readOnly = true)
    public List<Observation> recentObservations(TenantId tenantId, String control, Integer days) {
        int d = days == null ? 7 : Math.max(1, Math.min(90, days));
        return observations.recent(tenantId, control, Instant.now().minus(Duration.ofDays(d)), PageRequest.of(0, 100)).stream()
                .map(o -> new Observation(o.getDeliveryId(), o.getMessage(), o.getCreatedAt())).toList();
    }

    /** The older switches the rest of the code and the settings screen still read. */
    private void syncLegacySwitch(TenantId tenantId, String control, boolean enforced) {
        FuelSettings s = settings.current(tenantId);
        if (FuelEnforcement.ARRIVAL_CHECK.equals(control)) {
            if (s.isArrivalCheckEnforced() != enforced) settings.updateArrivalPolicy(tenantId, new ArrivalPolicyRequest(s.getArrivalCheckFreshHours(), enforced));
        } else if (s.isPodPolicyEnforced() != enforced) {
            settings.updatePodPolicy(tenantId, new PodPolicyRequest(enforced, s.allowedPodExceptions().stream().sorted().toList()));
        }
    }
}
