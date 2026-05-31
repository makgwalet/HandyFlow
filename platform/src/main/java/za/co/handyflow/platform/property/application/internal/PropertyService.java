// property/application/internal/PropertyService.java

package za.co.handyflow.platform.property.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.property.domain.model.*;
import za.co.handyflow.platform.property.domain.repository.*;
import za.co.handyflow.platform.property.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository     propertyRepository;
    private final UnitRepository         unitRepository;
    private final LeaseRepository        leaseRepository;
    private final LeasePaymentRepository paymentRepository;
    private final InspectionRepository   inspectionRepository;

    // ── Properties ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyResponse> getProperties(TenantId tenantId, Pageable pageable) {
        return propertyRepository.findAllActive(tenantId, pageable)
                .map(p -> toPropertyResponse(p, List.of()));
    }

    @Transactional(readOnly = true)
    public PropertyResponse getProperty(TenantId tenantId, UUID id) {
        Property property = propertyRepository
                .findActiveByIdWithUnits(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id.toString()));

        List<UnitResponse> units = property.getUnits().stream()
                .filter(u -> !u.isDeleted())
                .map(this::toUnitResponse)
                .toList();

        return toPropertyResponse(property, units);
    }

    @Transactional
    public PropertyResponse createProperty(TenantId tenantId, CreatePropertyRequest req) {
        Property property = Property.create(tenantId, req.name(), req.propertyType(),
                req.address(), req.description(), req.customerId());
        propertyRepository.save(property);
        log.info("Created property={} tenant={}", property.getName(), tenantId);
        return toPropertyResponse(property, List.of());
    }

    @Transactional
    public void deleteProperty(TenantId tenantId, UUID id) {
        Property property = propertyRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id.toString()));
        property.softDelete(null);
        propertyRepository.save(property);
    }

    // ── Units ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UnitResponse> getUnits(TenantId tenantId, UUID propertyId,
                                       String status, Pageable pageable) {
        if (propertyId != null) {
            return unitRepository.findByProperty(propertyId, pageable)
                    .map(this::toUnitResponse);
        }
        if (status != null) {
            return unitRepository.findByStatus(tenantId, status.toUpperCase(), pageable)
                    .map(this::toUnitResponse);
        }
        return unitRepository.findAllActive(tenantId, pageable)
                .map(this::toUnitResponse);
    }

    @Transactional
    public UnitResponse addUnit(TenantId tenantId, UUID propertyId,
                                CreateUnitRequest req) {
        Property property = propertyRepository.findActiveById(tenantId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId.toString()));

        if (unitRepository.existsByPropertyIdAndUnitNumberAndDeletedAtIsNull(
                propertyId, req.unitNumber().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Unit number " + req.unitNumber() + " already exists in this property"
            );
        }

        Unit unit = Unit.create(tenantId, property, req.unitNumber(), req.unitType(),
                req.floorNumber(), req.sizeSqm(), req.baseRent(),
                req.depositAmount(), req.furnished());
        unitRepository.save(unit);
        log.info("Added unit={} to property={}", unit.getUnitNumber(), property.getName());
        return toUnitResponse(unit);
    }

    // ── Leases ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LeaseResponse> getLeases(TenantId tenantId, String status,
                                         Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? leaseRepository.findAllActive(tenantId, pageable)
                : leaseRepository.findByStatus(tenantId, status.toUpperCase(), pageable);
        return page.map(this::toLeaseResponse);
    }

    @Transactional(readOnly = true)
    public LeaseResponse getLease(TenantId tenantId, UUID id) {
        return leaseRepository.findActiveById(tenantId, id)
                .map(this::toLeaseResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", id.toString()));
    }

    @Transactional
    public LeaseResponse createLease(TenantId tenantId, UUID unitId,
                                     CreateLeaseRequest req) {
        Unit unit = unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));

        // WHY check? Can't have two active leases on the same unit
        leaseRepository.findActiveLease(unitId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Unit already has an active lease. Terminate it before creating a new one."
            );
        });

        Lease lease = Lease.create(tenantId, unitId, req.customerId(),
                req.lesseeName(), req.lesseeIdNumber(), req.lesseeEmail(),
                req.lesseePhone(), req.startDate(), req.endDate(),
                req.monthlyRent(), req.depositAmount(), req.paymentDay(),
                req.escalationRate());
        leaseRepository.save(lease);

        // Mark unit as occupied
        unit.occupy();
        unitRepository.save(unit);

        log.info("Lease created unit={} lessee={}", unitId, req.lesseeName());
        return toLeaseResponse(lease);
    }

    @Transactional
    public LeaseResponse terminateLease(TenantId tenantId, UUID leaseId, String reason) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        lease.terminate(reason);
        leaseRepository.save(lease);

        // Mark unit as vacant
        unitRepository.findActiveById(tenantId, lease.getUnitId())
                .ifPresent(unit -> {
                    unit.vacate();
                    unitRepository.save(unit);
                });

        return toLeaseResponse(lease);
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(TenantId tenantId, UUID leaseId,
                                             Pageable pageable) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        return paymentRepository.findByLease(leaseId, pageable)
                .map(this::toPaymentResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getOutstandingPayments(TenantId tenantId) {
        return paymentRepository.findOutstanding(tenantId).stream()
                .map(this::toPaymentResponse).toList();
    }

    @Transactional
    public PaymentResponse createPaymentRecord(TenantId tenantId, UUID leaseId,
                                               CreatePaymentRequest req) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        // WHY check? Prevent duplicate payment records for the same period
        paymentRepository.findByPeriod(leaseId, req.periodYear(), req.periodMonth())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Payment record already exists for " +
                                    req.periodYear() + "/" + req.periodMonth()
                    );
                });

        LeasePayment payment = LeasePayment.create(tenantId, leaseId,
                req.periodYear(), req.periodMonth(), req.amountDue(), req.dueDate());
        paymentRepository.save(payment);
        return toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse recordPayment(TenantId tenantId, UUID leaseId,
                                         UUID paymentId, RecordPaymentRequest req) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        LeasePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.toString()));

        payment.recordPayment(req.amountPaid(), req.paidDate(),
                req.paymentMethod(), req.reference());
        paymentRepository.save(payment);

        log.info("Payment recorded lease={} amount={} status={}",
                leaseId, req.amountPaid(), payment.getStatus());
        return toPaymentResponse(payment);
    }

    // ── Inspections ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InspectionResponse> getInspections(TenantId tenantId, UUID unitId,
                                                   Pageable pageable) {
        unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));
        return inspectionRepository.findByUnit(unitId, pageable)
                .map(this::toInspectionResponse);
    }

    @Transactional
    public InspectionResponse createInspection(TenantId tenantId, UUID unitId,
                                               CreateInspectionRequest req) {
        unitRepository.findActiveById(tenantId, unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", unitId.toString()));

        Inspection inspection = Inspection.create(tenantId, unitId, req.leaseId(),
                req.type(), req.inspectedAt(), req.inspectedBy(),
                req.overallCondition(), req.notes(), req.items());
        inspectionRepository.save(inspection);
        return toInspectionResponse(inspection);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PropertyResponse toPropertyResponse(Property p, List<UnitResponse> units) {
        long vacant   = units.stream().filter(u -> "VACANT".equals(u.status())).count();
        long occupied = units.stream().filter(u -> "OCCUPIED".equals(u.status())).count();
        return new PropertyResponse(
                p.getId(), p.getName(), p.getPropertyType(), p.getAddress(),
                p.getDescription(), p.getCustomerId(), p.getPurchasePrice(),
                p.getMarketValue(), units.size(), vacant, occupied,
                units, p.getCreatedAt()
        );
    }

    private UnitResponse toUnitResponse(Unit u) {
        return new UnitResponse(
                u.getId(), u.getProperty().getId(), u.getUnitNumber(), u.getUnitType(),
                u.getFloorNumber(), u.getSizeSqm(), u.getBaseRent(), u.getDepositAmount(),
                u.getStatus(), u.isFurnished(), u.getAmenities(),
                u.getNotes(), u.getCreatedAt()
        );
    }

    private LeaseResponse toLeaseResponse(Lease l) {
        return new LeaseResponse(
                l.getId(), l.getUnitId(), l.getCustomerId(),
                l.getLesseeName(), l.getLesseeEmail(), l.getLesseePhone(),
                l.getStartDate(), l.getEndDate(), l.getMonthlyRent(),
                l.getDepositAmount(), l.isDepositPaid(), l.getPaymentDay(),
                l.getEscalationRate(), l.getStatus(),
                l.isMonthToMonth(), l.isExpiringSoon(), l.getCreatedAt()
        );
    }

    private PaymentResponse toPaymentResponse(LeasePayment p) {
        return new PaymentResponse(
                p.getId(), p.getLeaseId(), p.getPeriodYear(), p.getPeriodMonth(),
                p.getAmountDue(), p.getAmountPaid(), p.getBalance(),
                p.getDueDate(), p.getPaidDate(), p.getPaymentMethod(),
                p.getReference(), p.getStatus(), p.getCreatedAt()
        );
    }

    private InspectionResponse toInspectionResponse(Inspection i) {
        return new InspectionResponse(
                i.getId(), i.getUnitId(), i.getLeaseId(), i.getType(),
                i.getInspectedAt(), i.getInspectedBy(), i.getOverallCondition(),
                i.getNotes(), i.getItems(), i.getCreatedAt()
        );
    }

    // ── B13: Update lease terms ───────────────────────────────────────────────
    @Transactional
    public LeaseResponse updateLease(TenantId tenantId, UUID leaseId,
                                     UpdateLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        if (!lease.isActive()) {
            throw new HandyFlowException(
                    "Only active leases can be updated",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "LEASE_NOT_ACTIVE");
        }

        lease.updateTerms(req.monthlyRent(), req.endDate(),
                req.paymentDay(), req.escalationRate(), req.notes());
        leaseRepository.save(lease);
        log.info("Updated lease={} in tenant={}", leaseId, tenantId);
        return toLeaseResponse(lease);
    }

    // ── B13: Renew lease ──────────────────────────────────────────────────────
    @Transactional
    public LeaseResponse renewLease(TenantId tenantId, UUID leaseId,
                                    RenewLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        if ("TERMINATED".equals(lease.getStatus())) {
            throw new HandyFlowException(
                    "Terminated leases cannot be renewed. Create a new lease instead.",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "LEASE_TERMINATED");
        }

        // If no explicit new rent given, auto-apply the stored escalation rate
        BigDecimal newRent = req.newMonthlyRent();
        if (newRent == null && lease.getEscalationRate() != null
                && lease.getEscalationRate().compareTo(BigDecimal.ZERO) > 0) {
            newRent = lease.getMonthlyRent()
                    .multiply(BigDecimal.ONE.add(
                            lease.getEscalationRate().divide(
                                    new BigDecimal("100"), 6,
                                    java.math.RoundingMode.HALF_UP)))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        lease.renew(req.newEndDate(), newRent, req.newEscalationRate());
        leaseRepository.save(lease);

        // Ensure unit is OCCUPIED
        unitRepository.findActiveById(tenantId, lease.getUnitId())
                .ifPresent(unit -> {
                    if (!unit.isOccupied()) {
                        unit.occupy();
                        unitRepository.save(unit);
                    }
                });

        log.info("Renewed lease={} new end={} new rent={}", leaseId,
                req.newEndDate(), newRent);
        return toLeaseResponse(lease);
    }

    // ── B13: Apply rent escalation ────────────────────────────────────────────
    @Transactional
    public LeaseResponse escalateLease(TenantId tenantId, UUID leaseId,
                                       EscalateLeaseRequest req) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        if (!lease.isActive()) {
            throw new HandyFlowException(
                    "Only active leases can be escalated",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "LEASE_NOT_ACTIVE");
        }

        BigDecimal oldRent = lease.getMonthlyRent();

        if (req.newMonthlyRent() != null) {
            // Direct override — landlord sets exact new amount
            lease.setMonthlyRent(req.newMonthlyRent());
            log.info("Escalated lease={} from R{} to R{} (fixed)",
                    leaseId, oldRent, req.newMonthlyRent());
        } else if (req.escalationPercent() != null) {
            // Percentage increase — e.g. CPI 8.5%
            BigDecimal newRent = lease.applyEscalation(req.escalationPercent());
            log.info("Escalated lease={} from R{} to R{} ({}% increase)",
                    leaseId, oldRent, newRent, req.escalationPercent());
        } else {
            throw new HandyFlowException(
                    "Provide either escalationPercent or newMonthlyRent",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "MISSING_ESCALATION");
        }

        leaseRepository.save(lease);
        return toLeaseResponse(lease);
    }
}