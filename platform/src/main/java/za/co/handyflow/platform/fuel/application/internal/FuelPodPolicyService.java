package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryEvidence;
import za.co.handyflow.platform.fuel.domain.model.FuelPodException;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPodExceptionRepository;
import za.co.handyflow.platform.fuel.dto.CompleteDeliveryRequest;
import za.co.handyflow.platform.fuel.dto.PodDtos.ApproveRequest;
import za.co.handyflow.platform.fuel.dto.PodDtos.PodExceptionView;
import za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicy;
import za.co.handyflow.platform.fuel.dto.PodDtos.PodPolicyRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proof of delivery "received by". An ordinary customer-site delivery needs a receiver name. The only ways round that are the
 * four authorised exceptions: an unattended site and an automated receiving site (both designated on the site itself), and an
 * approved after-hours or contactless delivery (approved first by someone holding FUEL_POD_EXCEPTION_APPROVE). Each needs the
 * company to allow that type, a reason, evidence already on the load, and writes an audit event. A driver cannot grant themselves
 * an exception. Nothing is enforced until the company switches the policy on (default off, so existing screens keep working).
 *
 * <p>Both completion paths (the driver's and the office's) go through {@link #complete}.
 */
@Service
@RequiredArgsConstructor
public class FuelPodPolicyService {

    static final Set<String> SITE_TYPES = Set.of("UNATTENDED_SITE", "AUTOMATED_RECEIVING");
    static final Set<String> APPROVED_TYPES = Set.of("AFTER_HOURS_APPROVED", "CONTACTLESS_AUTHORISED");
    private static final Set<String> USABLE_EVIDENCE = Set.of("PHOTO", "GPS", "DOCUMENT", "SIGNATURE");

    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerSiteRepository sites;
    private final FuelDeliveryEvidenceRepository evidence;
    private final FuelPodExceptionRepository exceptions;
    private final FuelSettingsService settings;
    private final FuelService fuelService;
    private final FuelArrivalCheckService arrivalChecks;
    private final AuditTrailFacade audit;

    @Transactional
    public za.co.handyflow.platform.fuel.dto.DeliveryResponse complete(TenantId tenantId, UUID userId, UUID deliveryId, CompleteDeliveryRequest req) {
        return complete(tenantId, userId, deliveryId, req, Instant.now());
    }

    za.co.handyflow.platform.fuel.dto.DeliveryResponse complete(TenantId tenantId, UUID userId, UUID deliveryId, CompleteDeliveryRequest req, Instant now) {
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        // A retry of a completion that already happened is returned as it was: it is not judged again and records no second exception.
        boolean replay = req.clientRequestId() != null && req.clientRequestId().equals(d.getCompletionRequestId()) && "DELIVERED".equals(d.getStatus());
        // Offloading is final, so the arrival check is asked for here, on the one path both the driver and the office use.
        if (!replay) arrivalChecks.requireClear(tenantId, deliveryId);
        FuelSettings s = settings.current(tenantId);
        if (replay || !s.isPodPolicyEnforced()) return fuelService.completeDelivery(tenantId, deliveryId, req);

        FuelPodException toRecord = decide(tenantId, d, req, s, userId, now);
        var out = fuelService.completeDelivery(tenantId, deliveryId, req);
        if (toRecord != null) {
            exceptions.save(toRecord);
            audit.record(tenantId, "FUEL_POD_EXCEPTION_USED", "FuelDelivery", deliveryId, null,
                    Map.of("type", toRecord.getExceptionType(), "reason", toRecord.getReason(), "evidenceCount", toRecord.getEvidenceCount(),
                            "completedBy", String.valueOf(userId), "approvedBy", String.valueOf(toRecord.getApprovedBy()), "exceptionId", toRecord.getId().toString()));
        }
        return out;
    }

    /** Throws when the delivery may not be completed as asked. Returns the exception to record, or null for an ordinary delivery. */
    private FuelPodException decide(TenantId tenantId, FuelDelivery d, CompleteDeliveryRequest req, FuelSettings s, UUID userId, Instant now) {
        String type = req.podExceptionType() == null || req.podExceptionType().isBlank() ? null : req.podExceptionType().trim();
        boolean hasName = req.receiverName() != null && !req.receiverName().isBlank();
        if (type == null) {
            if (!hasName) throw new IllegalStateException("Enter the name of the person who received the fuel. If nobody can receive it, the delivery needs an authorised exception.");
            if (Boolean.TRUE.equals(req.signedOnBehalf()) && (req.onBehalfOf() == null || req.onBehalfOf().isBlank()))
                throw new IllegalStateException("Enter the name of the designated receiver you signed on behalf of.");
            return null;
        }
        if (hasName) throw new IllegalStateException("Give either the receiver's name or an exception, not both.");
        if (!FuelSettings.POD_EXCEPTION_TYPES.contains(type)) throw new IllegalStateException("Unknown exception type: " + type + ".");
        if (!s.allowedPodExceptions().contains(type)) throw new IllegalStateException("This company does not allow the exception " + type + ".");
        String reason = req.podExceptionReason() == null ? "" : req.podExceptionReason().trim();
        if (reason.length() < 10) throw new IllegalStateException("Explain why nobody could receive the fuel (at least 10 characters).");
        int evidenceCount = (int) evidence.findByDelivery(tenantId, d.getId()).stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()) && USABLE_EVIDENCE.contains(e.getKind())).count();
        if (evidenceCount == 0) throw new IllegalStateException("An exception needs evidence on the load first: a photo, a location or a document.");

        if (SITE_TYPES.contains(type)) {
            FuelCustomerSite site = d.getSiteId() == null ? null : sites.findActiveById(tenantId, d.getSiteId()).orElse(null);
            if (site == null || !type.equals(site.getPodExceptionType()))
                throw new IllegalStateException("This delivery's site is not marked as " + type + ", so that exception cannot be used.");
            return FuelPodException.usedBySiteRule(tenantId, d.getId(), type, reason, userId, evidenceCount, now);
        }
        FuelPodException approved = exceptions.forDelivery(tenantId, d.getId()).stream()
                .filter(e -> "APPROVED".equals(e.getStatus()) && type.equals(e.getExceptionType())).findFirst()
                .orElseThrow(() -> new IllegalStateException("This exception needs approval from an authorised person before the delivery is completed."));
        approved.markUsed(evidenceCount, now);
        return approved;
    }

    /** Pre-approve an after-hours or contactless delivery. The controller requires FUEL_POD_EXCEPTION_APPROVE. */
    @Transactional
    public PodExceptionView approve(TenantId tenantId, UUID approverUserId, UUID deliveryId, ApproveRequest req) { return approve(tenantId, approverUserId, deliveryId, req, Instant.now()); }

    PodExceptionView approve(TenantId tenantId, UUID approverUserId, UUID deliveryId, ApproveRequest req, Instant now) {
        if (approverUserId == null) throw new IllegalStateException("You are not signed in as a user.");
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        if (!"SCHEDULED".equals(d.getStatus()) && !"IN_TRANSIT".equals(d.getStatus())) throw new IllegalStateException("This load is already " + d.getStatus().toLowerCase().replace('_', ' ') + ".");
        String type = req.type();
        if (type == null || !APPROVED_TYPES.contains(type)) throw new IllegalArgumentException("Only AFTER_HOURS_APPROVED or CONTACTLESS_AUTHORISED can be approved here.");
        FuelSettings s = settings.current(tenantId);
        if (!s.allowedPodExceptions().contains(type)) throw new IllegalStateException("This company does not allow the exception " + type + ".");
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 10) throw new IllegalArgumentException("Explain the approval in at least 10 characters.");
        FuelPodException e = exceptions.save(FuelPodException.approved(tenantId, deliveryId, type, reason, approverUserId, now));
        audit.record(tenantId, "FUEL_POD_EXCEPTION_APPROVED", "FuelDelivery", deliveryId, null,
                Map.of("type", type, "reason", reason, "approver", approverUserId.toString(), "exceptionId", e.getId().toString()));
        return view(e);
    }

    @Transactional(readOnly = true)
    public List<PodExceptionView> forDelivery(TenantId tenantId, UUID deliveryId) {
        return exceptions.forDelivery(tenantId, deliveryId).stream().map(FuelPodPolicyService::view).toList();
    }

    @Transactional(readOnly = true)
    public PodPolicy policy(TenantId tenantId) { FuelSettings s = settings.current(tenantId); return new PodPolicy(s.isPodPolicyEnforced(), s.allowedPodExceptions().stream().sorted().toList()); }

    @Transactional
    public PodPolicy updatePolicy(TenantId tenantId, PodPolicyRequest req) { return settings.updatePodPolicy(tenantId, req); }

    private static PodExceptionView view(FuelPodException e) {
        return new PodExceptionView(e.getId(), e.getDeliveryId(), e.getExceptionType(), e.getReason(), e.getStatus(), e.getApprovedBy(), e.getApprovedAt(), e.getUsedAt(), e.getEvidenceCount());
    }
}
