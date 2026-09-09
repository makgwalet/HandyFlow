package za.co.handyflow.platform.property.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.property.domain.model.Inspection;
import za.co.handyflow.platform.property.domain.model.Lease;
import za.co.handyflow.platform.property.domain.model.LeasePayment;
import za.co.handyflow.platform.property.domain.model.Unit;
import za.co.handyflow.platform.property.domain.repository.InspectionRepository;
import za.co.handyflow.platform.property.domain.repository.LeasePaymentRepository;
import za.co.handyflow.platform.property.domain.repository.LeaseRepository;
import za.co.handyflow.platform.property.domain.repository.PropPortalAccessGrantRepository;
import za.co.handyflow.platform.property.domain.repository.UnitRepository;
import za.co.handyflow.platform.property.dto.PropPortalInspectionResponse;
import za.co.handyflow.platform.property.dto.PropPortalLeaseSummaryResponse;
import za.co.handyflow.platform.property.dto.PropPortalPaymentResponse;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-portal-facing read side — direct analog of
 * WhsePortalDataService. A tenant logged into the portal can see: which
 * leases (of this tenant/landlord's properties) they have access to
 * (agreed design decision — query all leases, show a list if there's
 * more than one rather than assuming exactly one), their own lease
 * details, payment history, and inspection reports for their unit.
 * View-only per the agreed design — no payment initiation, matching
 * every other portal in this codebase (none of them process payments;
 * no payment-gateway integration exists anywhere in this platform).
 * <p>
 * Every method funnels through requireAccess() first, same "portal
 * token proves identity, the grant proves scope" split every other
 * portal-data service in this codebase already establishes.
 */
@Service
@RequiredArgsConstructor
public class PropPortalDataService {

    private final PropPortalAccessGrantRepository grantRepo;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final LeasePaymentRepository paymentRepository;
    private final InspectionRepository inspectionRepository;

    @Transactional(readOnly = true)
    public List<PropPortalLeaseSummaryResponse> getMyLeases(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> leaseRepository.findById(g.getLeaseId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toLeaseSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PropPortalLeaseSummaryResponse getMyLease(UUID portalUserId, UUID leaseId) {
        Lease lease = requireAccess(portalUserId, leaseId);
        return toLeaseSummary(lease);
    }

    @Transactional(readOnly = true)
    public List<PropPortalPaymentResponse> getMyPayments(UUID portalUserId, UUID leaseId) {
        requireAccess(portalUserId, leaseId);
        return paymentRepository.findByLease(leaseId, Pageable.unpaged()).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PropPortalInspectionResponse> getMyInspections(UUID portalUserId, UUID leaseId) {
        Lease lease = requireAccess(portalUserId, leaseId);
        return inspectionRepository.findByUnit(lease.getUnitId(), Pageable.unpaged()).stream()
                .map(this::toInspectionResponse)
                .toList();
    }

    private Lease requireAccess(UUID portalUserId, UUID leaseId) {
        grantRepo.findActiveGrant(portalUserId, leaseId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this lease", HttpStatus.FORBIDDEN, "NO_ACCESS"));
        return leaseRepository.findById(leaseId)
                .orElseThrow(() -> new HandyFlowException("Lease not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /**
     * The portal side deliberately has no TenantId in scope (the caller
     * is an external tenant, not staff of this landlord) — same
     * sidestep every other portal-data service in this codebase uses,
     * resolving straight off the underlying entity. unitRepository.
     * findById() here is intentionally NOT tenant-filtered for that
     * reason; requireAccess() is what actually gates visibility.
     */
    private PropPortalLeaseSummaryResponse toLeaseSummary(Lease lease) {
        Unit unit = unitRepository.findById(lease.getUnitId()).orElse(null);
        String propertyName = unit != null && unit.getProperty() != null ? unit.getProperty().getName() : null;
        var propertyAddress = unit != null && unit.getProperty() != null ? unit.getProperty().getAddress() : null;
        String unitNumber = unit != null ? unit.getUnitNumber() : null;

        return new PropPortalLeaseSummaryResponse(
                lease.getId(), propertyName, propertyAddress, unitNumber, lease.getLesseeName(),
                lease.getStartDate(), lease.getEndDate(), lease.getMonthlyRent(), lease.getDepositAmount(),
                lease.isDepositPaid(), lease.getPaymentDay(), lease.getEscalationRate(), lease.getStatus());
    }

    private PropPortalPaymentResponse toPaymentResponse(LeasePayment p) {
        return new PropPortalPaymentResponse(p.getId(), p.getPeriodYear(), p.getPeriodMonth(),
                p.getAmountDue(), p.getAmountPaid(), p.getDueDate(), p.getPaidDate(), p.getStatus());
    }

    private PropPortalInspectionResponse toInspectionResponse(Inspection i) {
        return new PropPortalInspectionResponse(i.getId(), i.getType(), i.getInspectedAt(), i.getInspectedBy(),
                i.getOverallCondition(), i.getNotes(), i.getItems(), i.getPhotoUrls());
    }
}
