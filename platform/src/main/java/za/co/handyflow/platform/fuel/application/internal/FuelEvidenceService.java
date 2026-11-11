package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryEvidence;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.EvidenceRequest;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.EvidenceView;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.SupersedeRequest;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Proof-of-delivery evidence (gap C01): photos, signatures, documents and GPS points attached to a delivery. Records are
 * append-only. A wrong one is superseded with a reason; once someone other than the capturer verifies it, it is locked.
 * Files themselves are uploaded elsewhere; this stores the reference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelEvidenceService {

    private static final Set<String> CAPTURABLE = Set.of("IN_TRANSIT", "DELIVERED");

    private final FuelDeliveryRepository deliveries;
    private final FuelDeliveryEvidenceRepository evidence;
    private final HrFacade hr;

    @Transactional(readOnly = true)
    public List<EvidenceView> list(TenantId tenantId, UUID deliveryId) {
        delivery(tenantId, deliveryId);
        return evidence.findByDelivery(tenantId, deliveryId).stream().map(FuelEvidenceService::view).toList();
    }

    /** Office staff may add evidence to any delivery of the company. */
    @Transactional
    public EvidenceView addAsOffice(TenantId tenantId, UUID deliveryId, EvidenceRequest req, UUID by) {
        return add(tenantId, delivery(tenantId, deliveryId), req, by);
    }

    /** A driver may add evidence only to their own load. Another driver's load looks like a missing one. */
    @Transactional
    public EvidenceView addAsDriver(TenantId tenantId, UUID userId, UUID deliveryId, EvidenceRequest req) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        FuelDelivery d = delivery(tenantId, deliveryId);
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return add(tenantId, d, req, userId);
    }

    @Transactional(readOnly = true)
    public List<EvidenceView> listAsDriver(TenantId tenantId, UUID userId, UUID deliveryId) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        EmployeeSummary me = hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
        FuelDelivery d = delivery(tenantId, deliveryId);
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return evidence.findByDelivery(tenantId, deliveryId).stream().map(FuelEvidenceService::view).toList();
    }

    @Transactional
    public EvidenceView verify(TenantId tenantId, UUID evidenceId, UUID by) {
        FuelDeliveryEvidence e = lock(tenantId, evidenceId);
        e.verify(by);
        log.info("Fuel POD evidence verified tenant={} evidence={} by={}", tenantId, evidenceId, by);
        return view(evidence.save(e));
    }

    /** Replaces unverified evidence. With a replacement the new record is created in the same step; without one the old record is just withdrawn. */
    @Transactional
    public List<EvidenceView> supersede(TenantId tenantId, UUID evidenceId, SupersedeRequest req, UUID by) {
        FuelDeliveryEvidence old = lock(tenantId, evidenceId);
        old.supersede(by, req.reason());
        evidence.save(old);
        log.info("Fuel POD evidence superseded tenant={} evidence={} by={}", tenantId, evidenceId, by);
        if (req.replacement() == null) return List.of(view(old));
        FuelDelivery d = delivery(tenantId, old.getDeliveryId());
        return List.of(view(old), add(tenantId, d, req.replacement(), by));
    }

    private EvidenceView add(TenantId tenantId, FuelDelivery d, EvidenceRequest req, UUID by) {
        if (!CAPTURABLE.contains(d.getStatus())) {
            throw new IllegalStateException("Evidence can be added once a delivery is on the road or delivered. This one is " + d.getStatus().toLowerCase().replace('_', ' ') + ".");
        }
        FuelDeliveryEvidence e = FuelDeliveryEvidence.create(tenantId, d.getId(), req.kind(), req.fileRef(), req.latitude(), req.longitude(), req.accuracyM(), req.note(), by);
        return view(evidence.save(e));
    }

    private FuelDelivery delivery(TenantId tenantId, UUID id) {
        return deliveries.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery", id.toString()));
    }

    private FuelDeliveryEvidence lock(TenantId tenantId, UUID id) {
        return evidence.findByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Evidence", id.toString()));
    }

    private static EvidenceView view(FuelDeliveryEvidence e) {
        return new EvidenceView(e.getId(), e.getDeliveryId(), e.getKind(), e.getFileRef(), e.getLatitude(), e.getLongitude(), e.getAccuracyM(), e.getNote(),
                e.getCapturedAt(), e.getCapturedBy(), e.getStatus(), e.getSupersededReason(), e.getVerifiedBy(), e.getVerifiedAt(), e.isVerified());
    }
}
