package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheck;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheckRelease;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.application.internal.FuelArrivalCheckPolicy.Status;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ReleaseRequest;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The driver's own arrival check: the same eligibility rules as allocation, run against the tanker, driver and compartment
 * split already on the load, and recorded with its time. It changes nothing on the load.
 *
 * <p>Freshness is decided here, on the server's clock, from the company's window (default 12 hours). When the company has
 * switched enforcement on, {@link #requireClear} refuses protected steps unless the check is fresh or released. A stale
 * check can be released by someone holding FUEL_ARRIVAL_CHECK_OVERRIDE; a blocked check cannot, and a release re-runs the
 * live checks so a lapsed statutory licence or a prohibited product combination is never released past.
 */
@Service
@RequiredArgsConstructor
public class FuelArrivalCheckService {

    private final FuelDeliveryRepository deliveries;
    private final FuelTankerRepository tankers;
    private final FuelTankerCompartmentRepository compartments;
    private final FuelDeliveryCompartmentRepository loadSplits;
    private final FuelArrivalCheckRepository checks;
    private final FuelEligibilityService eligibility;
    private final HrFacade hr;
    private final ObjectMapper mapper;
    private final FuelArrivalCheckReleaseRepository releases;
    private final FuelSettingsService settings;
    private final FuelComplianceService compliance;
    private final AuditTrailFacade audit;
    private final FuelPushEventService pushEvents;
    private final FuelAlertRecipientService alertRecipients;

    @Transactional
    public ArrivalCheckView check(TenantId tenantId, UUID userId, UUID deliveryId) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        if (!"SCHEDULED".equals(d.getStatus()) && !"IN_TRANSIT".equals(d.getStatus())) {
            throw new IllegalStateException("This load is " + d.getStatus().toLowerCase().replace('_', ' ') + ", so there is nothing to check.");
        }
        EligibilityResult r = evaluateLive(tenantId, d);
        FuelArrivalCheck saved = checks.save(FuelArrivalCheck.record(tenantId, d.getId(), userId, r.allowed(), toJson(r.findings())));
        if (!r.allowed()) tellBlocked(tenantId, d, userId, r);
        return view(tenantId, saved, r.findings(), Instant.now());
    }

    /**
     * A blocked check: the driver and the company's chosen people (topic ARRIVAL_BLOCKED, administrators by default) are told.
     * The same reasons on the same load are told once. The alert authorises nothing; the server checks again at each protected step.
     */
    private void tellBlocked(TenantId tenantId, FuelDelivery d, UUID driverUserId, EligibilityResult r) {
        try {
            String reasons = r.blocks().stream().map(Finding::code).sorted().collect(java.util.stream.Collectors.joining(","));
            String key = "ARRIVAL:" + d.getId() + ":" + reasons;
            pushEvents.publish(tenantId, FuelPushEventService.ARRIVAL_CHECK_BLOCKED, key, List.of(driverUserId), "Arrival check blocked",
                    "You may not offload this load. Open it for the reason.", Map.of("screen", "LoadDetail", "loadId", String.valueOf(d.getId())));
            List<UUID> ops = alertRecipients.recipientsFor(tenantId, FuelAlertRecipientService.ARRIVAL_BLOCKED).stream()
                    .map(za.co.handyflow.platform.notifications.application.Recipient::userId).filter(java.util.Objects::nonNull).filter(u -> !u.equals(driverUserId)).toList();
            pushEvents.publish(tenantId, FuelPushEventService.ARRIVAL_CHECK_BLOCKED, key, ops, "Arrival check blocked",
                    "A driver's arrival check was blocked" + (d.getVehicleReg() == null ? "." : " (" + d.getVehicleReg() + ")."), Map.of("screen", "Overview"));
        } catch (RuntimeException e) { /* an alert never fails the check */ }
    }

    /** Runs the eligibility rules now against the load's tanker, driver and compartment split. */
    private EligibilityResult evaluateLive(TenantId tenantId, FuelDelivery d) {
        if (d.getTankerId() == null) throw new IllegalStateException("No tanker is allocated to this load yet. Ask the office.");
        FuelTanker tanker = tankers.findActiveById(tenantId, d.getTankerId())
                .orElseThrow(() -> new ResourceNotFoundException("Tanker", d.getTankerId().toString()));
        List<CompartmentLoad> loads = loadSplits.findByDelivery(tenantId, d.getId()).stream()
                .map(s -> new CompartmentLoad(s.getCompartmentId(), s.getLitres())).toList();
        return eligibility.evaluate(tenantId, d, tanker, d.getDriverEmployeeId(), d.getDriverName(),
                compartments.findByTanker(tenantId, tanker.getId()), loads);
    }

    private int freshHours(TenantId tenantId) {
        Integer h = settings.current(tenantId).getArrivalCheckFreshHours();
        return h == null ? FuelArrivalCheckPolicy.DEFAULT_FRESH_HOURS : h;
    }

    /** The view of one stored check with its status as of {@code now}. */
    private ArrivalCheckView view(TenantId tenantId, FuelArrivalCheck c, List<Finding> findings, Instant now) {
        return view(tenantId, c, findings, now, null);
    }

    /** knownReleasedAt: a release made in this very call, so the answer does not depend on the query seeing the row just saved. */
    private ArrivalCheckView view(TenantId tenantId, FuelArrivalCheck c, List<Finding> findings, Instant now, Instant knownReleasedAt) {
        int hours = freshHours(tenantId);
        Instant releasedAt = knownReleasedAt != null ? knownReleasedAt
                : releases.forCheck(tenantId, c.getId(), PageRequest.of(0, 1)).stream().findFirst().map(FuelArrivalCheckRelease::getReleasedAt).orElse(null);
        Status st = FuelArrivalCheckPolicy.status(c.getCheckedAt(), c.isAllowed(), releasedAt, hours, now);
        return new ArrivalCheckView(c.getDeliveryId(), c.isAllowed(), findings, c.getCheckedAt(), c.getId(), st.name(), hours,
                FuelArrivalCheckPolicy.expiresAt(c.getCheckedAt(), releasedAt, hours), releasedAt);
    }

    private ArrivalCheckView missing(TenantId tenantId, UUID deliveryId) {
        return new ArrivalCheckView(deliveryId, false, List.of(), null, null, Status.MISSING.name(), freshHours(tenantId), null, null);
    }

    /** The status of a load's arrival check as of now, for the office or a protected step. Tenant-scoped; no ownership test. */
    @Transactional(readOnly = true)
    public ArrivalCheckView status(TenantId tenantId, UUID deliveryId) { return status(tenantId, deliveryId, Instant.now()); }

    ArrivalCheckView status(TenantId tenantId, UUID deliveryId, Instant now) {
        return checks.latest(tenantId, deliveryId, PageRequest.of(0, 1)).stream().findFirst()
                .map(c -> view(tenantId, c, fromJson(c.getFindings()), now)).orElseGet(() -> missing(tenantId, deliveryId));
    }

    /**
     * The gate on protected steps (offloading complete, final delivery record). Does nothing unless the company has switched
     * enforcement on. Otherwise the check must be fresh or released, and the live rules are run again so a licence that has
     * lapsed since the check also stops the step. A notification or the app's own view never substitutes for this.
     */
    @Transactional(readOnly = true)
    public void requireClear(TenantId tenantId, UUID deliveryId) { requireClear(tenantId, deliveryId, Instant.now()); }

    void requireClear(TenantId tenantId, UUID deliveryId, Instant now) {
        FuelSettings s = settings.current(tenantId);
        if (!s.isArrivalCheckEnforced()) return;
        ArrivalCheckView v = status(tenantId, deliveryId, now);
        Status st = Status.valueOf(v.status());
        if (!st.clear()) throw new IllegalStateException(switch (st) {
            case MISSING -> "Do the arrival check at the site before offloading. No check has been made on this load.";
            case BLOCKED -> "The arrival check found something that blocks this delivery. Ask the office; it cannot be released.";
            default -> "The arrival check is out of date (older than " + v.freshHours() + " hours). Check again, or ask a supervisor to release it.";
        });
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        requireLiveClear(tenantId, d);
    }

    /** Safety-critical failures: any blocking finding, or an expired statutory certificate, stops the step now whatever the stored check says. */
    private void requireLiveClear(TenantId tenantId, FuelDelivery d) {
        EligibilityResult live = evaluateLive(tenantId, d);
        if (!live.allowed()) throw new IllegalStateException("The arrival check no longer passes: " + live.blocks().get(0).message());
        compliance.requireCompliantForAllocation(tenantId, d.getTankerId(), d.getVehicleReg(), d.getDriverEmployeeId(), d.getDriverName());
    }

    /**
     * Release a stale check. Needs a reason code, an explanation and the approver (the caller; the controller requires
     * FUEL_ARRIVAL_CHECK_OVERRIDE). The original check and its findings are untouched. Refused when the latest check is not
     * stale (missing, blocked, still fresh, already released), when the approver is the person who made the check, and when the
     * live rules now block, so a safety-critical failure cannot be released past.
     */
    @Transactional
    public ArrivalCheckView release(TenantId tenantId, UUID approverUserId, UUID deliveryId, ReleaseRequest req) { return release(tenantId, approverUserId, deliveryId, req, Instant.now()); }

    ArrivalCheckView release(TenantId tenantId, UUID approverUserId, UUID deliveryId, ReleaseRequest req, Instant now) {
        if (approverUserId == null) throw new IllegalStateException("You are not signed in as a user.");
        if (req.reasonCode() == null || !FuelArrivalCheckPolicy.RELEASE_REASONS.contains(req.reasonCode()))
            throw new IllegalArgumentException("Choose a reason: " + String.join(", ", FuelArrivalCheckPolicy.RELEASE_REASONS) + ".");
        String why = req.explanation() == null ? "" : req.explanation().trim();
        if (why.length() < 10) throw new IllegalArgumentException("Explain the release in at least 10 characters.");
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        FuelArrivalCheck original = checks.latest(tenantId, deliveryId, PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No arrival check has been made on this load, so there is nothing to release."));
        ArrivalCheckView before = view(tenantId, original, fromJson(original.getFindings()), now);
        Status st = Status.valueOf(before.status());
        if (st == Status.BLOCKED) throw new IllegalStateException("The arrival check found something that blocks this delivery. A blocked check cannot be released.");
        if (st != Status.STALE) throw new IllegalStateException("Only a stale arrival check can be released. This one is " + st.name().toLowerCase() + ".");
        if (approverUserId.equals(original.getCheckedBy())) throw new IllegalStateException("The person who made the check cannot release it.");
        requireLiveClear(tenantId, d);
        FuelArrivalCheckRelease rel = releases.save(FuelArrivalCheckRelease.record(tenantId, original.getId(), deliveryId, req.reasonCode(), why, approverUserId, now));
        audit.record(tenantId, "FUEL_ARRIVAL_CHECK_RELEASED", "FuelArrivalCheck", original.getId(),
                Map.of("status", Status.STALE.name(), "checkedAt", original.getCheckedAt().toString()),
                Map.of("status", Status.RELEASED.name(), "reasonCode", req.reasonCode(), "explanation", why, "releaseId", rel.getId().toString(),
                        "deliveryId", deliveryId.toString(), "approver", approverUserId.toString(), "releasedAt", now.toString()));
        return view(tenantId, original, fromJson(original.getFindings()), now, rel.getReleasedAt());
    }

    /** The latest check on one of the driver's own loads, or empty if none has been made. */
    @Transactional(readOnly = true)
    public Optional<ArrivalCheckView> latest(TenantId tenantId, UUID userId, UUID deliveryId) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        return checks.latest(tenantId, d.getId(), PageRequest.of(0, 1)).stream().findFirst()
                .map(c -> view(tenantId, c, fromJson(c.getFindings()), Instant.now()));
    }

    /** Every check made on a load, newest first, for the office's view of the load. Read-only. */
    @Transactional(readOnly = true)
    public List<ArrivalCheckView> history(TenantId tenantId, UUID deliveryId) {
        return checks.latest(tenantId, deliveryId, PageRequest.of(0, 50)).stream()
                .map(c -> view(tenantId, c, fromJson(c.getFindings()), Instant.now())).toList();
    }

    private FuelDelivery ownLoad(TenantId tenantId, UUID userId, UUID deliveryId) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        // Same message as a missing load so a driver cannot probe other drivers' loads.
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return d;
    }

    private String toJson(List<Finding> findings) {
        try { return mapper.writeValueAsString(findings); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not record the check.", e); }
    }

    private List<Finding> fromJson(String json) {
        try { return mapper.readValue(json, new TypeReference<List<Finding>>() {}); } catch (JsonProcessingException e) { return List.of(); }
    }
}
